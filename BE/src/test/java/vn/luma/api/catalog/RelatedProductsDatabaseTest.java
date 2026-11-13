package vn.luma.api.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "LUMA_DB_TESTS", matches = "true")
@Transactional
class RelatedProductsDatabaseTest {
    @Autowired CatalogRepository catalog;
    @Autowired JdbcTemplate jdbc;
    private UUID furniture;
    private UUID other;

    @BeforeEach
    void isolatedRecommendations() {
        // Changes are local to each transaction and rolled back, including fixture visibility.
        jdbc.update("UPDATE luma.products SET is_published = false");
        furniture = category("related-furniture", true);
        other = category("related-other", true);
    }

    @Test
    void prioritizesMatchingNameThenProductTypeThenCategoryAndExcludesHiddenItems() {
        product("source", "Ghế ăn Nordic", furniture, 100, true);
        product("dining", "Bàn ăn Nordic", furniture, 90, true);
        product("rocking", "Ghế bập bênh NÓRDIC", furniture, 80, true);
        product("other-chair", "Ghế Oslo", other, 70, true);
        product("same-category", "Bàn trà Sol", furniture, 1, true);
        product("fallback", "Đèn Horizon", other, 0, true);
        product("hidden-product", "Ghế ăn Nordic", furniture, 0, false);
        product("hidden-category", "Ghế ăn Nordic", category("related-hidden", false), 0, true);

        assertThat(catalog.getRelatedProducts("source", 4).orElseThrow())
            .extracting(CatalogModels.ProductPreview::slug)
            .containsExactly("dining", "rocking", "other-chair", "same-category");
        assertThat(catalog.getRelatedProducts("source", 2).orElseThrow())
            .extracting(CatalogModels.ProductPreview::slug).containsExactly("dining", "rocking");
    }

    @Test
    void fillsFourWithOtherCategoriesAndDoesNotTreatMaterialWordsAsACollection() {
        product("source", "Tủ gỗ sồi Komorebi", furniture, 100, true);
        product("same-category", "Kệ Bento", furniture, 90, true);
        product("lamp", "Đèn Orbit", other, 1, true);
        product("table", "Bàn Nora", other, 2, true);
        product("oak-chair", "Ghế gỗ sồi", other, 3, true);
        product("extra", "Bình Aria", other, 4, true);

        var suggestions = catalog.getRelatedProducts("source", 8).orElseThrow();
        assertThat(suggestions).extracting(CatalogModels.ProductPreview::slug)
            .containsExactly("same-category", "lamp", "table", "oak-chair");
        assertThat(suggestions).allSatisfy(item -> assertThat(item.images()).isEmpty());
        assertThat(catalog.getRelatedProducts("source", 4).orElseThrow()).isEqualTo(suggestions);
    }

    @Test
    void matchesWholeNormalizedWordsAndCountsRepeatedWordsOnce() {
        product("source", "GHẾ ĂN NÓRDIC", furniture, 100, true);
        product("repeated", "Ghế Nordic Nordic Nordic", furniture, 0, true);
        product("dining", "Bàn ăn Nordic", furniture, 90, true);
        product("same-category", "Bàn trà Sol", furniture, 80, true);
        product("partial", "Đèn Nordicplus", other, 1, true);

        assertThat(catalog.getRelatedProducts("source", 4).orElseThrow())
            .extracting(CatalogModels.ProductPreview::slug)
            .containsExactly("dining", "repeated", "same-category", "partial");
    }

    @Test
    void returnsFewerWhenCatalogIsSmallAndDistinguishesMissingSource() {
        product("source", "Ghế Nordic", furniture, 100, true);
        assertThat(catalog.getRelatedProducts("source", 4).orElseThrow()).isEmpty();
        product("one", "Bàn Nordic", furniture, 1, true);
        assertThat(catalog.getRelatedProducts("source", 4).orElseThrow())
            .extracting(CatalogModels.ProductPreview::slug).containsExactly("one");
        assertThat(catalog.getRelatedProducts("missing", 4)).isEmpty();
        jdbc.update("UPDATE luma.categories SET is_published = false WHERE id = ?", furniture);
        assertThat(catalog.getRelatedProducts("source", 4)).isEmpty();
    }

    private UUID category(String slug, boolean published) {
        return jdbc.queryForObject("""
            INSERT INTO luma.categories (slug, name, is_published) VALUES (?, ?, ?) RETURNING id
            """, UUID.class, slug, slug, published);
    }

    private void product(String slug, String name, UUID category, int order, boolean published) {
        jdbc.update("""
            INSERT INTO luma.products (slug, name, category_id, price_vnd, display_order, is_published)
            VALUES (?, ?, ?, 100000, ?, ?)
            """, slug, name, category, order, published);
    }
}
