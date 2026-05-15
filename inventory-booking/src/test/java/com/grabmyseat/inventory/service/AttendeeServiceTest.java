package com.grabmyseat.inventory.service;

import com.grabmyseat.inventory.dto.AttendeeRequest;
import com.grabmyseat.inventory.dto.AttendeeResponse;
import com.grabmyseat.inventory.model.Attendee;
import com.grabmyseat.inventory.repository.AttendeeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendeeServiceTest {

    @Mock
    private AttendeeRepository attendeeRepository;

    @InjectMocks
    private AttendeeService attendeeService;

    @Test
    void listsOnlyTheOwningUsersAttendeesInResponseShape() {
        Attendee attendee = new Attendee(7L, "Asha Rao", 28, "+919876543210", "asha@example.test");
        when(attendeeRepository.findByOwnerUserIdOrderByNameAsc(7L)).thenReturn(List.of(attendee));

        List<AttendeeResponse> result = attendeeService.list(7L);

        assertThat(result).containsExactly(
                new AttendeeResponse(null, "Asha Rao", 28, "+919876543210", "asha@example.test"));
    }

    @Test
    void createTrimsFreeTextFieldsAndScopesToTheOwner() {
        when(attendeeRepository.save(any(Attendee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AttendeeResponse response = attendeeService.create(7L,
                new AttendeeRequest("  Asha Rao  ", 28, " +919876543210 ", " asha@example.test "));

        ArgumentCaptor<Attendee> captor = ArgumentCaptor.forClass(Attendee.class);
        verify(attendeeRepository).save(captor.capture());
        assertThat(captor.getValue().getOwnerUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getName()).isEqualTo("Asha Rao");
        assertThat(captor.getValue().getMobile()).isEqualTo("+919876543210");
        assertThat(captor.getValue().getEmail()).isEqualTo("asha@example.test");
        assertThat(response.name()).isEqualTo("Asha Rao");
    }

    @Test
    void updateRejectsAnAttendeeThatDoesNotBelongToTheCaller() {
        when(attendeeRepository.findByIdAndOwnerUserId(9L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attendeeService.update(9L, 7L,
                new AttendeeRequest("Asha Rao", 28, "+919876543210", "asha@example.test")))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void removeDeletesOnlyWhenOwnedByTheCaller() {
        Attendee attendee = new Attendee(7L, "Asha Rao", 28, "+919876543210", "asha@example.test");
        when(attendeeRepository.findByIdAndOwnerUserId(9L, 7L)).thenReturn(Optional.of(attendee));

        attendeeService.remove(9L, 7L);

        verify(attendeeRepository).delete(attendee);
    }

    @Test
    void removeNeverDeletesWhenLookupMisses() {
        when(attendeeRepository.findByIdAndOwnerUserId(9L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attendeeService.remove(9L, 7L)).isInstanceOf(EntityNotFoundException.class);

        verify(attendeeRepository, never()).delete(any());
    }
}
