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

    public void remember(ProductImage image) {
        jdbc.query("SELECT pg_advisory_xact_lock(1886745197)", (rs,n) -> 0);
        var values = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
            .addValue("src",image.src()).addValue("thumbnail",image.thumbnail()).addValue("publicId",image.publicId())
            .addValue("alt",image.alt()).addValue("width",image.width()).addValue("height",image.height());
        jdbc.update("""
            INSERT INTO luma.media_assets(src,thumbnail,public_id,alt,width,height)
            VALUES(:src,:thumbnail,:publicId,:alt,:width,:height)
            """,values);
    }

    public boolean exists(UUID productId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM luma.products WHERE id = :id AND deleted_at IS NULL)",
            Map.of("id", productId), Boolean.class));
    }

    public boolean append(UUID productId, ProductImage image) {
        try {
            // One atomic append preserves existing gallery entries and concurrent uploads.
            return jdbc.update("""
                UPDATE luma.products SET images = images || jsonb_build_array(CAST(:image AS jsonb)) WHERE id = :id AND deleted_at IS NULL
                """, Map.of("id", productId, "image", json.writeValueAsString(image))) == 1;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot encode uploaded image metadata", exception);
        }
    }
}
