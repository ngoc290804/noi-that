-- Preserve existing publication flags and migrate care copy without losing text.
ALTER TABLE luma.products ADD COLUMN status varchar(20) NOT NULL DEFAULT 'draft'
    CHECK (status IN ('draft', 'published', 'archived')),
    ADD COLUMN care_instructions text NOT NULL DEFAULT '',
    ADD COLUMN version bigint NOT NULL DEFAULT 0,
    ADD COLUMN deleted_at timestamptz;
UPDATE luma.products SET status = CASE WHEN is_published THEN 'published' ELSE 'draft' END;
UPDATE luma.products p SET care_instructions = coalesce((
    SELECT string_agg(spec->>'value', E'\n\n' ORDER BY ord)
    FROM jsonb_array_elements(p.specifications) WITH ORDINALITY s(spec, ord)
    WHERE luma.normalize_search(btrim(spec->>'label')) IN ('huong dan bao quan', 'bao quan')
      AND nullif(btrim(spec->>'value'), '') IS NOT NULL
), '');
-- Keep one automatically derived compatibility label for older BE/FE instances
-- during rollout. care_instructions is the only editable source of truth.
CREATE FUNCTION luma.sync_care_specification() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.specifications = coalesce((SELECT jsonb_agg(spec ORDER BY ord)
        FROM jsonb_array_elements(NEW.specifications) WITH ORDINALITY s(spec,ord)
        WHERE luma.normalize_search(btrim(spec->>'label')) NOT IN ('huong dan bao quan','bao quan')), '[]'::jsonb);
    IF nullif(btrim(NEW.care_instructions),'') IS NOT NULL THEN
        NEW.specifications = NEW.specifications || jsonb_build_array(jsonb_build_object(
            'label','Hướng dẫn bảo quản','value',NEW.care_instructions,'unit',NULL));
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER products_care_compatibility BEFORE INSERT OR UPDATE ON luma.products
FOR EACH ROW EXECUTE FUNCTION luma.sync_care_specification();
UPDATE luma.products SET care_instructions=care_instructions;

-- Promote legacy structured fields when older catalogs only stored the labels.
UPDATE luma.products p SET material=coalesce(nullif(btrim(material),''),(
    SELECT left(spec->>'value',255) FROM jsonb_array_elements(p.specifications) spec
    WHERE luma.normalize_search(btrim(spec->>'label'))='chat lieu' AND nullif(btrim(spec->>'value'),'') IS NOT NULL LIMIT 1)),
    color=coalesce(nullif(btrim(color),''),(
    SELECT left(spec->>'value',100) FROM jsonb_array_elements(p.specifications) spec
    WHERE luma.normalize_search(btrim(spec->>'label'))='mau sac' AND nullif(btrim(spec->>'value'),'') IS NOT NULL LIMIT 1));

ALTER TABLE luma.categories ADD COLUMN version bigint NOT NULL DEFAULT 0,
    ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now(), ADD COLUMN deleted_at timestamptz;
ALTER TABLE luma.home_content ADD COLUMN version bigint NOT NULL DEFAULT 0;

CREATE TABLE luma.media_assets (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    src text NOT NULL UNIQUE,
    thumbnail text NOT NULL,
    public_id text,
    alt varchar(300) NOT NULL DEFAULT '',
    width integer NOT NULL CHECK (width > 0),
    height integer NOT NULL CHECK (height > 0),
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);
INSERT INTO luma.media_assets (src, thumbnail, public_id, alt, width, height)
SELECT DISTINCT ON (asset->>'src') asset->>'src', coalesce(asset->>'thumbnail', asset->>'src'),
    asset->>'publicId', left(coalesce(asset->>'alt', ''), 300),
    greatest(coalesce((asset->>'width')::integer, 1), 1),
    greatest(coalesce((asset->>'height')::integer, 1), 1)
FROM (
    SELECT jsonb_array_elements(images) AS asset FROM luma.products
    UNION ALL SELECT content->'heroImage' FROM luma.home_content
    UNION ALL SELECT space->'image' FROM luma.home_content h,
        LATERAL jsonb_array_elements(coalesce(h.content->'spaces', '[]')) space
) assets WHERE nullif(asset->>'src', '') IS NOT NULL
ORDER BY asset->>'src';

CREATE FUNCTION luma.set_write_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.version = OLD.version + 1;
    NEW.updated_at = clock_timestamp();
    RETURN NEW;
END
$$;
CREATE TRIGGER products_write_version BEFORE UPDATE ON luma.products FOR EACH ROW EXECUTE FUNCTION luma.set_write_version();
CREATE TRIGGER categories_write_version BEFORE UPDATE ON luma.categories FOR EACH ROW EXECUTE FUNCTION luma.set_write_version();
CREATE TRIGGER home_write_version BEFORE UPDATE ON luma.home_content FOR EACH ROW EXECUTE FUNCTION luma.set_write_version();
CREATE TRIGGER media_write_version BEFORE UPDATE ON luma.media_assets FOR EACH ROW EXECUTE FUNCTION luma.set_write_version();
CREATE INDEX products_admin_active_idx ON luma.products(display_order, id) WHERE deleted_at IS NULL;
