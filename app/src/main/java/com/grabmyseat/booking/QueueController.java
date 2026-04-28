package com.grabmyseat.booking;

import com.grabmyseat.inventory.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final BookingService bookingService;

    public QueueController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JoinResult join(@Valid @RequestBody JoinQueueRequest body, HttpServletRequest request) {
        return bookingService.joinQueue(userId(request), body);
    }

    @GetMapping("/{id}")
    public QueueResponse get(@PathVariable long id, HttpServletRequest request) {
        return bookingService.queue(userId(request), id);
    }

    @PostMapping("/{id}/leave")
    public QueueResponse leave(@PathVariable long id, HttpServletRequest request) {
        return bookingService.leaveQueue(userId(request), id);
    }

    private long userId(HttpServletRequest request) {
        return UserContext.fromRequest(request).userId();
    }
}
