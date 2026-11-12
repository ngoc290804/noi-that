package vn.luma.api.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static vn.luma.api.media.MediaModels.*;

@RestController
@RequestMapping("/api/admin/media")
public class MediaController {
    private final ImageUploadService images;

    public MediaController(ImageUploadService images) { this.images = images; }

    @GetMapping("/status")
    public StorageStatus status() { return images.status(); }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadedImage upload(
        @RequestParam MultipartFile file,
        @RequestParam @NotBlank @Size(max = 300) String alt,
        @RequestParam(required = false) UUID productId
    ) {
        return images.upload(file, alt, productId);
    }
}
