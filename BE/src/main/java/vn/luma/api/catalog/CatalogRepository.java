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
        WHERE p.is_published AND c.is_published
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

    public List<Category> listCategories() {
        return jdbc.query("""
            SELECT id, slug, name FROM luma.categories
            WHERE is_published ORDER BY display_order, id
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

    private RowMapper<Product> productMapper() {
        return (rs, row) -> mapProduct(rs);
    }

    private Product mapProduct(ResultSet rs) throws SQLException {
        return new Product(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name"),
            rs.getObject("category_id", UUID.class), rs.getLong("price_vnd"), rs.getString("short_description"),
            rs.getString("description"), decode(rs.getString("images"), new TypeReference<List<ProductImage>>() {}),
            decode(rs.getString("specifications"), new TypeReference<List<Specification>>() {}),
            rs.getInt("display_order"), rs.getBoolean("is_featured"),
            rs.getBigDecimal("length_cm"), rs.getBigDecimal("width_cm"), rs.getBigDecimal("height_cm"),
            rs.getString("material"), rs.getString("color"));
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
