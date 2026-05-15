package com.grabmyseat.inventory.web;

import com.grabmyseat.inventory.dto.InviteStaffRequest;
import com.grabmyseat.inventory.dto.StaffAssignmentResponse;
import com.grabmyseat.inventory.service.StaffService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StaffController.class)
@AutoConfigureMockMvc(addFilters = false)
class StaffControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StaffService staffService;

    @Test
    void inviteAsOrganizer_returnsCreated() throws Exception {
        when(staffService.inviteStaff(eq(1L), eq(42L), eq("staffuser")))
                .thenReturn(new StaffAssignmentResponse(10L, 1L, 7L, "ACTIVE", 42L, Instant.now(), Instant.now()));

        mockMvc.perform(post("/api/inventory/events/1/staff")
                        .header("X-User-Id", "42")
                        .header("X-User-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"staffuser\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void listAsOrganizer_returnsAssignments() throws Exception {
        when(staffService.listStaff(eq(1L), eq(42L)))
                .thenReturn(List.of(
                        new StaffAssignmentResponse(10L, 1L, 7L, "staffuser", "ACTIVE", 42L, Instant.now(), Instant.now())
                ));

        mockMvc.perform(get("/api/inventory/events/1/staff")
                        .header("X-User-Id", "42")
                        .header("X-User-Roles", "ROLE_ORGANIZER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(7))
                .andExpect(jsonPath("$[0].username").value("staffuser"));
    }

    @Test
    void revokeAsOrganizer_returnsOk() throws Exception {
        when(staffService.revokeStaff(eq(1L), eq(7L), eq(42L)))
                .thenReturn(new StaffAssignmentResponse(10L, 1L, 7L, "REVOKED", 42L, Instant.now(), Instant.now()));

        mockMvc.perform(post("/api/inventory/events/1/staff/7/revoke")
                        .header("X-User-Id", "42")
                        .header("X-User-Roles", "ROLE_ORGANIZER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));
    }

    @Test
    void inviteAsNonOrganizer_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/inventory/events/1/staff")
                        .header("X-User-Id", "42")
                        .header("X-User-Roles", "ROLE_CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"staffuser\"}"))
                .andExpect(status().isForbidden());
    }

}
