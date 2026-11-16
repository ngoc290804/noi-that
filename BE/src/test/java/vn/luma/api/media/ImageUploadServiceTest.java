package vn.luma.api.media;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static vn.luma.api.media.MediaModels.*;

@ExtendWith(MockitoExtension.class)
class ImageUploadServiceTest {
    @Mock CloudinaryClient cloudinary;
    @Mock ProductImageRepository products;
    ImageUploadService service;

    @BeforeEach void setUp() { service = new ImageUploadService(cloudinary, products); }

    private MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "untrusted-original-name.jpg", "image/jpeg",
            new byte[]{(byte)255, (byte)216, (byte)255, 0});
    }

    private void storageSucceeds() {
        when(cloudinary.upload(any(), any(), anyString())).thenAnswer(invocation -> {
            String publicId = invocation.getArgument(2);
            return new CloudinaryUpload(publicId, "https://res.cloudinary.com/test/image/upload/" + publicId + ".jpg",
                "image", 1, 1200, 896, 40000, "jpg");
        });
        when(cloudinary.thumbnail(any())).thenReturn("https://res.cloudinary.com/test/image/upload/w_600/test.jpg");
    }

    @Test
    void standaloneUploadUsesUniquePublicIdAndPersistsLibraryMetadata() {
        storageSucceeds();
        var result = service.upload(jpeg(), "  Ghế ăn  ", null);
        assertThat(result.alt()).isEqualTo("Ghế ăn");
        assertThat(result.publicId()).startsWith("luma/media/");
        assertThatCode(() -> UUID.fromString(result.publicId().substring("luma/media/".length()))).doesNotThrowAnyException();
        verify(products).remember(argThat(image -> image.alt().equals("Ghế ăn") && image.publicId().equals(result.publicId())));
    }

    @Test
    void productUploadAppendsMetadataAfterStorageSucceeds() {
        UUID id = UUID.randomUUID();
        when(products.exists(id)).thenReturn(true);
        when(products.append(eq(id), any())).thenReturn(true);
        storageSucceeds();
        var result = service.upload(jpeg(), "Ghế ăn", id);
        assertThat(result.productId()).isEqualTo(id);
        assertThat(result.publicId()).startsWith("luma/products/" + id + "/");
        verify(products).append(eq(id), argThat(image -> image.publicId().equals(result.publicId())
            && image.width() == 1200 && image.alt().equals("Ghế ăn")));
    }

    @Test
    void missingProductNeverUploads() {
        assertThatThrownBy(() -> service.upload(jpeg(), "Ghế", UUID.randomUUID()))
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(cloudinary, never()).upload(any(), any(), anyString());
    }

    @Test
    void compensatesOnlyTheNewAssetIfDatabaseWriteFails() {
        UUID id = UUID.randomUUID();
        when(products.exists(id)).thenReturn(true);
        when(products.append(eq(id), any())).thenThrow(new DataAccessResourceFailureException("test failure"));
        storageSucceeds();
        assertThatThrownBy(() -> service.upload(jpeg(), "Ghế", id)).isInstanceOf(DataAccessResourceFailureException.class);
        verify(cloudinary).discardNewUpload(startsWith("luma/products/" + id + "/"));
    }

    @Test
    void compensatesWhenProductDisappearsDuringUpload() {
        UUID id = UUID.randomUUID();
        when(products.exists(id)).thenReturn(true);
        storageSucceeds();
        assertThatThrownBy(() -> service.upload(jpeg(), "Ghế", id)).isInstanceOf(ResponseStatusException.class);
        verify(cloudinary).discardNewUpload(startsWith("luma/products/" + id + "/"));
    }

    @Test
    void rejectsEmptyAndFakeImagesBeforeUploading() {
        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", new byte[0]), "Ảnh", null))
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        var fake = new MockMultipartFile("file", "fake.jpg", "image/jpeg", "<svg>not a JPEG</svg>".getBytes());
        assertThatThrownBy(() -> service.upload(fake, "Ảnh", null))
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
        verify(cloudinary, never()).upload(any(), any(), anyString());
    }

    @Test
    void rejectsOversizedImagesAndInvalidAlt() {
        var large = new MockMultipartFile("file", new byte[(int)ImageUploadService.MAX_IMAGE_BYTES + 1]);
        assertThatThrownBy(() -> service.upload(large, "Ảnh", null))
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
        assertThatThrownBy(() -> service.upload(jpeg(), " ", null)).isInstanceOf(ResponseStatusException.class);
        verify(cloudinary, never()).upload(any(), any(), anyString());
    }

    @Test
    void recognizesPngAndWebpFromFileBytes() {
        storageSucceeds();
        var png = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{(byte)137, 80, 78, 71, 13, 10, 26, 10});
        service.upload(png, "PNG", null);
        verify(cloudinary).upload(any(), eq(MediaType.IMAGE_PNG), anyString());
        var webp = new MockMultipartFile("file", "photo.webp", "image/webp", new byte[]{'R','I','F','F',0,0,0,0,'W','E','B','P'});
        service.upload(webp, "WebP", null);
        verify(cloudinary).upload(any(), eq(MediaType.parseMediaType("image/webp")), anyString());
    }
}
