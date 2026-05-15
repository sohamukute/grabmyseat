package com.grabmyseat.inventory.web;

import com.grabmyseat.inventory.dto.PosterUploadResponse;
import com.grabmyseat.inventory.service.PosterStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PosterController.class)
@AutoConfigureMockMvc(addFilters = false)
class PosterControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PosterStorageService posterStorageService;

    @Test
    void uploadAsOrganizerReturnsStoredPoster() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47});
        when(posterStorageService.store(any())).thenReturn(new PosterUploadResponse(
                "/api/inventory/posters/0f6ca1df-3e7a-472f-99ab-1702be96d9ca.png",
                "image/png",
                4));

        mockMvc.perform(multipart("/api/inventory/posters")
                        .file(file)
                        .header("X-User-Id", "42")
                        .header("X-User-Roles", "ROLE_ORGANIZER"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.url").value(
                        "/api/inventory/posters/0f6ca1df-3e7a-472f-99ab-1702be96d9ca.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.size").value(4));

        verify(posterStorageService).store(any());
    }

    @Test
    void uploadAsCustomerReturnsForbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47});

        mockMvc.perform(multipart("/api/inventory/posters")
                        .file(file)
                        .header("X-User-Id", "42")
                        .header("X-User-Roles", "ROLE_CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("forbidden"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        verifyNoInteractions(posterStorageService);
    }

    @Test
    void invalidPosterReturnsApiErrorShape() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.png", "image/png", new byte[]{0x4d, 0x5a});
        when(posterStorageService.store(any()))
                .thenThrow(new IllegalArgumentException("poster content is not a valid image"));

        mockMvc.perform(multipart("/api/inventory/posters")
                        .file(file)
                        .header("X-User-Id", "42")
                        .header("X-User-Roles", "ROLE_ORGANIZER"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("invalid_request"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        verify(posterStorageService).store(any());
    }

    @Test
    void storageFailureReturnsApiErrorShape() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47});
        when(posterStorageService.store(any())).thenThrow(new IOException("disk unavailable"));

        mockMvc.perform(multipart("/api/inventory/posters")
                        .file(file)
                        .header("X-User-Id", "42")
                        .header("X-User-Roles", "ROLE_ORGANIZER"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("storage_error"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void oversizedMultipartReturnsApiErrorShape() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47});
        when(posterStorageService.store(any()))
                .thenThrow(new MaxUploadSizeExceededException(5L * 1024 * 1024));

        mockMvc.perform(multipart("/api/inventory/posters")
                        .file(file)
                        .header("X-User-Id", "42")
                        .header("X-User-Roles", "ROLE_ORGANIZER"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.code").value("poster_too_large"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void missingFilePartReturnsApiErrorShape() throws Exception {
        mockMvc.perform(multipart("/api/inventory/posters")
                        .header("X-User-Id", "42")
                        .header("X-User-Roles", "ROLE_ORGANIZER"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.fieldErrors.file").isString());
    }

}
