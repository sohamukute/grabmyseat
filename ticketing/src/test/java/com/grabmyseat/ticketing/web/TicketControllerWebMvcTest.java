package com.grabmyseat.ticketing.web;

import com.grabmyseat.ticketing.client.StaffAssignmentClient;
import com.grabmyseat.ticketing.model.Ticket;
import com.grabmyseat.ticketing.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketController.class)
@AutoConfigureMockMvc(addFilters = false)
class TicketControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TicketService ticketService;

    @MockBean
    private StaffAssignmentClient staffAssignmentClient;

    @Test
    void validate_asStaffWithActiveAssignment_allows() throws Exception {
        Ticket ticket = new Ticket("res-1", 1L, 2L, 100L, List.of(10L), "payload");
        when(ticketService.findByToken("token-1")).thenReturn(Optional.of(ticket));
        when(staffAssignmentClient.isAuthorizedScanner(1L, 42L)).thenReturn(true);
        when(ticketService.verify("token-1")).thenReturn(ticket);

        mockMvc.perform(post("/api/ticketing/tickets/token-1/validate")
                        .header("X-User-Id", "42")
                        .header("X-User-Roles", "ROLE_STAFF"))
                .andExpect(status().isOk());
        verify(ticketService).verify("token-1");
    }

    @Test
    void validate_asStaffWithoutActiveAssignment_forbidden() throws Exception {
        Ticket ticket = new Ticket("res-1", 1L, 2L, 100L, List.of(10L), "payload");
        when(ticketService.findByToken("token-1")).thenReturn(Optional.of(ticket));
        when(staffAssignmentClient.isAuthorizedScanner(1L, 42L)).thenReturn(false);

        mockMvc.perform(post("/api/ticketing/tickets/token-1/validate")
                        .header("X-User-Id", "42")
                        .header("X-User-Roles", "ROLE_STAFF"))
                .andExpect(status().isForbidden());

        verify(ticketService, never()).verify(any());
    }

    @Test
    void validate_asCustomer_forbidden() throws Exception {
        mockMvc.perform(post("/api/ticketing/tickets/token-1/validate")
                        .header("X-User-Id", "42")
                        .header("X-User-Roles", "ROLE_CUSTOMER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void validate_organizerIsAuthorizedRegardlessOfStaffAssignment() throws Exception {
        Ticket ticket = new Ticket("res-1", 1L, 2L, 100L, List.of(10L), "payload");
        when(ticketService.findByToken("token-1")).thenReturn(Optional.of(ticket));
        when(staffAssignmentClient.isAuthorizedScanner(1L, 42L)).thenReturn(true);
        when(ticketService.verify("token-1")).thenReturn(ticket);

        mockMvc.perform(post("/api/ticketing/tickets/token-1/validate")
                        .header("X-User-Id", "42")
                        .header("X-User-Roles", "ROLE_ORGANIZER"))
                .andExpect(status().isOk());
        verify(ticketService).verify("token-1");
    }

    @Test
    void validate_organizerNotRecognized_forbidden() throws Exception {
        Ticket ticket = new Ticket("res-1", 1L, 2L, 100L, List.of(10L), "payload");
        when(ticketService.findByToken("token-1")).thenReturn(Optional.of(ticket));
        when(staffAssignmentClient.isAuthorizedScanner(1L, 42L)).thenReturn(false);

        mockMvc.perform(post("/api/ticketing/tickets/token-1/validate")
                        .header("X-User-Id", "42")
                        .header("X-User-Roles", "ROLE_ORGANIZER"))
                .andExpect(status().isForbidden());
    }
}
