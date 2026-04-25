package com.grabmyseat.booking;

import com.grabmyseat.inventory.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse book(@Valid @RequestBody BookingRequest body, HttpServletRequest request) {
        return bookingService.book(UserContext.fromRequest(request).userId(), body);
    }

    @GetMapping("/tickets/mine")
    public ResponseEntity<MyTickets> myTickets(@RequestParam(required = false) String versions,
                                               HttpServletRequest request) {
        return bookingService.myTickets(UserContext.fromRequest(request).userId(), versions)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/bookings/{id}")
    public BookingResponse get(@PathVariable long id, HttpServletRequest request) {
        return bookingService.booking(UserContext.fromRequest(request).userId(), id);
    }

    @PostMapping("/bookings/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelBooking(@PathVariable long id, HttpServletRequest request) {
        bookingService.cancelBooking(UserContext.fromRequest(request).userId(), id);
    }

    @PostMapping("/tickets/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelTicket(@PathVariable long id, HttpServletRequest request) {
        bookingService.cancelTicket(UserContext.fromRequest(request).userId(), id);
    }
}
