package vn.luma.api.media;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vn.luma.api.media.MediaModels.*;

@WebMvcTest(value = MediaController.class, properties = "app.media.upload-token=test-media-token-at-least-32-characters")
class MediaControllerTest {
    private static final String AUTH = "Bearer test-media-token-at-least-32-characters";
    @Autowired MockMvc mvc;
    @MockitoBean ImageUploadService images;

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{(byte)255, (byte)216, (byte)255, 0});
    }

    @Test
    void anonymousAndInvalidTokensCannotUploadOrCheckStatus() throws Exception {
        mvc.perform(multipart("/api/admin/media/images").file(file()).param("alt", "Ảnh sản phẩm"))
            .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"));
        mvc.perform(get("/api/admin/media/status").header("Authorization", "Bearer wrong-token"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(images);
    }

    @Test
    void authorizedUploadReturnsMetadataAndProductUuid() throws Exception {
        UUID productId = UUID.randomUUID();
        var uploaded = new UploadedImage("luma/test", "https://res.cloudinary.com/test/image/upload/test.jpg",
            "https://res.cloudinary.com/test/image/upload/w_600/test.jpg", "Ảnh sản phẩm", 1200, 896, 50000, "jpg", productId);
        when(images.upload(any(), eq("Ảnh sản phẩm"), eq(productId))).thenReturn(uploaded);
        mvc.perform(multipart("/api/admin/media/images").file(file()).header("Authorization", AUTH)
                .param("alt", "Ảnh sản phẩm").param("productId", productId.toString()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.publicId").value("luma/test"))
            .andExpect(jsonPath("$.productId").value(productId.toString()))
            .andExpect(jsonPath("$.width").value(1200));
    }

    @Test
    void rejectsMissingFileBlankAltAndInvalidProductUuidBeforeCallingStorage() throws Exception {
        mvc.perform(multipart("/api/admin/media/images").header("Authorization", AUTH).param("alt", "Ảnh"))
            .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/admin/media/images").file(file()).header("Authorization", AUTH).param("alt", " "))
            .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/admin/media/images").file(file()).header("Authorization", AUTH)
                .param("alt", "Ảnh").param("productId", "not-a-uuid"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(images);
    }

    @Test
    void returnsProtectedConnectivityStatus() throws Exception {
        when(images.status()).thenReturn(new StorageStatus("cloudinary", "test-cloud", "UP"));
        mvc.perform(get("/api/admin/media/status").header("Authorization", AUTH))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void returnsSafeProviderFailure() throws Exception {
        when(images.upload(any(), anyString(), isNull())).thenThrow(new ImageStorageException(
            HttpStatus.BAD_GATEWAY, "CLOUDINARY_UNAVAILABLE", "Không thể kết nối dịch vụ lưu ảnh."));
        mvc.perform(multipart("/api/admin/media/images").file(file()).header("Authorization", AUTH).param("alt", "Ảnh"))
            .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("CLOUDINARY_UNAVAILABLE"));
    }

    @Test
    void adminCorsAllowsAuthorizedPostFromFrontend() throws Exception {
        mvc.perform(options("/api/admin/media/images").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
            .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,OPTIONS"));
        mvc.perform(options("/api/admin/media/images").header("Origin", "https://other.example")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isForbidden());
    }
}
