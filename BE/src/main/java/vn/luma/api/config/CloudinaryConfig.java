package vn.luma.api.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import vn.luma.api.media.CloudinaryClient;

@Configuration
public class CloudinaryConfig {
    @Bean
    CloudinaryClient cloudinaryClient(
        RestClient.Builder builder,
        @Value("${app.cloudinary.cloud-name}") String cloudName,
        @Value("${app.cloudinary.api-key}") String apiKey,
        @Value("${app.cloudinary.api-secret}") String apiSecret
    ) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(30));
        return new CloudinaryClient(builder.requestFactory(factory), cloudName, apiKey, apiSecret);
    }
}
