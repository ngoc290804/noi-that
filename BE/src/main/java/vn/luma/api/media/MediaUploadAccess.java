package vn.luma.api.media;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

public class MediaUploadAccess implements HandlerInterceptor {
    private final String token;

    public MediaUploadAccess(String token) { this.token = token; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equals(request.getMethod())) return true;
        if (token.length() < 32) throw new ImageStorageException(HttpStatus.SERVICE_UNAVAILABLE,
            "MEDIA_ACCESS_NOT_CONFIGURED", "Chưa cấu hình quyền quản lý ảnh.");
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !MessageDigest.isEqual(
            ("Bearer " + token).getBytes(StandardCharsets.UTF_8), authorization.getBytes(StandardCharsets.UTF_8))) {
            response.setHeader("WWW-Authenticate", "Bearer");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cần token quản lý ảnh hợp lệ.");
        }
        return true;
    }
}
