package vn.luma.api.media;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

public final class MediaModels {
    private MediaModels() {}

    public record UploadedImage(
        String publicId, String src, String thumbnail, String alt,
        int width, int height, long bytes, String format, UUID productId
    ) {}
    public record StorageStatus(String provider, String cloudName, String status) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CloudinaryUpload(
        @JsonProperty("public_id") String publicId,
        @JsonProperty("secure_url") String secureUrl,
        @JsonProperty("resource_type") String resourceType,
        long version, int width, int height, long bytes, String format
    ) {}
}
