package com.grabmyseat.inventory.service;

import com.grabmyseat.inventory.dto.AttendeeRequest;
import com.grabmyseat.inventory.dto.AttendeeResponse;
import com.grabmyseat.inventory.model.Attendee;
import com.grabmyseat.inventory.repository.AttendeeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AttendeeService {

    private final AttendeeRepository attendees;

    public AttendeeService(AttendeeRepository attendees) {
        this.attendees = attendees;
    }

    @Transactional(readOnly = true)
    public List<AttendeeResponse> list(Long ownerUserId) {
        return attendees.findByOwnerUserIdOrderByNameAsc(ownerUserId).stream()
                .map(AttendeeResponse::from)
                .toList();
    }

    @Transactional
    public AttendeeResponse create(Long ownerUserId, AttendeeRequest request) {
        Attendee saved = attendees.save(new Attendee(ownerUserId, request.name().trim(),
                request.age(), request.mobile().trim(), request.email().trim()));
        return AttendeeResponse.from(saved);
    }

    @Transactional
    public AttendeeResponse update(Long id, Long ownerUserId, AttendeeRequest request) {
        Attendee attendee = attendees.findByIdAndOwnerUserId(id, ownerUserId)
                .orElseThrow(() -> new EntityNotFoundException("attendee not found"));
        attendee.setName(request.name().trim());
        attendee.setAge(request.age());
        attendee.setMobile(request.mobile().trim());
        attendee.setEmail(request.email().trim());
        return AttendeeResponse.from(attendee);
    }

    @Transactional
    public void remove(Long id, Long ownerUserId) {
        Attendee attendee = attendees.findByIdAndOwnerUserId(id, ownerUserId)
                .orElseThrow(() -> new EntityNotFoundException("attendee not found"));
        attendees.delete(attendee);
    }
}
