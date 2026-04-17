package com.grabmyseat;

import com.grabmyseat.auth.web.ApiException;
import com.grabmyseat.pay.PaymentException;
import com.grabmyseat.inventory.service.EventValidationException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ErrorAdvice {

    private static final Logger log = LoggerFactory.getLogger(ErrorAdvice.class);
    private static final String NOT_FOUND = "We could not find that. Go back and pick again.";
    private static final String BAD_INPUT = "Some details are missing or wrong. Check the form and try again.";

    @ExceptionHandler({EntityNotFoundException.class, AccessDeniedException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> notFound(Exception ex) {
        return error(HttpStatus.NOT_FOUND, NOT_FOUND, Map.of());
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> api(ApiException ex) {
        return error(ex.getStatus(), ex.getMessage(), Map.of());
    }

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ApiError> payment(PaymentException ex) {
        log.warn("payment call failed");
        return error(HttpStatus.BAD_GATEWAY, "We could not reach the payment service. Try again in a moment.", Map.of());
    }

    @ExceptionHandler(EventValidationException.class)
    public ResponseEntity<ApiError> event(EventValidationException ex) {
        return error(HttpStatus.BAD_REQUEST, BAD_INPUT, ex.fieldErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalid(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(field ->
                fields.putIfAbsent(field.getField(), "Pattern".equals(field.getCode()) ? field.getDefaultMessage() : "Check this field."));
        return error(HttpStatus.BAD_REQUEST, BAD_INPUT, fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, HandlerMethodValidationException.class,
            HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ApiError> badRequest(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, BAD_INPUT, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> conflict(DataIntegrityViolationException ex) {
        return error(HttpStatus.CONFLICT, "Those seats changed while you were booking. Try again.", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex) {
        log.error("unexpected error", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side. Try again in a moment.", Map.of());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message, fields));
    }
}
