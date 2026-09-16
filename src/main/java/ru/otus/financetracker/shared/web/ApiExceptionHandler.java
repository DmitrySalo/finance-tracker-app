package ru.otus.financetracker.shared.web;

import java.util.List;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ru.otus.financetracker.shared.ErrorCode;
import ru.otus.financetracker.api.auth.RegistrationRateLimitExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception,
                                                       WebRequest request) {
        var violations = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toViolation)
                .toList();
        return error(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Request validation failed.", request, violations);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiErrorResponse> handleMethodValidation(HandlerMethodValidationException exception,
                                                             WebRequest request) {
        var violations = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream().map(error ->
                        new ApiErrorResponse.Violation(
                                result.getMethodParameter().getParameterName(),
                                toErrorCode(resolveErrorCode(error.getCodes())),
                                error.getDefaultMessage()
                        )))
                .toList();
        return error(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Request validation failed.", request, violations);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException exception,
                                                              WebRequest request) {
        if (hasCause(exception, RequestSizeExceededException.class)) {
            return error(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.PAYLOAD_TOO_LARGE,
                    "Request body exceeds the allowed size.", request, List.of());
        }
        return error(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Request validation failed.", request, List.of());
    }

    @ExceptionHandler({ResourceNotFoundException.class, OwnershipDeniedException.class, NoResourceFoundException.class})
    ResponseEntity<ApiErrorResponse> handleNotFound(Exception exception, WebRequest request) {
        return error(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Resource not found.", request, List.of());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiErrorResponse> handleConflict(OptimisticLockingFailureException exception, WebRequest request) {
        return error(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "Resource state conflict.", request, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleForbidden(AccessDeniedException exception, WebRequest request) {
        return error(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "Access is denied.", request, List.of());
    }

    @ExceptionHandler(RegistrationRateLimitExceededException.class)
    ResponseEntity<ApiErrorResponse> handleRateLimit(RegistrationRateLimitExceededException exception, WebRequest request) {
        return error(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED, "Too many registration attempts.", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, WebRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred.", request, List.of());
    }

    private ApiErrorResponse.Violation toViolation(FieldError fieldError) {
        return new ApiErrorResponse.Violation(
                fieldError.getField(),
                toErrorCode(fieldError.getCode()),
                fieldError.getDefaultMessage()
        );
    }

    private String toErrorCode(String code) {
        if (code == null) {
            return "INVALID";
        }
        return code.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
    }

    private String resolveErrorCode(String[] codes) {
        if (codes == null || codes.length == 0) {
            return null;
        }
        return codes[codes.length - 1];
    }

    private boolean hasCause(Throwable exception, Class<? extends Throwable> causeType) {
        Throwable current = exception;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            ErrorCode code,
            String message,
            WebRequest request,
            List<ApiErrorResponse.Violation> violations
    ) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                code,
                message,
                (String) request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME, WebRequest.SCOPE_REQUEST),
                violations
        ));
    }
}
