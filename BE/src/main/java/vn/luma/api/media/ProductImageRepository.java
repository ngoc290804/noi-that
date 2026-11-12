package vn.luma.api.media;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.luma.api.catalog.CatalogModels.ProductImage;

@Repository
public class ProductImageRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public ProductImageRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public boolean exists(UUID productId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM luma.products WHERE id = :id)",
            Map.of("id", productId), Boolean.class));
    }

    public boolean append(UUID productId, ProductImage image) {
        try {
            // One atomic append preserves existing gallery entries and concurrent uploads.
            return jdbc.update("""
                UPDATE luma.products SET images = images || jsonb_build_array(CAST(:image AS jsonb)) WHERE id = :id
                """, Map.of("id", productId, "image", json.writeValueAsString(image))) == 1;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot encode uploaded image metadata", exception);
        }
    }
}
