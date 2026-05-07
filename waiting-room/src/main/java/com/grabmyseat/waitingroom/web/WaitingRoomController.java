package com.grabmyseat.waitingroom.web;

import com.grabmyseat.waitingroom.dto.JoinQueueResponse;
import com.grabmyseat.waitingroom.dto.PermitResponse;
import com.grabmyseat.waitingroom.dto.QueuePositionResponse;
import com.grabmyseat.waitingroom.security.UserContext;
import com.grabmyseat.waitingroom.service.QueueService;
import com.grabmyseat.waitingroom.service.SseEmitterService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/waiting-room/events/{eventId}")
public class WaitingRoomController {

    private final QueueService queueService;
    private final SseEmitterService sseEmitterService;

    public WaitingRoomController(QueueService queueService, SseEmitterService sseEmitterService) {
        this.queueService = queueService;
        this.sseEmitterService = sseEmitterService;
    }

    @PostMapping("/join")
    public ResponseEntity<JoinQueueResponse> join(@PathVariable Long eventId, HttpServletRequest request) {
        UserContext ctx = UserContext.fromRequest(request);
        if (ctx.userId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(queueService.join(ctx.userId(), eventId));
    }

    @GetMapping("/position")
    public ResponseEntity<QueuePositionResponse> position(@PathVariable Long eventId,
                                                          @RequestParam String token,
                                                          HttpServletRequest request) {
        return ResponseEntity.ok(queueService.position(requireUserId(request), eventId, token));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long eventId, @RequestParam String token,
                             HttpServletRequest request) {
        queueService.assertOwnedBy(requireUserId(request), eventId, token);
        return sseEmitterService.subscribe(eventId);
    }

    @PostMapping("/leave")
    public ResponseEntity<Void> leave(@PathVariable Long eventId, @RequestParam String token,
                                      HttpServletRequest request) {
        queueService.leave(requireUserId(request), eventId, token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/permit")
    public ResponseEntity<PermitResponse> permit(@PathVariable Long eventId, @RequestParam String token,
                                                  HttpServletRequest request) {
        String permitToken = queueService.permitValue(requireUserId(request), eventId, token);
        if (permitToken == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(new PermitResponse(permitToken));
    }

    private Long requireUserId(HttpServletRequest request) {
        Long userId = UserContext.fromRequest(request).userId();
        if (userId == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
        }
        return userId;
    }
}
