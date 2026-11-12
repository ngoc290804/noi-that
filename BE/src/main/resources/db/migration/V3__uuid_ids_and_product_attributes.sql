-- Flyway runs this entire migration in one transaction. Preserve every row and
-- remap foreign keys / JSON references before discarding the temporary mapping.
CREATE TEMP TABLE luma_uuid_mapping (
    entity text NOT NULL,
    old_id text NOT NULL,
    new_id uuid NOT NULL DEFAULT gen_random_uuid(),
    PRIMARY KEY (entity, old_id),
    UNIQUE (new_id)
) ON COMMIT DROP;

INSERT INTO luma_uuid_mapping (entity, old_id)
SELECT 'categories', id FROM luma.categories
UNION ALL SELECT 'products', id FROM luma.products
UNION ALL SELECT 'home_content', id FROM luma.home_content;

CREATE FUNCTION pg_temp.luma_migrate_id(text, text) RETURNS uuid
LANGUAGE sql STABLE STRICT AS $$
    SELECT new_id FROM pg_temp.luma_uuid_mapping WHERE entity = $1 AND old_id = $2
$$;

-- A stable business code still identifies the homepage after its PK becomes UUID.
ALTER TABLE luma.home_content ADD COLUMN code varchar(100);
UPDATE luma.home_content SET code = id;
ALTER TABLE luma.home_content ALTER COLUMN code SET NOT NULL;
ALTER TABLE luma.home_content ADD CONSTRAINT home_content_code_key UNIQUE (code);

-- Rewrite the stored featured IDs too, preserving their order. Remove stale IDs
-- that no longer reference any product, rather than retaining non-UUID values.
UPDATE luma.home_content h
SET content = jsonb_set(h.content, '{featuredProductIds}', COALESCE((
    SELECT jsonb_agg(to_jsonb(m.new_id) ORDER BY element.ordinality)
    FROM jsonb_array_elements_text(h.content -> 'featuredProductIds')
         WITH ORDINALITY AS element(old_id, ordinality)
    JOIN pg_temp.luma_uuid_mapping m ON m.entity = 'products' AND m.old_id = element.old_id
), '[]'::jsonb))
WHERE jsonb_typeof(h.content -> 'featuredProductIds') = 'array';

ALTER TABLE luma.products DROP CONSTRAINT products_category_id_fkey;
ALTER TABLE luma.categories ALTER COLUMN id TYPE uuid
    USING pg_temp.luma_migrate_id('categories', id);
ALTER TABLE luma.products ALTER COLUMN id TYPE uuid
    USING pg_temp.luma_migrate_id('products', id);
ALTER TABLE luma.products ALTER COLUMN category_id TYPE uuid
    USING pg_temp.luma_migrate_id('categories', category_id);
ALTER TABLE luma.home_content ALTER COLUMN id TYPE uuid
    USING pg_temp.luma_migrate_id('home_content', id);

ALTER TABLE luma.categories ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE luma.products ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE luma.home_content ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE luma.products ADD CONSTRAINT products_category_id_fkey
    FOREIGN KEY (category_id) REFERENCES luma.categories(id);

ALTER TABLE luma.products
    ADD COLUMN length_cm numeric(10, 2) CHECK (length_cm > 0 AND length_cm <> 'NaN'::numeric),
    ADD COLUMN width_cm numeric(10, 2) CHECK (width_cm > 0 AND width_cm <> 'NaN'::numeric),
    ADD COLUMN height_cm numeric(10, 2) CHECK (height_cm > 0 AND height_cm <> 'NaN'::numeric),
    ADD COLUMN material varchar(255),
    ADD COLUMN color varchar(100);

COMMENT ON COLUMN luma.products.length_cm IS 'Length in centimeters; NULL when unknown';
COMMENT ON COLUMN luma.products.width_cm IS 'Width in centimeters; NULL when unknown';
COMMENT ON COLUMN luma.products.height_cm IS 'Height in centimeters; NULL when unknown';
COMMENT ON COLUMN luma.products.material IS 'Confirmed material; NULL when unknown';
COMMENT ON COLUMN luma.products.color IS 'Confirmed color; NULL when unknown';

DROP FUNCTION pg_temp.luma_migrate_id(text, text);
