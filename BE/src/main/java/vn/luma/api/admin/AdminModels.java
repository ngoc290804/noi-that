package vn.luma.api.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.luma.api.catalog.CatalogModels;

public final class AdminModels {
    private AdminModels() {}
    public record ImageRef(@NotNull UUID id) {}
    public record SpecificationInput(@NotBlank @Size(max=150) String label,
        @NotBlank @Size(max=10000) String value, @Size(max=30) String unit) {}
    public record ProductInput(
        @NotBlank @Size(max=200) String name,
        @NotBlank @Size(max=160) @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
        @NotNull UUID categoryId,
        @NotNull @Min(0) @Max(9007199254740991L) Long priceVnd,
        @NotNull @Size(max=1000) String shortDescription,
        @NotNull @Size(max=30000) String description,
        @NotNull @Size(max=20000) String careInstructions,
        @NotNull @Size(max=30) List<@NotNull @Valid ImageRef> images,
        @NotNull @Size(max=50) List<@NotNull @Valid SpecificationInput> specifications,
        @NotNull @Min(0) @Max(1000000) Integer displayOrder,
        boolean isFeatured,
        @NotNull @Pattern(regexp="published|draft|archived") String status,
        @DecimalMin(value="0", inclusive=false) @DecimalMax("99999999.99") @Digits(integer=8, fraction=2) BigDecimal lengthCm,
        @DecimalMin(value="0", inclusive=false) @DecimalMax("99999999.99") @Digits(integer=8, fraction=2) BigDecimal widthCm,
        @DecimalMin(value="0", inclusive=false) @DecimalMax("99999999.99") @Digits(integer=8, fraction=2) BigDecimal heightCm,
        @Size(max=255) String material, @Size(max=100) String color,
        @NotNull @Min(0) Long version
    ) {}
    public record CategoryInput(@NotBlank @Size(max=200) String name,
        @NotBlank @Size(max=100) @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
        boolean active, @Min(0) @Max(1000000) int displayOrder, @NotNull @Min(0) Long version) {}
    public record Category(UUID id, String name, String slug, boolean active, int displayOrder, long version) {}
    public record Asset(UUID id, String src, String thumbnail, String publicId, String alt,
        int width, int height, long version, long usedCount) {}
    public record AssetInput(@NotBlank @Size(max=300) String alt, @NotNull @Min(0) Long version) {}
    public record Product(UUID id, String name, String slug, UUID categoryId, long priceVnd,
        String shortDescription, String description, String careInstructions,
        List<Asset> images, List<CatalogModels.Specification> specifications, int displayOrder,
        boolean isFeatured, String status, BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm,
        String material, String color, Instant updatedAt, long version) {}
    public record SpaceInput(@NotBlank @Size(max=200) String title, @NotNull @Size(max=1000) String description,
        @NotNull @Valid ImageRef image, @NotBlank @Size(max=500) @Pattern(regexp="/san-pham(?:\\?[^\\s]*)?") String href) {}
    public record HomeInput(@NotBlank @Size(max=180) String heroTitle,
        @NotNull @Size(max=1000) String heroDescription, @NotNull @Valid ImageRef heroImage,
        @NotNull @Size(max=100) List<@NotNull UUID> featuredProductIds,
        @NotNull @Size(max=20) List<@NotNull @Valid SpaceInput> spaces, @NotNull @Min(0) Long version) {}
    public record HomeDocument(CatalogModels.HomeContent content, long version) {}
    public record Catalog(List<Product> products, List<Category> categories, List<Asset> media,
        CatalogModels.HomeContent content, long contentVersion) {}
    public record ProductVersion(@NotNull UUID id, @NotNull @Min(0) Long version) {}
    public record ArchiveInput(@NotEmpty @Size(max=100) List<@NotNull @Valid ProductVersion> products) {}
}
