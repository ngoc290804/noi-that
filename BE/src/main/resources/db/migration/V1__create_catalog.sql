-- Keep application tables separate from any existing objects in the Neon database.
CREATE FUNCTION luma.normalize_search(input text) RETURNS text
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE AS $$
    SELECT replace(regexp_replace(normalize(lower(input), NFD), U&'[\0300-\036f]', '', 'g'), 'đ', 'd')
$$;

CREATE TABLE luma.categories (
    id varchar(100) PRIMARY KEY,
    slug varchar(100) NOT NULL UNIQUE,
    name varchar(200) NOT NULL,
    display_order integer NOT NULL DEFAULT 0,
    is_published boolean NOT NULL DEFAULT false
);

CREATE TABLE luma.products (
    id varchar(100) PRIMARY KEY,
    slug varchar(160) NOT NULL UNIQUE,
    name varchar(200) NOT NULL,
    category_id varchar(100) NOT NULL REFERENCES luma.categories(id),
    price_vnd bigint NOT NULL CHECK (price_vnd >= 0 AND price_vnd <= 9007199254740991),
    short_description text NOT NULL DEFAULT '',
    description text NOT NULL DEFAULT '',
    images jsonb NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(images) = 'array'),
    specifications jsonb NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(specifications) = 'array'),
    display_order integer NOT NULL DEFAULT 0,
    is_featured boolean NOT NULL DEFAULT false,
    is_published boolean NOT NULL DEFAULT false,
    search_name text GENERATED ALWAYS AS (luma.normalize_search(name)) STORED,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX products_category_idx ON luma.products(category_id);
CREATE INDEX products_public_order_idx ON luma.products(display_order, id) WHERE is_published;
CREATE INDEX products_public_price_idx ON luma.products(price_vnd, display_order, id) WHERE is_published;

CREATE TABLE luma.home_content (
    id varchar(100) PRIMARY KEY,
    content jsonb NOT NULL CHECK (jsonb_typeof(content) = 'object'),
    is_published boolean NOT NULL DEFAULT false,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE FUNCTION luma.set_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END
$$;

CREATE TRIGGER products_updated_at BEFORE UPDATE ON luma.products
FOR EACH ROW EXECUTE FUNCTION luma.set_updated_at();
CREATE TRIGGER home_content_updated_at BEFORE UPDATE ON luma.home_content
FOR EACH ROW EXECUTE FUNCTION luma.set_updated_at();
