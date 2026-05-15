package com.grabmyseat.inventory.service;

import com.grabmyseat.inventory.client.AuthServiceClient;
import com.grabmyseat.inventory.dto.StaffAssignmentResponse;
import com.grabmyseat.inventory.model.Event;
import com.grabmyseat.inventory.model.StaffAssignment;
import com.grabmyseat.inventory.model.StaffAssignmentStatus;
import com.grabmyseat.inventory.repository.EventRepository;
import com.grabmyseat.inventory.repository.StaffAssignmentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private StaffAssignmentRepository staffAssignmentRepository;

    @Mock
    private AuthServiceClient authServiceClient;

    @InjectMocks
    private StaffService staffService;

    @Test
    void inviteStaff_createsActiveAssignmentAndGrantsRole() {
        Event event = new Event("Concert", "Hall", Instant.now(), Instant.now().plusSeconds(3600), 42L);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(authServiceClient.lookupByUsername("staffuser"))
                .thenReturn(new AuthServiceClient.LookupResult(7L, "staffuser", List.of("ROLE_CUSTOMER")));
        when(staffAssignmentRepository.findByEventIdAndUserId(1L, 7L)).thenReturn(Optional.empty());
        StaffAssignment saved = new StaffAssignment(event, 7L, StaffAssignmentStatus.ACTIVE, 42L);
        saved.setRespondedAt(Instant.now());
        when(staffAssignmentRepository.save(any(StaffAssignment.class))).thenReturn(saved);

        StaffAssignmentResponse response = staffService.inviteStaff(1L, 42L, "staffuser");

        assertEquals(7L, response.userId());
        assertEquals("ACTIVE", response.status());
        verify(authServiceClient).grantRole(7L, "ROLE_STAFF");
    }

    @Test
    void inviteStaff_unknownUsername_throwsIllegalArgument() {
        when(authServiceClient.lookupByUsername("nope")).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> staffService.inviteStaff(1L, 42L, "nope"));
        verify(authServiceClient, never()).grantRole(any(), any());
    }

    @Test
    void inviteStaff_notOrganizer_throwsSecurityException() {
        Event event = new Event("Concert", "Hall", Instant.now(), Instant.now().plusSeconds(3600), 99L);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(authServiceClient.lookupByUsername("staffuser"))
                .thenReturn(new AuthServiceClient.LookupResult(7L, "staffuser", List.of("ROLE_CUSTOMER")));

        assertThrows(SecurityException.class,
                () -> staffService.inviteStaff(1L, 42L, "staffuser"));
    }

    @Test
    void revokeStaff_marksRevoked() {
        Event event = new Event("Concert", "Hall", Instant.now(), Instant.now().plusSeconds(3600), 42L);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        StaffAssignment assignment = new StaffAssignment(event, 7L, StaffAssignmentStatus.ACTIVE, 42L);
        when(staffAssignmentRepository.findByEventIdAndUserId(1L, 7L)).thenReturn(Optional.of(assignment));

        StaffAssignmentResponse response = staffService.revokeStaff(1L, 7L, 42L);

        assertEquals("REVOKED", response.status());
        assertEquals(StaffAssignmentStatus.REVOKED, assignment.getStatus());
    }

    @Test
    void listStaff_notOrganizer_throwsSecurityException() {
        Event event = new Event("Concert", "Hall", Instant.now(), Instant.now().plusSeconds(3600), 99L);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        assertThrows(SecurityException.class,
                () -> staffService.listStaff(1L, 42L));
    }

    @Test
    void listStaff_returnsUsernameFromDisplayLookup() {
        Event event = new Event("Concert", "Hall", Instant.now(), Instant.now().plusSeconds(3600), 42L);
        StaffAssignment assignment = new StaffAssignment(event, 7L, StaffAssignmentStatus.ACTIVE, 42L);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(staffAssignmentRepository.findByEventId(1L)).thenReturn(List.of(assignment));
        when(authServiceClient.lookupDisplayById(7L))
                .thenReturn(new AuthServiceClient.DisplayUser(7L, "staffuser"));

        List<StaffAssignmentResponse> response = staffService.listStaff(1L, 42L);

        assertEquals("staffuser", response.getFirst().username());
        verify(authServiceClient).lookupDisplayById(7L);
    }

    @Test
    void revokeStaff_eventNotFound_throwsEntityNotFoundException() {
        when(eventRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> staffService.revokeStaff(1L, 7L, 42L));
    }
}
