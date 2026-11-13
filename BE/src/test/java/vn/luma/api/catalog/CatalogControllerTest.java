package vn.luma.api.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vn.luma.api.catalog.CatalogModels.*;

@WebMvcTest(CatalogController.class)
class CatalogControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CatalogRepository catalog;

    @Test
    void returnsCatalogAndPassesBoundFilters() throws Exception {
        when(catalog.listProducts("ghe an", "ban-ghe", "price-asc", 0, 24))
            .thenReturn(new ProductPage(List.of(), 0, 0, 24));
        mvc.perform(get("/api/products").param("q", "ghe an").param("category", "ban-ghe").param("sort", "price-asc"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.size").value(24));
        verify(catalog).listProducts("ghe an", "ban-ghe", "price-asc", 0, 24);
    }

    @Test
    void rejectsInvalidPaginationAndOversizedSearchBeforeQueryingDatabase() throws Exception {
        for (String query : List.of("size=0", "size=101", "page=-1", "page=abc", "q=" + "a".repeat(121))) {
            mvc.perform(get("/api/products?" + query)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(catalog);
    }

    @Test
    void missingProductReturns404() throws Exception {
        when(catalog.getProduct("missing")).thenReturn(Optional.empty());
        mvc.perform(get("/api/products/missing"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void databaseFailureIs503AndDoesNotExposeSqlOrCredentials() throws Exception {
        when(catalog.listProducts(anyString(), anyString(), anyString(), anyInt(), anyInt()))
            .thenThrow(new DataAccessResourceFailureException("sensitive-database-details"));
        mvc.perform(get("/api/products"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sensitive-database-details"))));
    }

    @Test
    void categoryEndpointReturnsPublicFields() throws Exception {
        UUID categoryId = UUID.randomUUID();
        when(catalog.listCategories()).thenReturn(List.of(new Category(categoryId, "den", "Đèn")));
        mvc.perform(get("/api/categories"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Đèn"))
            .andExpect(jsonPath("$[0].id").value(categoryId.toString()));
    }

    @Test
    void heroIsIndependentAndContainsOnlyBannerFields() throws Exception {
        when(catalog.getHomeHero()).thenReturn(Optional.of(new HomeHero("Luma", "Giới thiệu", null)));
        mvc.perform(get("/api/home/hero"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.heroTitle").value("Luma"))
            .andExpect(jsonPath("$.featuredProductIds").doesNotExist())
            .andExpect(jsonPath("$.spaces").doesNotExist());
        verify(catalog).getHomeHero();
        verifyNoMoreInteractions(catalog);
    }

    @Test
    void featuredProductsAreBoundedAndDoNotScanCatalogPages() throws Exception {
        when(catalog.getHomeFeaturedProducts(4)).thenReturn(List.of());
        when(catalog.getHomeFeaturedProducts(2)).thenReturn(List.of());
        mvc.perform(get("/api/home/featured-products")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/home/featured-products?size=2")).andExpect(status().isOk());
        verify(catalog).getHomeFeaturedProducts(4);
        verify(catalog).getHomeFeaturedProducts(2);
        verifyNoMoreInteractions(catalog);
    }

    @Test
    void rejectsInvalidHomeFeaturedLimitsBeforeQuerying() throws Exception {
        for (String size : List.of("0", "-1", "13", "abc")) {
            mvc.perform(get("/api/home/featured-products").param("size", size)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(catalog);
    }

    @Test
    void spacesAreReturnedWithoutLoadingOtherHomeSections() throws Exception {
        when(catalog.getHomeSpaces()).thenReturn(Optional.of(List.of(new Space("Phòng khách", "Không gian sống", null, "/san-pham"))));
        mvc.perform(get("/api/home/spaces"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Phòng khách"))
            .andExpect(jsonPath("$[0].href").value("/san-pham"));
        verify(catalog).getHomeSpaces();
        verifyNoMoreInteractions(catalog);
    }

    @Test
    void missingHomeSectionsReturn404Independently() throws Exception {
        when(catalog.getHomeHero()).thenReturn(Optional.empty());
        when(catalog.getHomeSpaces()).thenReturn(Optional.empty());
        mvc.perform(get("/api/home/hero")).andExpect(status().isNotFound());
        mvc.perform(get("/api/home/spaces")).andExpect(status().isNotFound());
    }

    @Test
    void failedFeaturedSectionDoesNotPreventHeroResponse() throws Exception {
        when(catalog.getHomeFeaturedProducts(4)).thenThrow(new DataAccessResourceFailureException("database-private-details"));
        when(catalog.getHomeHero()).thenReturn(Optional.of(new HomeHero("Luma", "Giới thiệu", null)));
        mvc.perform(get("/api/home/featured-products"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"));
        mvc.perform(get("/api/home/hero")).andExpect(status().isOk()).andExpect(jsonPath("$.heroTitle").value("Luma"));
    }

    @Test
    void productResponseSerializesUuidAndStructuredAttributes() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        var product = new Product(productId, "test-product", "Test product", categoryId, 0,
            "", "", List.of(), List.of(), 0, false,
            new BigDecimal("55.25"), new BigDecimal("48.50"), new BigDecimal("80.00"), "Test material", "Test color");
        when(catalog.getProduct("test-product")).thenReturn(Optional.of(product));
        mvc.perform(get("/api/products/test-product"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(productId.toString()))
            .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
            .andExpect(jsonPath("$.lengthCm").value(55.25))
            .andExpect(jsonPath("$.widthCm").value(48.5))
            .andExpect(jsonPath("$.heightCm").value(80))
            .andExpect(jsonPath("$.material").value("Test material"))
            .andExpect(jsonPath("$.color").value("Test color"));
    }

    @Test
    void corsAllowsConfiguredFrontendAndRejectsOtherOrigins() throws Exception {
        mvc.perform(options("/api/products").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/products").header("Origin", "https://other.example")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden());
    }

    @Test
    void publicApiHasNoProductMutation() throws Exception {
        mvc.perform(post("/api/products").contentType("application/json").content("{}"))
            .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(catalog);
    }
}
