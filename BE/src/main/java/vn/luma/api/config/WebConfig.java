package vn.luma.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String[] origins;

    public WebConfig(@Value("${app.cors.allowed-origins}") String[] origins) {
        this.origins = origins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/admin/**")
            .allowedOrigins(origins)
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("Authorization", "Content-Type", "X-CSRF-TOKEN")
            .allowCredentials(true)
            .maxAge(3600);
        registry.addMapping("/api/auth/**")
            .allowedOrigins(origins).allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("Authorization", "Content-Type", "X-CSRF-TOKEN")
            .allowCredentials(true).maxAge(3600);
        registry.addMapping("/api/**")
            .allowedOrigins(origins)
            .allowedMethods("GET", "HEAD", "OPTIONS")
            .maxAge(3600);
    }

}
