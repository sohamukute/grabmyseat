package com.grabmyseat.inventory.web;

import com.grabmyseat.inventory.security.InvalidQueuePermitException;
import com.grabmyseat.inventory.service.EventValidationException;
import com.grabmyseat.inventory.security.MissingQueuePermitException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class InventoryExceptionHandler {

    @ExceptionHandler(MissingQueuePermitException.class)
    public ResponseEntity<ApiError> handleMissingPermit(MissingQueuePermitException ex) {
        return error(HttpStatus.UNAUTHORIZED, "queue_permit_missing", "A queue permit is required.");
    }

    @ExceptionHandler(InvalidQueuePermitException.class)
    public ResponseEntity<ApiError> handleInvalidPermit(InvalidQueuePermitException ex) {
        return error(HttpStatus.CONFLICT, "queue_permit_invalid", "The queue permit is invalid or expired.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage() == null ? "Invalid value." : error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError(HttpStatus.BAD_REQUEST.value(), "validation_error", "Invalid request.", fieldErrors));
    }

    @ExceptionHandler(EventValidationException.class)
    public ResponseEntity<ApiError> eventValidation(EventValidationException ex) {
        return ResponseEntity.badRequest().body(new ApiError(
                HttpStatus.BAD_REQUEST.value(), "validation_error", "Invalid event.", ex.fieldErrors()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        String code = status == HttpStatus.UNAUTHORIZED.value() ? "unauthorized"
                : status == HttpStatus.FORBIDDEN.value() ? "forbidden"
                : "request_error";
        String message = ex.getReason() == null ? "The request could not be completed." : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ApiError(status, code, message, Map.of()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleOversizedUpload(MaxUploadSizeExceededException ex) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "poster_too_large", "Poster exceeds 5 MiB.");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingRequestPart(MissingServletRequestPartException ex) {
        return ResponseEntity.badRequest().body(new ApiError(
                HttpStatus.BAD_REQUEST.value(),
                "validation_error",
                "A required request part is missing.",
                Map.of(ex.getRequestPartName(), "is required.")));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiError> handleStorageFailure(IOException ex) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "storage_error", "Poster storage is unavailable.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, "invalid_request", "Invalid request.");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex) {
        return error(HttpStatus.CONFLICT, "request_conflict", "The request conflicts with current availability.");
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiError> handleSecurity(SecurityException ex) {
        return error(HttpStatus.FORBIDDEN, "forbidden", "You are not authorized to perform this action.");
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(EntityNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "not_found", "The requested resource was not found.");
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), code, message, Map.of()));
    }
}
