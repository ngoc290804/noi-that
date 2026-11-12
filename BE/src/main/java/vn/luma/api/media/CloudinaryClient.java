package vn.luma.api.media;

import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import static vn.luma.api.media.MediaModels.*;

/** Server-side HTTPS calls to Cloudinary. Secrets never appear in request URLs or exceptions. */
public class CloudinaryClient {
    private static final Logger log = LoggerFactory.getLogger(CloudinaryClient.class);
    private static final Set<String> FORMATS = Set.of("jpg", "jpeg", "png", "webp");
    private final RestClient http;
    private final String cloudName;
    private final boolean configured;

    public CloudinaryClient(RestClient.Builder builder, String cloudName, String apiKey, String apiSecret) {
        this.cloudName = cloudName;
        boolean validName = cloudName.matches("[a-zA-Z0-9_-]{1,100}");
        this.configured = validName && !apiKey.isBlank() && !apiSecret.isBlank();
        this.http = builder.baseUrl("https://api.cloudinary.com/v1_1/" + (validName ? cloudName : "unconfigured"))
            .defaultHeaders(headers -> headers.setBasicAuth(apiKey, apiSecret)).build();
    }

    public void requireConfigured() {
        if (!configured) throw new ImageStorageException(HttpStatus.SERVICE_UNAVAILABLE,
            "CLOUDINARY_NOT_CONFIGURED", "Chưa cấu hình đầy đủ dịch vụ lưu ảnh.");
    }

    public StorageStatus status() {
        requireConfigured();
        try {
            var response = http.get().uri("/ping").retrieve().body(Map.class);
            if (response == null || !"ok".equals(response.get("status"))) throw unavailable();
            return new StorageStatus("cloudinary", cloudName, "UP");
        } catch (RestClientException exception) {
            log.warn("Cloudinary status request failed ({})", exception.getClass().getSimpleName());
            throw unavailable();
        }
    }

    public CloudinaryUpload upload(byte[] data, MediaType mediaType, String publicId) {
        requireConfigured();
        var body = new LinkedMultiValueMap<String, Object>();
        var fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(mediaType);
        body.add("file", new HttpEntity<>(new ByteArrayResource(data) {
            @Override public String getFilename() { return "image." + mediaType.getSubtype(); }
        }, fileHeaders));
        body.add("public_id", publicId);
        // Dynamic folder mode requires an explicit Media Library location.
        body.add("asset_folder", publicId.substring(0, publicId.lastIndexOf('/')));
        body.add("overwrite", "false");
        body.add("allowed_formats", "jpg,jpeg,png,webp");
        try {
            var response = http.post().uri("/image/upload").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body).retrieve().body(CloudinaryUpload.class);
            if (response == null || !publicId.equals(response.publicId()) || !"image".equals(response.resourceType())
                || response.secureUrl() == null
                || !response.secureUrl().startsWith("https://res.cloudinary.com/" + cloudName + "/image/upload/")
                || response.version() <= 0 || response.width() <= 0 || response.height() <= 0
                || response.bytes() <= 0 || response.format() == null || !FORMATS.contains(response.format())) throw unavailable();
            return response;
        } catch (RestClientResponseException exception) {
            log.warn("Cloudinary upload returned HTTP {}", exception.getStatusCode().value());
            if (exception.getStatusCode().value() == 400) throw new ImageStorageException(HttpStatus.BAD_REQUEST,
                "IMAGE_REJECTED", "Ảnh không hợp lệ hoặc không được dịch vụ lưu ảnh chấp nhận.");
            throw unavailable();
        } catch (RestClientException exception) {
            log.warn("Cloudinary upload failed ({})", exception.getClass().getSimpleName());
            throw unavailable();
        }
    }

    public String thumbnail(CloudinaryUpload image) {
        return "https://res.cloudinary.com/" + cloudName + "/image/upload/c_limit,w_600,q_auto,f_auto/v"
            + image.version() + "/" + image.publicId() + "." + image.format();
    }

    /** Compensation only for an image created by this request when attaching it to the DB fails. */
    public void discardNewUpload(String publicId) {
        var body = new LinkedMultiValueMap<String, String>();
        body.add("public_id", publicId);
        body.add("invalidate", "true");
        try {
            http.post().uri("/image/destroy").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body).retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn("Could not compensate a new Cloudinary upload ({})", exception.getClass().getSimpleName());
        }
    }

    private ImageStorageException unavailable() {
        return new ImageStorageException(HttpStatus.BAD_GATEWAY, "CLOUDINARY_UNAVAILABLE",
            "Không thể kết nối dịch vụ lưu ảnh. Vui lòng thử lại sau.");
    }
}
