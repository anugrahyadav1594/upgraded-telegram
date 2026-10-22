package com.realtimechat.common.error;

import com.realtimechat.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Converts every exception into the single documented error contract.
 *
 * <p>Guarantees:
 * <ul>
 *   <li>No stack traces, SQL text, internal class names, host names or secrets reach the client.</li>
 *   <li>Unexpected failures are logged with the full stack trace and the {@code requestId}, and
 *       answered with a generic {@code 500 INTERNAL_ERROR}.</li>
 *   <li>Expected failures are logged at WARN/INFO without message content.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY");

    // -------------------------------------------------------------------------------
    // Expected, deliberate failures
    // -------------------------------------------------------------------------------

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        if (code == ErrorCode.FORBIDDEN || code == ErrorCode.BLOCKED) {
            securityLog.warn("authorization denied code={} path={} requestId={}",
                    code, request.getRequestURI(), RequestIdFilter.currentRequestId());
        }
        return ResponseEntity.status(code.status())
                .body(ApiErrorResponse.of(code, ex.getMessage(), request.getRequestURI(),
                        RequestIdFilter.currentRequestId()));
    }

    // -------------------------------------------------------------------------------
    // Request parsing / validation
    // -------------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                             HttpServletRequest request) {
        List<FieldErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(
                        error.getField(),
                        error.getCode() == null ? "INVALID" : error.getCode().toUpperCase(),
                        error.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(ApiErrorResponse.of(ErrorCode.VALIDATION_FAILED,
                        "The request could not be processed",
                        request.getRequestURI(),
                        RequestIdFilter.currentRequestId(),
                        details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                     HttpServletRequest request) {
        List<FieldErrorDetail> details = ex.getConstraintViolations().stream()
                .map(violation -> new FieldErrorDetail(
                        violation.getPropertyPath().toString(),
                        "INVALID",
                        violation.getMessage()))
                .toList();
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(ApiErrorResponse.of(ErrorCode.VALIDATION_FAILED,
                        "The request could not be processed",
                        request.getRequestURI(),
                        RequestIdFilter.currentRequestId(),
                        details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
                                                             HttpServletRequest request) {
        // Covers malformed JSON and rejected unknown properties (mass-assignment protection).
        log.debug("unreadable request body path={} reason={}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.status())
                .body(ApiErrorResponse.of(ErrorCode.INVALID_REQUEST,
                        "The request body is invalid",
                        request.getRequestURI(),
                        RequestIdFilter.currentRequestId()));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiErrorResponse> handleBadParameter(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.status())
                .body(ApiErrorResponse.of(ErrorCode.INVALID_REQUEST,
                        "The request contains an invalid parameter",
                        request.getRequestURI(),
                        RequestIdFilter.currentRequestId()));
    }

    // -------------------------------------------------------------------------------
    // Security
    // -------------------------------------------------------------------------------

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException ex,
                                                                HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.UNAUTHENTICATED.status())
                .body(ApiErrorResponse.of(ErrorCode.UNAUTHENTICATED,
                        "Authentication is required",
                        request.getRequestURI(),
                        RequestIdFilter.currentRequestId()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                              HttpServletRequest request) {
        securityLog.warn("access denied path={} requestId={}", request.getRequestURI(),
                RequestIdFilter.currentRequestId());
        return ResponseEntity.status(ErrorCode.FORBIDDEN.status())
                .body(ApiErrorResponse.of(ErrorCode.FORBIDDEN,
                        "You do not have permission to perform this action",
                        request.getRequestURI(),
                        RequestIdFilter.currentRequestId()));
    }

    // -------------------------------------------------------------------------------
    // Routing
    // -------------------------------------------------------------------------------

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.status())
                .body(ApiErrorResponse.of(ErrorCode.NOT_FOUND,
                        "The requested resource does not exist",
                        request.getRequestURI(),
                        RequestIdFilter.currentRequestId()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                                   HttpServletRequest request) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ApiErrorResponse(java.time.Instant.now(), 405, "METHOD_NOT_ALLOWED",
                        "This method is not supported for this resource",
                        request.getRequestURI(), RequestIdFilter.currentRequestId(), null));
    }

    // -------------------------------------------------------------------------------
    // Infrastructure
    // -------------------------------------------------------------------------------

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleDataAccess(DataAccessException ex, HttpServletRequest request) {
        // Never surface SQL. Log with the request id so it can be correlated.
        log.error("database error path={} requestId={}", request.getRequestURI(),
                RequestIdFilter.currentRequestId(), ex);
        return ResponseEntity.status(ErrorCode.SERVICE_UNAVAILABLE.status())
                .body(ApiErrorResponse.of(ErrorCode.SERVICE_UNAVAILABLE,
                        "The service is temporarily unavailable",
                        request.getRequestURI(),
                        RequestIdFilter.currentRequestId()));
    }

    @ExceptionHandler({org.springframework.dao.QueryTimeoutException.class, TimeoutException.class})
    public ResponseEntity<ApiErrorResponse> handleTimeout(Exception ex, HttpServletRequest request) {
        log.warn("dependency timeout path={} requestId={}", request.getRequestURI(),
                RequestIdFilter.currentRequestId(), ex);
        return ResponseEntity.status(ErrorCode.SERVICE_UNAVAILABLE.status())
                .body(ApiErrorResponse.of(ErrorCode.SERVICE_UNAVAILABLE,
                        "The service is temporarily unavailable",
                        request.getRequestURI(),
                        RequestIdFilter.currentRequestId()));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadTooLarge(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.PAYLOAD_TOO_LARGE.status())
                .body(ApiErrorResponse.of(ErrorCode.PAYLOAD_TOO_LARGE,
                        "The uploaded content is too large",
                        request.getRequestURI(),
                        RequestIdFilter.currentRequestId()));
    }

    // -------------------------------------------------------------------------------
    // Catch-all
    // -------------------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("unhandled exception path={} requestId={}", request.getRequestURI(),
                RequestIdFilter.currentRequestId(), ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiErrorResponse.of(ErrorCode.INTERNAL_ERROR,
                        "An unexpected error occurred",
                        request.getRequestURI(),
                        RequestIdFilter.currentRequestId()));
    }
}
