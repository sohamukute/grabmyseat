package com.grabmyseat.inventory.web;

import org.junit.jupiter.api.Test;
import com.grabmyseat.inventory.service.EventValidationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryExceptionHandlerTest {

    private final InventoryExceptionHandler handler = new InventoryExceptionHandler();

    @Test
    void validation_error_contains_field_and_message() {
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(new Object(), "createEventRequest");
        errors.addError(new FieldError("createEventRequest", "artworkUrl", "must be an http or https image URL"));
        when(exception.getBindingResult()).thenReturn(errors);

        var response = handler.validation(exception);

        assertThat(response.getBody().fieldErrors())
                .containsEntry("artworkUrl", "must be an http or https image URL");
    }

    @Test
    void event_validation_error_preserves_schedule_field_errors() {
        var response = handler.eventValidation(new EventValidationException(
                java.util.Map.of("queueOpensAt", "Queue open time is required for flash sales.")));

        assertThat(response.getBody().fieldErrors())
                .containsEntry("queueOpensAt", "Queue open time is required for flash sales.");
    }
}
