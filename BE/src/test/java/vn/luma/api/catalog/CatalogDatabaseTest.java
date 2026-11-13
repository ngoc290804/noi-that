package vn.luma.api.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Opt-in: uses the configured PostgreSQL database, with fixture changes rolled back after each test. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "LUMA_DB_TESTS", matches = "true")
@Transactional
class CatalogDatabaseTest {
    @Autowired CatalogRepository catalog;
    @Autowired JdbcTemplate jdbc;

    @Test
    void searchesVietnameseNamesAndTreatsSqlWildcardsLiterally() {
        assertThat(catalog.listProducts("  GHE AN  ", "ban-ghe", "featured", 0, 24).items())
            .extracting(CatalogModels.Product::slug).containsExactly("ghe-an-nordic");
        assertThat(catalog.listProducts("ĐÈN BÀN", "", "featured", 0, 24).items())
            .extracting(CatalogModels.Product::slug).containsExactly("den-ban-horizon");
        assertThat(catalog.listProducts("%", "", "featured", 0, 24).total()).isZero();
        assertThat(catalog.listProducts("' OR 1=1 --", "", "featured", 0, 24).total()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ghe  an", " GHE\tAN ", "Ghế\năn", "\u00a0ghế\u00a0\u00a0ăn\u00a0", "\ufeffghe an\ufeff"})
    void normalizesSearchWhitespaceLikeTheFrontend(String query) {
        assertThat(catalog.listProducts(query, "ban-ghe", "featured", 0, 24).items())
            .extracting(CatalogModels.Product::slug).containsExactly("ghe-an-nordic");
    }

    @Test
    void sortsPricesAndPaginatesWithIndependentTotal() {
        var result = catalog.listProducts("", "", "price-asc", 0, 2);
        assertThat(result.items()).extracting(CatalogModels.Product::priceVnd).containsExactly(450000L, 620000L);
        assertThat(result.total()).isEqualTo(4);
        assertThat(catalog.listProducts("", "", "price-desc", 0, 24).items())
            .extracting(CatalogModels.Product::priceVnd).containsExactly(2900000L, 1250000L, 620000L, 450000L);
        assertThat(catalog.listProducts("", "", "featured", 10, 24).items()).isEmpty();
        assertThat(catalog.listProducts("", "", "invalid", 0, 24).items())
            .extracting(CatalogModels.Product::priceVnd).containsExactly(620000L, 2900000L, 1250000L, 450000L);
    }

    @Test
    void unpublishedProductsNeverAppearInListDetailOrFeaturedIds() {
        UUID productId = catalog.getProduct("ghe-an-nordic").orElseThrow().id();
        jdbc.update("UPDATE luma.products SET is_published = false WHERE id = ?", productId);
        assertThat(catalog.getProduct("ghe-an-nordic")).isEmpty();
        assertThat(catalog.listProducts("ghe an", "", "featured", 0, 24).total()).isZero();
        assertThat(catalog.getHomeContent().orElseThrow().featuredProductIds()).doesNotContain(productId);
        assertThat(catalog.getHomeFeaturedProducts(12)).extracting(CatalogModels.Product::id).doesNotContain(productId);
    }

    @Test
    void unpublishedCategoriesAlsoHideTheirProductsAndHomeIds() {
        UUID vaseId = catalog.getProduct("binh-gom-luma").orElseThrow().id();
        UUID cupsId = catalog.getProduct("bo-coc-gom-moc").orElseThrow().id();
        jdbc.update("UPDATE luma.categories SET is_published = false WHERE slug = ?", "gom-su");
        assertThat(catalog.listCategories()).extracting(CatalogModels.Category::slug).doesNotContain("gom-su");
        assertThat(catalog.getProduct("binh-gom-luma")).isEmpty();
        assertThat(catalog.listProducts("", "gom-su", "featured", 0, 24).total()).isZero();
        assertThat(catalog.getHomeContent().orElseThrow().featuredProductIds())
            .doesNotContain(vaseId, cupsId);
        assertThat(catalog.getHomeFeaturedProducts(12)).extracting(CatalogModels.Product::id).doesNotContain(vaseId, cupsId);
    }

    @Test
    void homeSectionsMatchPublishedContentAndFeaturedProductsRespectOrderAndLimit() {
        var content = catalog.getHomeContent().orElseThrow();
        var hero = catalog.getHomeHero().orElseThrow();
        assertThat(hero.heroTitle()).isEqualTo(content.heroTitle());
        assertThat(hero.heroDescription()).isEqualTo(content.heroDescription());
        assertThat(hero.heroImage()).isEqualTo(content.heroImage());
        assertThat(catalog.getHomeSpaces().orElseThrow()).isEqualTo(content.spaces());
        assertThat(catalog.getHomeFeaturedProducts(2)).extracting(CatalogModels.Product::slug)
            .containsExactly("binh-gom-luma", "ghe-an-nordic");
        jdbc.update("UPDATE luma.products SET is_featured = false WHERE slug = 'binh-gom-luma'");
        assertThat(catalog.getHomeFeaturedProducts(2)).extracting(CatalogModels.Product::slug)
            .containsExactly("ghe-an-nordic", "den-ban-horizon");
    }

    @Test
    void unpublishedHomeDocumentHidesHeroAndSpaces() {
        jdbc.update("UPDATE luma.home_content SET is_published = false WHERE code = 'home'");
        assertThat(catalog.getHomeHero()).isEmpty();
        assertThat(catalog.getHomeSpaces()).isEmpty();
    }

    @Test
    void readsGalleryAndHomeJsonWithoutLeakingInternalFields() {
        var product = catalog.getProduct("ghe-an-nordic").orElseThrow();
        assertThat(product.images()).hasSize(2);
        assertThat(product.images().get(1).width()).isEqualTo(1024);
        assertThat(product.specifications()).allMatch(spec -> spec.value() == null);
        assertThat(catalog.getHomeContent().orElseThrow().spaces()).hasSize(2);
        assertThat(catalog.getProduct("missing")).isEmpty();
    }

    @Test
    void uuidColumnsAndJsonReferencesRemainConsistent() {
        var products = catalog.listProducts("", "", "featured", 0, 24).items();
        var categoryIds = catalog.listCategories().stream().map(CatalogModels.Category::id).toList();
        assertThat(products).allSatisfy(product -> {
            assertThat(product.id()).isNotNull();
            assertThat(categoryIds).contains(product.categoryId());
        });
        assertThat(catalog.getHomeContent().orElseThrow().featuredProductIds())
            .containsExactlyElementsOf(products.stream().map(CatalogModels.Product::id).toList());
        var storedIds = jdbc.queryForObject(
            "SELECT content->'featuredProductIds' FROM luma.home_content WHERE code = 'home'", String.class);
        assertThat(storedIds).doesNotContain("p-");
        var storedUuids = jdbc.queryForList("""
            SELECT element.value::uuid FROM luma.home_content h,
            jsonb_array_elements_text(h.content->'featuredProductIds') WITH ORDINALITY AS element(value, position)
            WHERE h.code = 'home' ORDER BY element.position
            """, UUID.class);
        assertThat(storedUuids).containsExactlyElementsOf(products.stream().map(CatalogModels.Product::id).toList());
        assertThat(jdbc.queryForObject("SELECT id FROM luma.home_content WHERE code = 'home'", UUID.class)).isNotNull();
    }

    @Test
    void categoryFilterSupportsUuidAndSlugAndRejectsUntrustedValuesSafely() {
        var category = catalog.listCategories().stream().filter(c -> c.slug().equals("gom-su")).findFirst().orElseThrow();
        assertThat(catalog.listProducts("", category.id().toString(), "featured", 0, 24).items())
            .isEqualTo(catalog.listProducts("", category.slug(), "featured", 0, 24).items());
        assertThat(catalog.listProducts("", UUID.randomUUID().toString(), "featured", 0, 24).total()).isZero();
        assertThat(catalog.listProducts("", "' OR 1=1 --", "featured", 0, 24).total()).isZero();
    }

    @Test
    void productAttributesPreserveDecimalsAndUnknownValues() {
        var product = catalog.getProduct("ghe-an-nordic").orElseThrow();
        assertThat(product.lengthCm()).isNull();
        assertThat(product.widthCm()).isNull();
        assertThat(product.heightCm()).isNull();
        assertThat(product.material()).isNull();
        assertThat(product.color()).isNull();
        jdbc.update("""
            UPDATE luma.products SET length_cm = ?, width_cm = ?, height_cm = ?, material = ?, color = ? WHERE id = ?
            """, new BigDecimal("55.25"), new BigDecimal("48.50"), new BigDecimal("80.00"), "Vật liệu thử nghiệm", "Màu thử nghiệm", product.id());
        var updated = catalog.getProduct("ghe-an-nordic").orElseThrow();
        assertThat(updated.lengthCm()).isEqualByComparingTo("55.25");
        assertThat(updated.widthCm()).isEqualByComparingTo("48.50");
        assertThat(updated.heightCm()).isEqualByComparingTo("80.00");
        assertThat(updated.material()).isEqualTo("Vật liệu thử nghiệm");
        assertThat(updated.color()).isEqualTo("Màu thử nghiệm");
    }

    @Test
    void databaseGeneratesUuidWhenInsertingWithoutId() {
        UUID id = jdbc.queryForObject("""
            INSERT INTO luma.categories (slug, name) VALUES ('test-uuid-generated', 'Test UUID') RETURNING id
            """, UUID.class);
        assertThat(id).isNotNull();
        assertThat(id.version()).isEqualTo(4);
        UUID productId = jdbc.queryForObject("""
            INSERT INTO luma.products (slug, name, category_id, price_vnd)
            VALUES ('test-generated-product', 'Test product', ?, 0) RETURNING id
            """, UUID.class, id);
        UUID homeId = jdbc.queryForObject("""
            INSERT INTO luma.home_content (code, content) VALUES ('test-generated-home', '{}'::jsonb) RETURNING id
            """, UUID.class);
        assertThat(productId.version()).isEqualTo(4);
        assertThat(homeId.version()).isEqualTo(4);
    }

    @ParameterizedTest
    @CsvSource({"length_cm,0", "length_cm,-1", "width_cm,0", "width_cm,-1", "height_cm,0", "height_cm,-1"})
    void databaseRejectsNonPositiveDimensions(String column, BigDecimal value) {
        assertThatThrownBy(() -> jdbc.update("UPDATE luma.products SET " + column + " = ? WHERE slug = ?", value, "ghe-an-nordic"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsNaNDimension() {
        assertThatThrownBy(() -> jdbc.update("UPDATE luma.products SET length_cm = 'NaN'::numeric WHERE slug = ?", "ghe-an-nordic"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsUnknownCategoryUuid() {
        assertThatThrownBy(() -> jdbc.update("UPDATE luma.products SET category_id = ? WHERE slug = ?", UUID.randomUUID(), "ghe-an-nordic"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
