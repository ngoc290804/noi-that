package vn.luma.api.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.ConcurrencyFailureException;
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

    @Override
    protected org.springframework.http.ResponseEntity<Object> handleMethodArgumentNotValid(
            org.springframework.web.bind.MethodArgumentNotValidException exception,
            org.springframework.http.HttpHeaders headers, org.springframework.http.HttpStatusCode status, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Thông tin nhập chưa hợp lệ.");
        var errors = new java.util.LinkedHashMap<String,String>();
        exception.getBindingResult().getFieldErrors().forEach(error -> errors.putIfAbsent(error.getField(), error.getField()+": "+error.getDefaultMessage()));
        problem.setProperty("errors",errors);
        return handleExceptionInternal(exception,problem,headers,status,request);
    }

    @ExceptionHandler(ImageStorageException.class)
    ProblemDetail imageStorageFailure(ImageStorageException exception) {
        var problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setProperty("code", exception.code());
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail constraintViolation(DataIntegrityViolationException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
            "Tên đường dẫn đã được dùng (kể cả bản ghi đã xóa), hoặc dữ liệu còn được tham chiếu. Kiểm tra và thử lại.");
        problem.setProperty("code", "DATA_CONFLICT");
        return problem;
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    ProblemDetail concurrentWrite(ConcurrencyFailureException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
            "Có thao tác khác đang cập nhật dữ liệu. Nội dung đang nhập vẫn được giữ; vui lòng tải lại và đối chiếu.");
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
