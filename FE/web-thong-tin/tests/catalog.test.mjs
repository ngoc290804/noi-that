import assert from 'node:assert/strict';
import test from 'node:test';
import { CatalogApiError, createCatalogApi } from '../src/services/catalog.ts';
import { products } from '../src/data/catalog.ts';
import { createCatalogParams, formatPrice, getSpecifications, parseCatalogQuery } from '../src/utils/catalog.ts';

const all = { q: '', category: '', sort: 'featured' };
const json = (value, status = 200) => Response.json(value, { status });

test('catalog forwards search, category UUID, sort and pagination to BE', async () => {
  let actual;
  const result = { items: [products[0]], total: 25, page: 1, size: 24 };
  const api = createCatalogApi('https://api.example/api/', async (url, options) => {
    actual = new URL(url);
    assert.equal(options.headers.Accept, 'application/json');
    return json(result);
  });
  assert.deepEqual(await api.listProducts({ q: '  ĐÈN BÀN ', category: '550e8400-e29b-41d4-a716-446655440000', sort: 'price-desc', page: 1 }), result);
  assert.equal(actual.pathname, '/api/products');
  assert.deepEqual(Object.fromEntries(actual.searchParams), { q: '  ĐÈN BÀN ', category: '550e8400-e29b-41d4-a716-446655440000', sort: 'price-desc', page: '1', size: '24' });
});

test('categories and home are loaded from API instead of local fixtures', async () => {
  const api = createCatalogApi('/api', async (url) => json(url.endsWith('/categories') ? [{ id: 'uuid', slug: 'moi', name: 'Danh mục mới' }] : { heroTitle: 'Nội dung từ BE', featuredProductIds: [] }));
  assert.equal((await api.listCategories())[0].slug, 'moi');
  assert.equal((await api.getHomeContent()).heroTitle, 'Nội dung từ BE');
});

test('home sections have independent endpoints without a home-to-catalog dependency', async () => {
  const calls = [];
  const hero = { heroTitle: 'Giới thiệu', heroDescription: 'Luma', heroImage: products[0].images[0] };
  const spaces = [{ title: 'Phòng khách', description: 'Không gian sống', image: products[0].images[0], href: '/san-pham' }];
  const payloads = { '/api/home/hero': hero, '/api/home/featured-products': [products[0]], '/api/home/spaces': spaces };
  const api = createCatalogApi('/api', async (url) => { calls.push(url); assert.ok(url in payloads); return json(payloads[url]); });
  assert.deepEqual(await Promise.all([api.getHomeHero(), api.getHomeFeaturedProducts(), api.getHomeSpaces()]), [hero, [products[0]], spaces]);
  assert.deepEqual(calls.sort(), Object.keys(payloads).sort());
});

test('detail encodes slug and preserves new attributes and image publicId', async () => {
  const product = { ...products[0], material: 'Gỗ sồi', lengthCm: 32.5, images: [{ ...products[0].images[0], publicId: 'luma/catalog/id' }] };
  const api = createCatalogApi('/api', async (url) => {
    assert.equal(url, '/api/products/m%E1%BB%9Bi%2Fslug');
    return json(product);
  });
  assert.deepEqual(await api.getProduct('mới/slug'), product);
});

for (const status of [400, 404, 500, 503]) {
  test(`HTTP ${status} remains an error and never falls back to demo data`, async () => {
    const api = createCatalogApi('/api', async () => json({ detail: 'Internal detail' }, status));
    await assert.rejects(api.getProduct('missing'), (error) => error instanceof CatalogApiError && error.status === status && !error.message.includes('Internal'));
  });
}

test('HTML returned by a broken proxy is reported as an API error', async () => {
  const api = createCatalogApi('/api', async () => new Response('<html>SPA</html>', { headers: { 'Content-Type': 'text/html' } }));
  await assert.rejects(api.listCategories(), (error) => error.status === 502);
});

test('network failure is propagated and a subsequent retry can recover', async () => {
  let calls = 0;
  const api = createCatalogApi('/api', async () => { if (++calls === 1) throw new TypeError('Failed to fetch'); return json([]); });
  await assert.rejects(api.listCategories(), TypeError);
  assert.deepEqual(await api.listCategories(), []);
});

test('cancellation reaches the fetch request', async () => {
  const controller = new AbortController();
  const api = createCatalogApi('/api', async (_url, { signal }) => {
    controller.abort();
    signal.throwIfAborted();
  });
  await assert.rejects(api.getProduct('cancelled', controller.signal), { name: 'AbortError' });
});

function memoryStorage() {
  const entries = new Map();
  return { entries, getItem: (key) => entries.get(key) ?? null, setItem: (key, value) => entries.set(key, value) };
}

const categoryList = [{ id: 'ceramics-id', slug: 'gom-su', name: 'Gốm sứ' }];

test('categories share one in-flight request and reuse the result across calls', async () => {
  let calls = 0;
  let complete;
  const response = new Promise(resolve => { complete = resolve; });
  const api = createCatalogApi('/api', async () => { calls++; return response; }, memoryStorage());
  const first = api.listCategories();
  const second = api.listCategories();
  assert.equal(calls, 1);
  complete(json(categoryList));
  assert.deepEqual(await Promise.all([first, second]), [categoryList, categoryList]);
  assert.deepEqual(await api.listCategories(), categoryList);
  assert.equal(calls, 1);
});

test('categories persist in session storage and are reusable within the same page load', async () => {
  const storage = memoryStorage();
  const api = createCatalogApi('/api', async () => json(categoryList), storage);
  await api.listCategories();
  assert.equal(storage.entries.size, 1);
  const second = createCatalogApi('/api', async () => { assert.fail('Must use saved categories'); }, storage);
  assert.deepEqual(await second.listCategories(), categoryList);
});

test('a new page load refreshes categories instead of keeping old session data', async () => {
  const storage = memoryStorage();
  await createCatalogApi('/api', async () => json(categoryList), storage).listCategories();
  const [key, value] = [...storage.entries][0];
  storage.setItem(key, JSON.stringify({ ...JSON.parse(value), pageStartedAt: -1 }));
  let calls = 0;
  const fresh = [{ id: 'new-id', slug: 'moi', name: 'Danh mục mới' }];
  const reloaded = createCatalogApi('/api', async () => { calls++; return json(fresh); }, storage);
  assert.deepEqual(await reloaded.listCategories(), fresh);
  assert.deepEqual(await reloaded.listCategories(), fresh);
  assert.equal(calls, 1);
});

test('cancelling one category consumer leaves the shared request alive for another', async () => {
  const controller = new AbortController();
  let complete;
  let fetchSignal;
  let calls = 0;
  const response = new Promise(resolve => { complete = resolve; });
  const api = createCatalogApi('/api', async (_url, options) => { calls++; fetchSignal = options.signal; return response; }, memoryStorage());
  const first = api.listCategories(controller.signal);
  const second = api.listCategories();
  controller.abort();
  await assert.rejects(first, { name: 'AbortError' });
  assert.equal(fetchSignal.aborted, false);
  complete(json(categoryList));
  assert.deepEqual(await second, categoryList);
  assert.deepEqual(await api.listCategories(), categoryList);
  assert.equal(calls, 1);
});

test('an already cancelled category consumer does not start a request', async () => {
  const controller = new AbortController();
  controller.abort();
  const api = createCatalogApi('/api', async () => { assert.fail('Request must not start'); }, memoryStorage());
  await assert.rejects(api.listCategories(controller.signal), { name: 'AbortError' });
});

test('failed categories are not saved and retry can populate the cache', async () => {
  const storage = memoryStorage();
  let calls = 0;
  const api = createCatalogApi('/api', async () => ++calls === 1 ? json({}, 503) : json(categoryList), storage);
  await assert.rejects(api.listCategories(), error => error.status === 503);
  assert.equal(storage.entries.size, 0);
  assert.deepEqual(await api.listCategories(), categoryList);
  assert.deepEqual(await api.listCategories(), categoryList);
  assert.equal(calls, 2);
});

test('invalid or inaccessible category storage falls back to API and memory', async () => {
  for (const value of ['{broken', JSON.stringify({ pageStartedAt: performance.timeOrigin, items: [null] })]) {
    let calls = 0;
    const storage = { getItem: () => value, setItem: () => { throw new Error('Quota exceeded'); } };
    const api = createCatalogApi('/api', async () => { calls++; return json(categoryList); }, storage);
    assert.deepEqual(await api.listCategories(), categoryList);
    assert.deepEqual(await api.listCategories(), categoryList);
    assert.equal(calls, 1);
  }
  const blocked = { getItem: () => { throw new Error('Storage disabled'); }, setItem: () => { throw new Error('Storage disabled'); } };
  const api = createCatalogApi('/api', async () => json(categoryList), blocked);
  assert.deepEqual(await api.listCategories(), categoryList);
});

test('category caches stay separate for different API base URLs', async () => {
  const storage = memoryStorage();
  await createCatalogApi('/api', async () => json(categoryList), storage).listCategories();
  const other = [{ id: 'other-id', slug: 'khac', name: 'Khác' }];
  assert.deepEqual(await createCatalogApi('/other-api', async () => json(other), storage).listCategories(), other);
  assert.equal(storage.entries.size, 2);
});

test('featured products scan additional pages and retain the home UUID order', async () => {
  const pages = [];
  const api = createCatalogApi('/api', async (url) => {
    const page = Number(new URL(url, 'http://local').searchParams.get('page'));
    pages.push(page);
    return json({ items: [products[page]], page, size: 1, total: 4 });
  });
  assert.deepEqual((await api.getFeaturedProducts([products[2].id, products[0].id, 'hidden'])).map((p) => p.id), [products[2].id, products[0].id]);
  assert.deepEqual(pages, [0, 1, 2, 3]);
  assert.deepEqual(await api.getFeaturedProducts([]), []);
});

test('product sections have independent encoded endpoints and related uses one request', async () => {
  const calls = [];
  const product = products[0];
  const summary = { id: product.id, slug: product.slug, name: product.name, categoryId: product.categoryId, priceVnd: product.priceVnd, shortDescription: product.shortDescription, primaryImage: product.images[0] };
  const details = { description: product.description, specifications: product.specifications, material: 'Gỗ sồi' };
  const prefix = '/api/products/m%E1%BB%9Bi%2Fslug';
  const payloads = { [`${prefix}/summary`]: summary, [`${prefix}/images`]: product.images, [`${prefix}/details`]: details, [`${prefix}/related?size=4`]: [] };
  const api = createCatalogApi('/api', async (url) => { calls.push(url); assert.ok(url in payloads); return json(payloads[url]); });
  assert.deepEqual(await Promise.all([
    api.getProductSummary('mới/slug'), api.getProductImages('mới/slug'), api.getProductDetails('mới/slug'), api.getRelatedProducts('mới/slug'),
  ]), [summary, product.images, details, []]);
  assert.deepEqual(calls.sort(), Object.keys(payloads).sort());
});

test('related products request four suggestions and cap oversized responses without changing their ranking', async () => {
  const suggestions = Array.from({ length: 7 }, (_, index) => ({ ...products[0], id: `suggestion-${index}` }));
  let requests = 0;
  const api = createCatalogApi('/api', async (url) => {
    requests++;
    assert.equal(url, '/api/products/ghe-an-nordic/related?size=4');
    return json(suggestions);
  });
  assert.deepEqual(await api.getRelatedProducts('ghe-an-nordic'), suggestions.slice(0, 4));
  assert.equal(requests, 1);
});

test('a failing product section leaves summary available and retries independently', async () => {
  let detailsCalls = 0;
  let summaryCalls = 0;
  const api = createCatalogApi('/api', async (url) => {
    if (url.endsWith('/summary')) { summaryCalls++; return json({ name: 'Ghế' }); }
    if (url.endsWith('/details')) return ++detailsCalls === 1 ? json({}, 503) : json({ specifications: [] });
    assert.fail(`Unexpected request: ${url}`);
  });
  const results = await Promise.allSettled([api.getProductSummary('chair'), api.getProductDetails('chair')]);
  assert.equal(results[0].value.name, 'Ghế');
  assert.equal(results[1].reason.status, 503);
  assert.deepEqual(await api.getProductDetails('chair'), { specifications: [] });
  assert.equal(summaryCalls, 1);
  assert.equal(detailsCalls, 2);
});

test('all product sections forward cancellation', async () => {
  for (const method of ['getProductSummary', 'getProductImages', 'getProductDetails', 'getRelatedProducts']) {
    const controller = new AbortController();
    const api = createCatalogApi('/api', async (_url, { signal }) => { controller.abort(); signal.throwIfAborted(); });
    await assert.rejects(api[method]('chair', controller.signal), { name: 'AbortError' });
  }
});

test('URL query accepts dynamic slugs and UUIDs; validates sort, length and page', () => {
  assert.deepEqual(parseCatalogQuery(new URLSearchParams('category=%3Cbad%3E&sort=invalid&q=%20ghế%20&page=-1')), { ...all, q: 'ghế' });
  for (const category of ['danh-muc-moi', '550e8400-e29b-41d4-a716-446655440000']) {
    const query = { q: 'gốm mộc', category, sort: 'price-asc', page: 2 };
    assert.deepEqual(parseCatalogQuery(createCatalogParams(query)), query);
  }
  for (const page of ['-1', '1.2', 'NaN', '2147483648']) assert.equal(parseCatalogQuery(new URLSearchParams({ page })).page, undefined);
  assert.equal(createCatalogParams(all).toString(), '');
  assert.equal(parseCatalogQuery(new URLSearchParams({ q: 'a'.repeat(200) })).q.length, 120);
});

test('typed product attributes replace placeholders while preserving other specifications', () => {
  const placeholders = [
    { label: 'Chất liệu', value: null },
    { label: 'Kích thước', value: null },
    { label: 'Màu sắc', value: null },
    { label: 'Gợi ý bố trí', value: 'Phòng khách' },
  ];
  const product = { ...products[0], specifications: placeholders, lengthCm: 80.5, widthCm: null, heightCm: 42, material: 'Gỗ sồi', color: 'Nâu' };
  const specs = getSpecifications(product);
  assert.equal(specs.find((s) => s.label === 'Chất liệu').value, 'Gỗ sồi');
  assert.equal(specs.find((s) => s.label === 'Màu sắc').value, 'Nâu');
  assert.equal(specs.find((s) => s.label === 'Kích thước').value, 'Dài 80,5 cm × Cao 42 cm');
  assert.equal(placeholders[0].value, null);
  assert.equal(specs.find((s) => s.label === 'Gợi ý bố trí').value, 'Phòng khách');
  assert.deepEqual(getSpecifications({ specifications: placeholders }), placeholders);
});

test('reference prices use Vietnamese formatting', () => {
  assert.equal(formatPrice(2900000), '2.900.000đ');
  assert.equal(formatPrice(450000), '450.000đ');
});
