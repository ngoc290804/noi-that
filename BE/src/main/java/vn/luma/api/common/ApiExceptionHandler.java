package vn.luma.api.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import vn.luma.api.media.ImageStorageException;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ImageStorageException.class)
    ProblemDetail imageStorageFailure(ImageStorageException exception) {
        var problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setProperty("code", exception.code());
        return problem;
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail databaseUnavailable(DataAccessException exception, WebRequest request) {
        log.error("Catalog database request failed ({})", exception.getClass().getSimpleName());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
            "Dữ liệu tạm thời chưa sẵn sàng. Vui lòng thử lại sau.");
        problem.setProperty("code", "DATABASE_UNAVAILABLE");
        return problem;
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail invalidContent(IllegalStateException exception) {
        log.error("Catalog content could not be read ({})", exception.getClass().getSimpleName());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
            "Không thể đọc nội dung. Vui lòng thử lại sau.");
        problem.setProperty("code", "CONTENT_UNAVAILABLE");
        return problem;
    }
}
