package vn.luma.api.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class CatalogModels {
    private CatalogModels() {}

    public record Category(UUID id, String slug, String name) {}
    public record ProductImage(String src, String thumbnail, String alt, int width, int height, String publicId) {}
    public record Specification(String label, String value, String unit) {}
    public record Product(
        UUID id, String slug, String name, UUID categoryId, long priceVnd,
        String shortDescription, String description, List<ProductImage> images,
        List<Specification> specifications, int displayOrder, boolean isFeatured,
        BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm, String material, String color
    ) {}
    public record ProductPage(List<Product> items, long total, int page, int size) {}
    public record Space(String title, String description, ProductImage image, String href) {}
    public record HomeHero(String heroTitle, String heroDescription, ProductImage heroImage) {}
    public record HomeContent(
        String heroTitle, String heroDescription, ProductImage heroImage,
        List<UUID> featuredProductIds, List<Space> spaces
    ) {}
}
