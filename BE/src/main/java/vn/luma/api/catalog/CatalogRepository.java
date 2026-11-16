package vn.luma.api.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static vn.luma.api.catalog.CatalogModels.*;

@Repository
@Transactional(readOnly = true)
public class CatalogRepository {
    private static final Pattern SEARCH_WHITESPACE = Pattern.compile("[\\s\\uFEFF]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final String PUBLIC_PRODUCTS = """
        FROM luma.products p JOIN luma.categories c ON c.id = p.category_id
        WHERE p.is_published AND c.is_published AND p.deleted_at IS NULL AND c.deleted_at IS NULL
        """;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public CatalogRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public ProductPage listProducts(String q, String category, String sort, int page, int size) {
        String filters = PUBLIC_PRODUCTS + """
            AND (:q = '' OR position(luma.normalize_search(:q) in p.search_name) > 0)
            """;
        // Only these fixed SQL fragments may enter ORDER BY. All user input is bound.
        String order = switch (sort) {
            case "price-asc" -> "p.price_vnd ASC, p.display_order, p.id";
            case "price-desc" -> "p.price_vnd DESC, p.display_order, p.id";
            default -> "p.display_order, p.id";
        };
        var params = new MapSqlParameterSource()
            .addValue("q", SEARCH_WHITESPACE.matcher(q).replaceAll(" ").trim())
            .addValue("limit", size).addValue("offset", (long) page * size);
        if (!category.isEmpty()) {
            UUID categoryId = parseUuid(category);
            if (categoryId != null) {
                filters += " AND p.category_id = :categoryId";
                params.addValue("categoryId", categoryId);
            } else {
                filters += " AND c.slug = :categorySlug";
                params.addValue("categorySlug", category);
            }
        }
        long total = jdbc.queryForObject("SELECT count(*) " + filters, params, Long.class);
        var items = jdbc.query("SELECT p.* " + filters + " ORDER BY " + order + " LIMIT :limit OFFSET :offset",
            params, productMapper());
        return new ProductPage(items, total, page, size);
    }

    public Optional<Product> getProduct(String slug) {
        return jdbc.query("SELECT p.* " + PUBLIC_PRODUCTS + " AND p.slug = :slug",
            Map.of("slug", slug), productMapper()).stream().findFirst();
    }

    public Optional<ProductSummary> getProductSummary(String slug) {
        return jdbc.query("""
            SELECT p.id, p.slug, p.name, p.category_id, p.price_vnd, p.short_description,
                (p.images->0)::text AS primary_image
            """ + PUBLIC_PRODUCTS + " AND p.slug = :slug", Map.of("slug", slug), (rs, row) ->
            new ProductSummary(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name"),
                rs.getObject("category_id", UUID.class), rs.getLong("price_vnd"), rs.getString("short_description"),
                rs.getString("primary_image") == null ? null : decode(rs.getString("primary_image"), new TypeReference<ProductImage>() {})))
            .stream().findFirst();
    }

    public Optional<List<ProductImage>> getProductImages(String slug) {
        return jdbc.query("SELECT p.images::text " + PUBLIC_PRODUCTS + " AND p.slug = :slug",
            Map.of("slug", slug), (rs, row) -> decode(rs.getString(1), new TypeReference<List<ProductImage>>() {}))
            .stream().findFirst();
    }

    public Optional<ProductDetails> getProductDetails(String slug) {
        return jdbc.query("""
            SELECT p.description, p.specifications::text, p.length_cm, p.width_cm, p.height_cm, p.material, p.color, p.care_instructions
            """ + PUBLIC_PRODUCTS + " AND p.slug = :slug", Map.of("slug", slug), (rs, row) ->
            new ProductDetails(rs.getString("description"), publicSpecifications(rs),
                rs.getBigDecimal("length_cm"), rs.getBigDecimal("width_cm"), rs.getBigDecimal("height_cm"),
                rs.getString("material"), rs.getString("color"), rs.getString("care_instructions")))
            .stream().findFirst();
    }

    public Optional<List<ProductPreview>> getRelatedProducts(String slug, int size) {
        // One bounded query: shared collection/name, product type, category, then fallback.
        // Generic furniture/material terms do not count as a shared collection name.
        // The left join distinguishes a missing source (no rows) from no suggestions (one null row).
        var rows = jdbc.query("""
            WITH source AS (
                SELECT p.id, p.category_id,
                    regexp_split_to_array(p.search_name, '[^a-z0-9]+') AS name_words,
                    split_part(regexp_replace(btrim(p.search_name), '^(bo|set) +', ''), ' ', 1) AS product_type
            """ + PUBLIC_PRODUCTS + " AND p.slug = :slug) " + """
            SELECT related.id, related.slug, related.name, related.price_vnd, related.images
            FROM source s LEFT JOIN LATERAL (
                SELECT p.id, p.slug, p.name, p.price_vnd, p.display_order,
                    relevance.series_score, relevance.name_score,
                    (split_part(regexp_replace(btrim(p.search_name), '^(bo|set) +', ''), ' ', 1) = s.product_type) AS same_type,
                    (p.category_id = s.category_id) AS same_category,
                    jsonb_build_array(p.images->0)::text AS images
                FROM luma.products p JOIN luma.categories c ON c.id = p.category_id
                CROSS JOIN LATERAL (
                    SELECT count(*) FILTER (WHERE word NOT IN ('an', 'tra', 'tha', 'bap', 'benh', 'trang', 'tri')) AS series_score,
                        count(*) AS name_score
                    FROM (SELECT DISTINCT token AS word
                        FROM regexp_split_to_table(p.search_name, '[^a-z0-9]+') AS token) words
                    WHERE word = ANY(s.name_words) AND length(word) > 1
                        AND word NOT IN ('ghe', 'ban', 'den', 'tu', 'ke', 'binh', 'bo', 'coc', 'gom', 'su',
                            'go', 'soi', 'nhien', 'cao', 'cap', 'noi', 'that')
                ) relevance
                WHERE p.is_published AND c.is_published AND p.deleted_at IS NULL AND c.deleted_at IS NULL
                AND p.id <> s.id
                ORDER BY relevance.series_score DESC, relevance.name_score DESC, same_type DESC,
                    same_category DESC, p.display_order, p.id
                LIMIT :size
            ) related ON true
            ORDER BY related.series_score DESC, related.name_score DESC, related.same_type DESC,
                related.same_category DESC, related.display_order, related.id
            """, Map.of("slug", slug, "size", Math.min(size, 4)), (rs, row) -> {
                if (rs.getObject("id") == null) return null;
                var images = decode(rs.getString("images"), new TypeReference<List<ProductImage>>() {});
                return new ProductPreview(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name"),
                    rs.getLong("price_vnd"), images.stream().filter(java.util.Objects::nonNull).toList());
            });
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.stream().filter(java.util.Objects::nonNull).toList());
    }

    public List<Category> listCategories() {
        return jdbc.query("""
            SELECT id, slug, name FROM luma.categories
            WHERE is_published AND deleted_at IS NULL ORDER BY display_order, id
            """, (rs, row) -> new Category(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name")));
    }

    public Optional<HomeContent> getHomeContent() {
        var documents = jdbc.query("SELECT content::text FROM luma.home_content WHERE code = 'home' AND is_published",
            (rs, row) -> decode(rs.getString(1), new TypeReference<HomeContent>() {}));
        if (documents.isEmpty()) return Optional.empty();
        var content = documents.get(0);
        // Recompute from published products; draft IDs can never leak via home content.
        var featuredIds = jdbc.query("SELECT p.id " + PUBLIC_PRODUCTS + " AND p.is_featured ORDER BY p.display_order, p.id",
            (rs, row) -> rs.getObject(1, UUID.class));
        return Optional.of(new HomeContent(content.heroTitle(), content.heroDescription(), content.heroImage(),
            featuredIds, content.spaces()));
    }

    public Optional<HomeHero> getHomeHero() {
        return jdbc.query("""
            SELECT jsonb_build_object('heroTitle', content->'heroTitle',
                'heroDescription', content->'heroDescription', 'heroImage', content->'heroImage')::text
            FROM luma.home_content WHERE code = 'home' AND is_published
            """, (rs, row) -> decode(rs.getString(1), new TypeReference<HomeHero>() {}))
            .stream().findFirst();
    }

    public List<Product> getHomeFeaturedProducts(int size) {
        return jdbc.query("SELECT p.* " + PUBLIC_PRODUCTS + """
            AND p.is_featured ORDER BY p.display_order, p.id LIMIT :size
            """, Map.of("size", size), productMapper());
    }

    public Optional<List<Space>> getHomeSpaces() {
        return jdbc.query("""
            SELECT content->'spaces' FROM luma.home_content WHERE code = 'home' AND is_published
            """, (rs, row) -> decode(rs.getString(1), new TypeReference<List<Space>>() {}))
            .stream().findFirst();
    }

    private RowMapper<Product> productMapper() {
        return (rs, row) -> mapProduct(rs);
    }

    private Product mapProduct(ResultSet rs) throws SQLException {
        return new Product(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name"),
            rs.getObject("category_id", UUID.class), rs.getLong("price_vnd"), rs.getString("short_description"),
            rs.getString("description"), decode(rs.getString("images"), new TypeReference<List<ProductImage>>() {}),
            publicSpecifications(rs),
            rs.getInt("display_order"), rs.getBoolean("is_featured"),
            rs.getBigDecimal("length_cm"), rs.getBigDecimal("width_cm"), rs.getBigDecimal("height_cm"),
            rs.getString("material"), rs.getString("color"), rs.getString("care_instructions"));
    }

    private List<Specification> publicSpecifications(ResultSet rs) throws SQLException {
        var specs = new java.util.ArrayList<>(decode(rs.getString("specifications"), new TypeReference<List<Specification>>() {}));
        specs.removeIf(s -> {
            String label = java.text.Normalizer.normalize(s.label().strip().toLowerCase(java.util.Locale.ROOT),java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").replace('đ','d');
            return label.equals("huong dan bao quan") || label.equals("bao quan");
        });
        // Derived compatibility label for older public FE; storage has one care field.
        String care = rs.getString("care_instructions");
        if (care != null && !care.isBlank()) specs.add(new Specification("Hướng dẫn bảo quản", care, null));
        return List.copyOf(specs);
    }

    private UUID parseUuid(String value) {
        try {
            // Require the canonical form instead of accepting shortened UUID segments.
            UUID id = UUID.fromString(value);
            return id.toString().equalsIgnoreCase(value) ? id : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private <T> T decode(String value, TypeReference<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid catalog content in database", exception);
        }
    }
}
