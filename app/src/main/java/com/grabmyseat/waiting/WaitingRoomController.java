package com.grabmyseat.waiting;

import com.grabmyseat.inventory.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/waiting-room/{eventId}")
public class WaitingRoomController {

    private final WaitingRoom room;

    public WaitingRoomController(WaitingRoom room) {
        this.room = room;
    }

    @GetMapping
    public WaitingStatus status(@PathVariable long eventId, HttpServletRequest request) {
        return room.status(UserContext.fromRequest(request).userId(), eventId);
    }

    @PostMapping
    public WaitingStatus join(@PathVariable long eventId, HttpServletRequest request) {
        return room.join(UserContext.fromRequest(request).userId(), eventId);
    }
}
