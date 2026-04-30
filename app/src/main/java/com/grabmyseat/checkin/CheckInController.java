package com.grabmyseat.checkin;

import com.grabmyseat.inventory.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CheckInController {

    private final CheckInService checkInService;

    public CheckInController(CheckInService checkInService) {
        this.checkInService = checkInService;
    }

    @GetMapping("/api/checkin/events")
    public List<CheckInService.StaffEvent> events(HttpServletRequest request) {
        return checkInService.myEvents(UserContext.fromRequest(request).userId());
    }

    @PostMapping("/api/checkin")
    public CheckInResult checkIn(@Valid @RequestBody CheckInRequest body, HttpServletRequest request) {
        return checkInService.checkIn(UserContext.fromRequest(request).userId(), body);
    }
}
