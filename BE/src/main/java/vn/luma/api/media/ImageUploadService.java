package vn.luma.api.media;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.luma.api.catalog.CatalogModels.ProductImage;

import static vn.luma.api.media.MediaModels.*;

@Service
public class ImageUploadService {
    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private final CloudinaryClient cloudinary;
    private final ProductImageRepository products;

    public ImageUploadService(CloudinaryClient cloudinary, ProductImageRepository products) {
        this.cloudinary = cloudinary;
        this.products = products;
    }

    public StorageStatus status() { return cloudinary.status(); }

    @org.springframework.transaction.annotation.Transactional
    public UploadedImage upload(MultipartFile file, String alt, UUID productId) {
        cloudinary.requireConfigured();
        if (alt == null || alt.isBlank() || alt.length() > 300)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mô tả ảnh phải có từ 1 đến 300 ký tự.");
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn ảnh.");
        if (file.getSize() > MAX_IMAGE_BYTES)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Ảnh không được vượt quá 10 MB.");
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không đọc được tệp ảnh.");
        }
        MediaType type = imageType(data);
        if (productId != null && !products.exists(productId)) throw missingProduct();
        String publicId = "luma/" + (productId == null ? "media/" : "products/" + productId + "/") + UUID.randomUUID();
        var uploaded = cloudinary.upload(data, type, publicId);
        var image = new ProductImage(uploaded.secureUrl(), cloudinary.thumbnail(uploaded), alt.strip(),
            uploaded.width(), uploaded.height(), uploaded.publicId());
        try {
            products.remember(image);
            if (productId != null && !products.append(productId, image)) throw missingProduct();
        } catch (RuntimeException exception) {
            cloudinary.discardNewUpload(uploaded.publicId());
            throw exception;
        }
        return new UploadedImage(image.publicId(), image.src(), image.thumbnail(), image.alt(), image.width(),
            image.height(), uploaded.bytes(), uploaded.format(), productId);
    }

    private MediaType imageType(byte[] data) {
        if (data.length >= 3 && (data[0] & 255) == 255 && (data[1] & 255) == 216 && (data[2] & 255) == 255)
            return MediaType.IMAGE_JPEG;
        byte[] pngHeader = {(byte)137, 80, 78, 71, 13, 10, 26, 10};
        if (data.length >= 8 && Arrays.equals(Arrays.copyOf(data, 8), pngHeader)) return MediaType.IMAGE_PNG;
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
            && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P')
            return MediaType.parseMediaType("image/webp");
        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Chỉ chấp nhận ảnh JPEG, PNG hoặc WebP.");
    }

    private ResponseStatusException missingProduct() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm để gắn ảnh.");
    }
}
