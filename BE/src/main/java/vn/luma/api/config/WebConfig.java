package vn.luma.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import vn.luma.api.media.MediaUploadAccess;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String[] origins;
    private final MediaUploadAccess mediaAccess;

    public WebConfig(@Value("${app.cors.allowed-origins}") String[] origins,
                     @Value("${app.media.upload-token:}") String uploadToken) {
        this.origins = origins;
        this.mediaAccess = new MediaUploadAccess(uploadToken);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/admin/**")
            .allowedOrigins(origins)
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("Authorization", "Content-Type")
            .maxAge(3600);
        registry.addMapping("/api/**")
            .allowedOrigins(origins)
            .allowedMethods("GET", "HEAD", "OPTIONS")
            .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(mediaAccess).addPathPatterns("/api/admin/**");
    }
}
