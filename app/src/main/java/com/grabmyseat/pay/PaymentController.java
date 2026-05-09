package com.grabmyseat.pay;

import com.grabmyseat.booking.BookingResponse;
import com.grabmyseat.inventory.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final Payments payments;
    private final Razorpay razorpay;

    public PaymentController(Payments payments, Razorpay razorpay) {
        this.payments = payments;
        this.razorpay = razorpay;
    }

    @PostMapping("/api/bookings/{id}/order")
    public PayOrder order(@PathVariable long id, HttpServletRequest request) {
        return payments.order(UserContext.fromRequest(request).userId(), id);
    }

    @PostMapping("/api/bookings/{id}/confirm")
    public BookingResponse confirm(@PathVariable long id, @Valid @RequestBody ConfirmRequest body, HttpServletRequest request) {
        return payments.confirm(UserContext.fromRequest(request).userId(), id, body);
    }

    @PostMapping("/api/payments/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String body,
                                        @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
                                        @RequestHeader(value = "x-razorpay-event-id", required = false) String eventId) {
        if (!razorpay.webhooksOn()) {
            return ResponseEntity.notFound().build();
        }
        payments.webhook(body, signature, eventId == null ? "" : eventId);
        return ResponseEntity.ok().build();
    }
}
