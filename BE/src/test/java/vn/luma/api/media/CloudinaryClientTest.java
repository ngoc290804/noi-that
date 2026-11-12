package vn.luma.api.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CloudinaryClientTest {
    MockRestServiceServer server;
    CloudinaryClient client;
    private static final String BASE = "https://api.cloudinary.com/v1_1/test-cloud";

    @BeforeEach void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CloudinaryClient(builder, "test-cloud", "test-key", "test-secret");
    }

    @Test
    void authenticatesWithHeaderAndChecksConnectivity() {
        server.expect(requestTo(BASE + "/ping")).andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Basic dGVzdC1rZXk6dGVzdC1zZWNyZXQ="))
            .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));
        assertThat(client.status().status()).isEqualTo("UP");
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"luma/media/test,luma/media", "luma/products/7abfb56a-c3a4-42b9-886d-5eced40b1a4d/test,luma/products/7abfb56a-c3a4-42b9-886d-5eced40b1a4d"})
    void uploadsWithRestrictedFormatsIntoExplicitAssetFolder(String publicId, String folder) {
        server.expect(requestTo(BASE + "/image/upload")).andExpect(method(HttpMethod.POST))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("jpg,jpeg,png,webp")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"asset_folder\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\r\n\r\n" + folder + "\r\n")))
            .andRespond(withSuccess("""
                {"public_id":"%s", "secure_url":"https://res.cloudinary.com/test-cloud/image/upload/v123/%s.jpg",
                 "resource_type":"image", "version":123, "width":1200, "height":896, "bytes":12345, "format":"jpg", "extra_provider_field":true}
                """.formatted(publicId, publicId), MediaType.APPLICATION_JSON));
        var image = client.upload(new byte[]{1,2,3}, MediaType.IMAGE_JPEG, publicId);
        assertThat(image.width()).isEqualTo(1200);
        assertThat(client.thumbnail(image)).isEqualTo(
            "https://res.cloudinary.com/test-cloud/image/upload/c_limit,w_600,q_auto,f_auto/v123/" + publicId + ".jpg");
        server.verify();
    }

    @Test
    void rejectsUnconfiguredStorageWithoutNetworkCall() {
        var unconfigured = new CloudinaryClient(RestClient.builder(), "", "", "");
        assertThatThrownBy(unconfigured::status).isInstanceOfSatisfying(ImageStorageException.class,
            error -> assertThat(error.code()).isEqualTo("CLOUDINARY_NOT_CONFIGURED"));
    }

    @Test
    void providerErrorDoesNotExposeResponseOrCredentials() {
        server.expect(requestTo(BASE + "/image/upload"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("provider-private-details:test-secret"));
        assertThatThrownBy(() -> client.upload(new byte[]{1}, MediaType.IMAGE_JPEG, "luma/media/test"))
            .isInstanceOfSatisfying(ImageStorageException.class, error -> {
                assertThat(error.code()).isEqualTo("CLOUDINARY_UNAVAILABLE");
                assertThat(error.getMessage()).doesNotContain("test-secret", "provider-private-details");
            });
        server.verify();
    }

    @Test
    void rejectsUnexpectedDeliveryUrls() {
        server.expect(requestTo(BASE + "/image/upload")).andRespond(withSuccess("""
            {"public_id":"luma/media/test", "secure_url":"http://untrusted.example/image.jpg", "resource_type":"image",
             "version":123, "width":1200, "height":896, "bytes":12345, "format":"jpg"}
            """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.upload(new byte[]{1}, MediaType.IMAGE_JPEG, "luma/media/test"))
            .isInstanceOf(ImageStorageException.class);
    }
}
