package com.grabmyseat.waitingroom.web;

import com.grabmyseat.waitingroom.dto.ReleaseNotification;
import com.grabmyseat.waitingroom.service.WaitlistService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/waiting-room/internal")
public class InternalReleaseController {

    private final WaitlistService waitlistService;

    public InternalReleaseController(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    @PostMapping("/events/{eventId}/zones/{zoneId}/release")
    public ResponseEntity<List<Long>> release(@PathVariable Long eventId,
                                              @PathVariable Long zoneId,
                                              @RequestBody ReleaseNotification notification) {
        ReleaseNotification request = new ReleaseNotification(eventId, zoneId,
                notification.count() == null ? 1 : notification.count());
        return ResponseEntity.ok(waitlistService.handleRelease(request));
    }
}
