#!/usr/bin/env python3
"""Sync the current FE catalog to Cloudinary and Neon; preview by default.

Requires Python 3, Node >=22.12, psql/pg_dump and BE/.env (or environment vars).
Images are uploaded first; catalog upserts then commit in one DB transaction.
"""
import argparse
import base64
import copy
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, quote, urlencode, urlparse
from urllib.request import Request, urlopen
import uuid

BE = Path(__file__).resolve().parents[1]
ROOT = BE.parent
PUBLIC = (ROOT / 'FE/web-thong-tin/public').resolve()
CATALOG_ASSET_FOLDER = 'luma/catalog'
ATTRIBUTES = {
    'lengthCm': ('length_cm', 'numeric'), 'widthCm': ('width_cm', 'numeric'),
    'heightCm': ('height_cm', 'numeric'), 'material': ('material', 'text'), 'color': ('color', 'text'),
}
SNAPSHOT_SQL = """SELECT jsonb_build_object(
    'categories', (SELECT coalesce(jsonb_agg(to_jsonb(c) ORDER BY slug), '[]') FROM luma.categories c),
    'products', (SELECT coalesce(jsonb_agg(to_jsonb(p) ORDER BY slug), '[]') FROM luma.products p),
    'home', (SELECT coalesce(jsonb_agg(to_jsonb(h) ORDER BY code), '[]') FROM luma.home_content h)
);"""


class SyncError(Exception):
    pass


def save_json(path, value):
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    temporary.chmod(0o600)
    temporary.replace(path)


def configuration():
    local = {}
    if (BE / '.env').is_file():
        for line in (BE / '.env').read_text(encoding='utf-8').splitlines():
            if '=' in line and not line.lstrip().startswith('#'):
                key, value = line.split('=', 1)
                local[key] = value
    keys = ('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'CLOUDINARY_CLOUD_NAME',
            'CLOUDINARY_API_KEY', 'CLOUDINARY_API_SECRET')
    config = {key: os.environ.get(key, local.get(key, '')) for key in keys}
    missing = [key for key, value in config.items() if not value]
    if missing:
        raise SyncError('Missing configuration: ' + ', '.join(missing))
    for executable in ('node', 'psql', 'pg_dump'):
        if not shutil.which(executable):
            raise SyncError('Required executable not found: ' + executable)
    return config


def source_catalog():
    module = (ROOT / 'FE/web-thong-tin/src/data/catalog.ts').as_uri()
    code = "const {categories,products,homeContent}=await import(process.argv[1]);process.stdout.write(JSON.stringify({categories,products,home:homeContent}));"
    result = subprocess.run(['node', '--experimental-strip-types', '--input-type=module', '-e', code, module],
                            text=True, capture_output=True, timeout=30)
    if result.returncode:
        raise SyncError('Cannot read the TypeScript catalog; check Node version and source syntax.')
    source = json.loads(result.stdout)
    if not source['categories'] or not source['products']:
        raise SyncError('Refusing an empty source catalog.')
    for entity in ('categories', 'products'):
        rows = source[entity]
        for key in ('id', 'slug'):
            values = [row[key] for row in rows]
            if any(not isinstance(value, str) or not value for value in values) or len(values) != len(set(values)):
                raise SyncError(f'Invalid or duplicated {entity}.{key}.')
    categories = {row['id']: row['slug'] for row in source['categories']}
    products = {row['id']: row['slug'] for row in source['products']}
    for index, category in enumerate(source['categories'], 1):
        category.setdefault('displayOrder', index)
        category.setdefault('isPublished', True)
    for product in source['products']:
        if product['categoryId'] not in categories:
            raise SyncError('A source product references a missing category.')
        product['categorySlug'] = categories[product['categoryId']]
        product.setdefault('isPublished', True)
        price = product['priceVnd']
        if type(price) is not int or not 0 <= price <= 9007199254740991:
            raise SyncError('Invalid product price: ' + product['slug'])
        for key, limit in (('lengthCm', 99999999.99), ('widthCm', 99999999.99), ('heightCm', 99999999.99)):
            value = product.get(key)
            if value is not None and (type(value) not in (int, float) or not math.isfinite(value) or not 0 < value <= limit):
                raise SyncError('Invalid dimension: ' + product['slug'] + '.' + key)
        for key, limit in (('material', 255), ('color', 100)):
            value = product.get(key)
            if value is not None and (not isinstance(value, str) or len(value) > limit):
                raise SyncError('Invalid attribute: ' + product['slug'] + '.' + key)
    try:
        source['featuredProductSlugs'] = [products[value] for value in source['home']['featuredProductIds']]
    except KeyError:
        raise SyncError('Homepage references a missing source product.') from None
    return source


def image_occurrences(source):
    for product in source['products']:
        yield from product['images']
    if 'home' in source:
        yield source['home']['heroImage']
        for space in source['home']['spaces']:
            yield space['image']


def source_files(source):
    files = {}
    for image in image_occurrences(source):
        src = image['src']
        if not src.startswith('/images/'):
            raise SyncError('This sync command accepts local /images/ source assets only.')
        path = (PUBLIC / src.lstrip('/')).resolve()
        if not path.is_relative_to(PUBLIC) or not path.is_file():
            raise SyncError('Source image missing or outside public directory: ' + src)
        if not 0 < path.stat().st_size <= 10 * 1024 * 1024:
            raise SyncError('Source image must be between 1 byte and 10 MiB: ' + src)
        files[src] = path
    return files


class Database:
    def __init__(self, config):
        url = urlparse(config['DB_URL'].removeprefix('jdbc:'))
        if not url.hostname or not url.path.strip('/'):
            raise SyncError('Invalid DB_URL.')
        query = parse_qs(url.query)
        self.env = dict(os.environ, PGHOST=url.hostname, PGPORT=str(url.port or 5432),
                        PGDATABASE=url.path.lstrip('/'), PGUSER=config['DB_USERNAME'], PGPASSWORD=config['DB_PASSWORD'],
                        PGSSLMODE=query.get('sslmode', ['require'])[0],
                        PGCHANNELBINDING=query.get('channelBinding', ['require'])[0],
                        PGCONNECT_TIMEOUT='15', PGCLIENTENCODING='UTF8')
        # Neon transaction pooling rejects statement_timeout in startup options.
        self.env.pop('PGOPTIONS', None)

    def execute(self, sql):
        result = subprocess.run(['psql', '-X', '-A', '-t', '-q', '-v', 'ON_ERROR_STOP=1'],
                                input=sql, env=self.env, text=True, capture_output=True, timeout=90)
        if result.returncode:
            raise SyncError('PostgreSQL command failed; no further sync steps were executed. Check DB availability/schema.')
        return json.loads(result.stdout)

    def backup(self, path):
        path.touch(mode=0o600)
        result = subprocess.run(['pg_dump', '--no-owner', '--no-privileges', '--schema=luma', '--file=' + str(path)],
                                env=self.env, text=True, capture_output=True, timeout=90)
        if result.returncode:
            raise SyncError('Database backup failed; no uploads or catalog changes were started.')


class Cloudinary:
    def __init__(self, config):
        self.cloud = config['CLOUDINARY_CLOUD_NAME']
        if not self.cloud or any(char not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-' for char in self.cloud):
            raise SyncError('Invalid Cloudinary cloud name.')
        self.base = 'https://api.cloudinary.com/v1_1/' + self.cloud
        pair = (config['CLOUDINARY_API_KEY'] + ':' + config['CLOUDINARY_API_SECRET']).encode()
        self.authorization = 'Basic ' + base64.b64encode(pair).decode()

    def request(self, path, data=None, headers=None, missing_ok=False):
        request = Request(self.base + path, data=data, headers={'Authorization': self.authorization, **(headers or {})})
        try:
            with urlopen(request, timeout=30) as response:
                return json.load(response)
        except HTTPError as error:
            if missing_ok and error.code == 404:
                return None
            raise SyncError(f'Cloudinary request failed (HTTP {error.code}); credentials/provider details are not logged.') from None
        except (URLError, TimeoutError):
            raise SyncError('Cloudinary connection failed; rerun to resume using the same asset IDs.') from None

    def lookup(self, public_id):
        return self.request('/resources/image/upload/' + quote(public_id, safe=''), missing_ok=True)

    def ensure_folder(self):
        self.request('/folders/' + CATALOG_ASSET_FOLDER, data=b'')

    def organize(self, asset, display_name):
        public_id = asset['public_id']
        self.request('/resources/image/upload/' + quote(public_id, safe=''),
                     data=urlencode({'asset_folder': CATALOG_ASSET_FOLDER, 'display_name': display_name}).encode(),
                     headers={'Content-Type': 'application/x-www-form-urlencoded'})
        updated = self.lookup(public_id)
        if (not updated or updated.get('asset_folder') != CATALOG_ASSET_FOLDER
            or updated.get('display_name') != display_name
            or updated.get('asset_id') != asset.get('asset_id')
            or updated.get('secure_url') != asset.get('secure_url')):
            raise SyncError('Could not verify the Cloudinary folder move; no database changes were started.')
        return updated

    def upload(self, path, public_id):
        boundary = 'luma-sync-' + uuid.uuid4().hex
        parts = []
        for key, value in (('public_id', public_id), ('asset_folder', CATALOG_ASSET_FOLDER),
                           ('display_name', path.stem), ('overwrite', 'false'), ('allowed_formats', 'jpg,jpeg,png,webp')):
            parts.append((f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n').encode())
        parts.append((f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="source{path.suffix}"\r\nContent-Type: application/octet-stream\r\n\r\n').encode())
        parts += [path.read_bytes(), f'\r\n--{boundary}--\r\n'.encode()]
        return self.request('/image/upload', data=b''.join(parts),
                            headers={'Content-Type': 'multipart/form-data; boundary=' + boundary})

    def metadata(self, asset, expected_id):
        if (asset.get('public_id') != expected_id or asset.get('resource_type') != 'image'
            or asset.get('format') not in ('jpg', 'jpeg', 'png', 'webp')
            or not asset.get('secure_url', '').startswith('https://res.cloudinary.com/' + self.cloud + '/image/upload/')
            or asset.get('width', 0) <= 0 or asset.get('height', 0) <= 0 or asset.get('version', 0) <= 0):
            raise SyncError('Unexpected metadata from Cloudinary; database was not updated.')
        return {'src': asset['secure_url'], 'thumbnail': 'https://res.cloudinary.com/' + self.cloud
                + '/image/upload/c_limit,w_600,q_auto,f_auto/v' + str(asset['version']) + '/' + expected_id + '.' + asset['format'],
                'width': asset['width'], 'height': asset['height'], 'publicId': expected_id}


def verify_delivery(url):
    try:
        with urlopen(Request(url, method='HEAD'), timeout=30) as response:
            if response.status != 200 or not response.headers.get('Content-Type', '').startswith('image/'):
                raise SyncError('An image URL is not delivering an image; database was not updated.')
    except (HTTPError, URLError, TimeoutError):
        raise SyncError('An image/thumbnail could not be verified; rerun to reuse uploaded images.') from None


def sync_sql(payload):
    # One canonical care field; legacy fixtures still use the specification label.
    for product in payload['products']:
        care = [s.get('value', '') for s in product.get('specifications', [])
                if s.get('label', '').strip().casefold() in ('hướng dẫn bảo quản', 'bảo quản') and s.get('value')]
        product.setdefault('careInstructions', '\n\n'.join(care))
        product['specifications'] = [s for s in product.get('specifications', [])
                                    if s.get('label', '').strip().casefold() not in ('hướng dẫn bảo quản', 'bảo quản')]
    # JSON is a quoted SQL value, never a SQL fragment. No secrets enter this file.
    encoded = json.dumps(payload, ensure_ascii=False).replace("'", "''")
    sql = "BEGIN;\nSET LOCAL standard_conforming_strings = on;\nSET LOCAL statement_timeout = '30s';\nSET LOCAL lock_timeout = '10s';\n"
    sql += "SELECT pg_advisory_xact_lock(1886745197);\nCREATE TEMP TABLE catalog_sync_input (payload jsonb NOT NULL) ON COMMIT DROP;\n"
    sql += "INSERT INTO catalog_sync_input VALUES ('" + encoded + "'::jsonb);\n"
    sql += """
INSERT INTO luma.categories AS existing (slug, name, display_order, is_published)
SELECT item->>'slug', item->>'name', (item->>'displayOrder')::integer, (item->>'isPublished')::boolean
FROM catalog_sync_input, jsonb_array_elements(payload->'categories') item
WHERE true
ON CONFLICT (slug) DO UPDATE SET name=EXCLUDED.name, display_order=EXCLUDED.display_order, is_published=EXCLUDED.is_published
WHERE (existing.name, existing.display_order, existing.is_published)
    IS DISTINCT FROM (EXCLUDED.name, EXCLUDED.display_order, EXCLUDED.is_published);

INSERT INTO luma.products AS existing
    (slug, name, category_id, price_vnd, short_description, description, care_instructions, images, specifications, display_order, is_featured, is_published, status)
SELECT item->>'slug', item->>'name', category.id, (item->>'priceVnd')::bigint,
    item->>'shortDescription', item->>'description', item->>'careInstructions', item->'images', item->'specifications',
    (item->>'displayOrder')::integer, (item->>'isFeatured')::boolean, (item->>'isPublished')::boolean, CASE WHEN (item->>'isPublished')::boolean THEN 'published' ELSE 'draft' END
FROM catalog_sync_input CROSS JOIN LATERAL jsonb_array_elements(payload->'products') item
JOIN luma.categories category ON category.slug=item->>'categorySlug'
WHERE true
ON CONFLICT (slug) DO UPDATE SET
    name=EXCLUDED.name, category_id=EXCLUDED.category_id, price_vnd=EXCLUDED.price_vnd,
    short_description=EXCLUDED.short_description, description=EXCLUDED.description, care_instructions=EXCLUDED.care_instructions,
    images=EXCLUDED.images, specifications=EXCLUDED.specifications, display_order=EXCLUDED.display_order,
    is_featured=EXCLUDED.is_featured, is_published=EXCLUDED.is_published, status=EXCLUDED.status
WHERE (existing.name, existing.category_id, existing.price_vnd, existing.short_description, existing.description,
       existing.images, existing.specifications, existing.display_order, existing.is_featured, existing.is_published, existing.care_instructions, existing.status)
    IS DISTINCT FROM (EXCLUDED.name, EXCLUDED.category_id, EXCLUDED.price_vnd, EXCLUDED.short_description, EXCLUDED.description,
       EXCLUDED.images, EXCLUDED.specifications, EXCLUDED.display_order, EXCLUDED.is_featured, EXCLUDED.is_published, EXCLUDED.care_instructions, EXCLUDED.status);
"""
    for key, (column, datatype) in ATTRIBUTES.items():
        # Fixed identifier/type allowlist; only fields explicitly present in source are changed.
        sql += f"""
UPDATE luma.products p SET {column}=(item->>'{key}')::{datatype}
FROM catalog_sync_input CROSS JOIN LATERAL jsonb_array_elements(payload->'products') item
WHERE item ? '{key}' AND p.slug=item->>'slug' AND p.{column} IS DISTINCT FROM (item->>'{key}')::{datatype};
"""
    if 'home' in payload:
        sql += """
INSERT INTO luma.home_content AS existing (code, content, is_published)
SELECT 'home', jsonb_set(payload->'home', '{featuredProductIds}', (
    SELECT coalesce(jsonb_agg(to_jsonb(p.id) ORDER BY element.ordinality), '[]')
    FROM jsonb_array_elements_text(payload->'featuredProductSlugs') WITH ORDINALITY element(slug, ordinality)
    JOIN luma.products p ON p.slug=element.slug
)), true FROM catalog_sync_input
WHERE true
ON CONFLICT (code) DO UPDATE SET content=EXCLUDED.content, is_published=EXCLUDED.is_published
WHERE (existing.content, existing.is_published) IS DISTINCT FROM (EXCLUDED.content, EXCLUDED.is_published);
"""
    sql += """
INSERT INTO luma.media_assets AS m(src,thumbnail,public_id,alt,width,height)
SELECT DISTINCT ON (asset->>'src') asset->>'src',coalesce(asset->>'thumbnail',asset->>'src'),asset->>'publicId',
    left(coalesce(asset->>'alt',''),300),(asset->>'width')::integer,(asset->>'height')::integer
FROM (SELECT jsonb_array_elements(images) asset FROM luma.products WHERE deleted_at IS NULL
    UNION ALL SELECT content->'heroImage' FROM luma.home_content
    UNION ALL SELECT s->'image' FROM luma.home_content h,LATERAL jsonb_array_elements(h.content->'spaces') s) assets
WHERE nullif(asset->>'src','') IS NOT NULL
ORDER BY asset->>'src'
ON CONFLICT(src) DO UPDATE SET thumbnail=EXCLUDED.thumbnail,public_id=EXCLUDED.public_id,alt=EXCLUDED.alt,
    width=EXCLUDED.width,height=EXCLUDED.height,deleted_at=NULL
WHERE (m.thumbnail,m.public_id,m.alt,m.width,m.height,m.deleted_at) IS DISTINCT FROM
    (EXCLUDED.thumbnail,EXCLUDED.public_id,EXCLUDED.alt,EXCLUDED.width,EXCLUDED.height,NULL::timestamptz);
"""
    return sql + SNAPSHOT_SQL + '\nCOMMIT;\n'


def verify_database(before, after, payload):
    old_categories = {row['slug']: row for row in before['categories']}
    old_products = {row['slug']: row for row in before['products']}
    categories = {row['slug']: row for row in after['categories']}
    products = {row['slug']: row for row in after['products']}
    for source in payload['categories']:
        actual = categories[source['slug']]
        uuid.UUID(actual['id'])
        assert actual['name'] == source['name'] and actual['display_order'] == source['displayOrder']
        assert actual['is_published'] == source['isPublished']
        if source['slug'] in old_categories:
            assert actual['id'] == old_categories[source['slug']]['id']
    fields = {'name': 'name', 'priceVnd': 'price_vnd', 'shortDescription': 'short_description',
              'description': 'description', 'careInstructions': 'care_instructions', 'images': 'images',
              'displayOrder': 'display_order', 'isFeatured': 'is_featured', 'isPublished': 'is_published'}
    for source in payload['products']:
        actual = products[source['slug']]
        uuid.UUID(actual['id'])
        assert actual['category_id'] == categories[source['categorySlug']]['id']
        assert all(actual[column] == source[key] for key, column in fields.items())
        editable_specs = [s for s in actual['specifications'] if s.get('label') != 'Hướng dẫn bảo quản']
        assert editable_specs == source['specifications']
        compatibility_care = [s['value'] for s in actual['specifications'] if s.get('label') == 'Hướng dẫn bảo quản']
        assert compatibility_care == ([source['careInstructions']] if source['careInstructions'].strip() else [])
        previous = old_products.get(source['slug'])
        if previous:
            assert actual['id'] == previous['id'] and actual['created_at'] == previous['created_at']
        for key, (column, _) in ATTRIBUTES.items():
            if key in source:
                assert actual[column] == source[key]
            elif previous:
                assert actual[column] == previous[column]
    if 'home' in payload:
        home = next(row for row in after['home'] if row['code'] == 'home')
        old_home = next((row for row in before['home'] if row['code'] == 'home'), None)
        uuid.UUID(home['id'])
        if old_home:
            assert home['id'] == old_home['id']
        expected_home = copy.deepcopy(payload['home'])
        expected_home['featuredProductIds'] = [products[slug]['id'] for slug in payload['featuredProductSlugs']]
        assert home['content'] == expected_home and home['is_published']
    else:
        assert before['home'] == after['home']
    # Unrelated records are outside the sync scope and must remain unchanged.
    for entity, source_slugs in (('categories', {x['slug'] for x in payload['categories']}),
                                 ('products', {x['slug'] for x in payload['products']})):
        before_extra = [row for row in before[entity] if row['slug'] not in source_slugs]
        after_extra = [row for row in after[entity] if row['slug'] not in source_slugs]
        assert before_extra == after_extra


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--apply', action='store_true', help='Back up, upload images and sync Neon.')
    mode.add_argument('--dry-run', action='store_true', help='Preview without uploading or changing DB (default).')
    parser.add_argument('--product', metavar='SLUG', help='Sync only this product; preserve other products, categories and homepage.')
    args = parser.parse_args()
    config = configuration()
    payload = source_catalog()
    if args.product:
        selected = [product for product in payload['products'] if product['slug'] == args.product]
        if not selected:
            raise SyncError('Product slug not found in source: ' + args.product)
        payload = {'categories': [], 'products': selected}
    files = source_files(payload)
    db = Database(config)
    cloud = Cloudinary(config)
    if cloud.request('/ping').get('status') != 'ok':
        raise SyncError('Cloudinary authentication check failed.')
    before = db.execute(SNAPSHOT_SQL)
    if args.product and not any(category['slug'] == payload['products'][0]['categorySlug'] for category in before['categories']):
        raise SyncError('Product category missing in database; sync the catalog categories first.')
    homepages = int('home' in payload)
    print(f"Source: {len(payload['categories'])} categories, {len(payload['products'])} products, {homepages} homepage, {len(files)} unique image files.", flush=True)
    folder = None
    if args.apply:
        private = BE / '.local'
        private.mkdir(mode=0o700, exist_ok=True)
        backups = private / 'catalog-sync'
        backups.mkdir(mode=0o700, exist_ok=True)
        folder = backups / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
        folder.mkdir(mode=0o700)
        db.backup(folder / 'before.sql')
        save_json(folder / 'before.json', before)
        save_json(folder / 'source.json', payload)
        print('Backup saved: ' + str(folder / 'before.sql'), flush=True)
        cloud.ensure_folder()
    assets = {}
    previous_assets = {}
    placements = {}
    created = 0
    reused = 0
    pending = 0
    moved = 0
    pending_moves = 0
    for src, path in files.items():
        public_id = 'luma/catalog/' + hashlib.sha256(path.read_bytes()).hexdigest()
        asset = cloud.lookup(public_id)
        if asset:
            reused += 1
            print('Reuse image: ' + src, flush=True)
            if asset.get('asset_folder') != CATALOG_ASSET_FOLDER or asset.get('display_name') != path.stem:
                if args.apply:
                    previous_assets[src] = {key: asset.get(key) for key in
                        ('asset_id', 'public_id', 'asset_folder', 'display_name', 'secure_url')}
                    save_json(folder / 'cloudinary-before.json', previous_assets)
                    asset = cloud.organize(asset, path.stem)
                    moved += 1
                    print('Moved into ' + CATALOG_ASSET_FOLDER + ': ' + path.stem, flush=True)
                else:
                    pending_moves += 1
                    print('Would move into ' + CATALOG_ASSET_FOLDER + ': ' + path.stem, flush=True)
        elif args.apply:
            asset = cloud.upload(path, public_id)
            created += 1
            print('Uploaded image: ' + src, flush=True)
        else:
            pending += 1
            print('Would upload: ' + src, flush=True)
            continue
        assets[src] = cloud.metadata(asset, public_id)
        if asset.get('asset_folder') != CATALOG_ASSET_FOLDER and args.apply:
            raise SyncError('An uploaded image is outside the project folder; database was not updated.')
        placements[src] = {'publicId': public_id, 'assetFolder': asset.get('asset_folder'), 'displayName': asset.get('display_name')}
        if folder:
            save_json(folder / 'images.json', assets)
            save_json(folder / 'cloudinary-folders.json', placements)
    if not args.apply:
        print(json.dumps({'mode': 'dry-run', 'existingImages': reused, 'imagesToUpload': pending,
                          'assetFolder': CATALOG_ASSET_FOLDER, 'imagesToOrganize': pending_moves,
                          'matchedCategoryUuids': sum(row['slug'] in {c['slug'] for c in before['categories']} for row in payload['categories']),
                          'matchedProductUuids': sum(row['slug'] in {p['slug'] for p in before['products']} for row in payload['products'])}))
        return
    urls = {url for asset in assets.values() for url in (asset['src'], asset['thumbnail'])}
    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(verify_delivery, urls))
    for image in image_occurrences(payload):
        metadata = assets[image['src']]
        image.update(metadata)
    sql = sync_sql(payload)
    (folder / 'apply.sql').write_text(sql, encoding='utf-8')
    (folder / 'apply.sql').chmod(0o600)
    after = db.execute(sql)
    save_json(folder / 'after.json', after)
    verify_database(before, after, payload)
    summary = {'categories': len(payload['categories']), 'products': len(payload['products']), 'homepages': homepages,
               'uniqueImages': len(assets), 'uploadedImages': created, 'reusedImages': reused,
               'assetFolder': CATALOG_ASSET_FOLDER, 'organizedImages': moved,
               'databaseChanged': before != after, 'verified': True}
    save_json(folder / 'result.json', summary)
    print(json.dumps(summary, ensure_ascii=False), flush=True)
    print('Report: ' + str(folder / 'result.json'), flush=True)


if __name__ == '__main__':
    try:
        main()
    except (SyncError, AssertionError, subprocess.TimeoutExpired, OSError, ValueError, KeyError) as error:
        message = str(error) if isinstance(error, SyncError) else type(error).__name__
        print('Sync failed: ' + message, file=sys.stderr)
        sys.exit(1)
