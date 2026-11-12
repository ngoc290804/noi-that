package vn.luma.api.media;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import vn.luma.api.catalog.CatalogModels.ProductImage;
import vn.luma.api.catalog.CatalogRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "LUMA_DB_TESTS", matches = "true")
@Transactional
class ProductImageDatabaseTest {
    @Autowired ProductImageRepository images;
    @Autowired CatalogRepository catalog;

    @Test
    void appendsCloudinaryMetadataWithoutReplacingExistingGallery() {
        var product = catalog.getProduct("ghe-an-nordic").orElseThrow();
        var added = new ProductImage("https://res.cloudinary.com/test/image/upload/test.jpg",
            "https://res.cloudinary.com/test/image/upload/w_600/test.jpg", "Ảnh kiểm thử", 1200, 896, "luma/products/test");
        assertThat(images.exists(product.id())).isTrue();
        assertThat(images.append(product.id(), added)).isTrue();
        var updated = catalog.getProduct("ghe-an-nordic").orElseThrow();
        assertThat(updated.images()).hasSize(product.images().size() + 1);
        assertThat(updated.images().subList(0, product.images().size())).isEqualTo(product.images());
        assertThat(updated.images().get(updated.images().size() - 1)).isEqualTo(added);
    }

    @Test
    void missingProductCannotReceiveImage() {
        UUID missing = UUID.randomUUID();
        assertThat(images.exists(missing)).isFalse();
        assertThat(images.append(missing, new ProductImage("test", "test", "test", 1, 1, "test"))).isFalse();
    }
}
