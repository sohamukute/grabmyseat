package com.grabmyseat.waitingroom.web;

import com.grabmyseat.waitingroom.dto.WaitlistJoinResponse;
import com.grabmyseat.waitingroom.dto.WaitlistStatusResponse;
import com.grabmyseat.waitingroom.security.UserContext;
import com.grabmyseat.waitingroom.service.WaitlistService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/waiting-room")
public class WaitlistController {

    private final WaitlistService waitlistService;

    public WaitlistController(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    @PostMapping("/events/{eventId}/zones/{zoneId}/waitlist")
    public ResponseEntity<WaitlistJoinResponse> join(@PathVariable Long eventId,
                                                     @PathVariable Long zoneId,
                                                     HttpServletRequest request) {
        UserContext ctx = UserContext.fromRequest(request);
        if (ctx.userId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(waitlistService.join(ctx.userId(), eventId, zoneId));
    }

    @GetMapping("/waitlist/{token}/status")
    public ResponseEntity<WaitlistStatusResponse> status(@PathVariable String token) {
        return ResponseEntity.ok(waitlistService.status(token));
    }

    @PostMapping("/waitlist/{token}/accept")
    public ResponseEntity<WaitlistStatusResponse> accept(@PathVariable String token,
                                                         HttpServletRequest request) {
        UserContext ctx = UserContext.fromRequest(request);
        if (ctx.userId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(waitlistService.accept(token));
    }
}
