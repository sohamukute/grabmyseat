package com.grabmyseat.inventory.web;

import com.grabmyseat.inventory.dto.PosterUploadResponse;
import com.grabmyseat.inventory.security.UserContext;
import com.grabmyseat.inventory.service.PosterStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
@RequestMapping("/api/inventory/posters")
public class PosterController {

    private final PosterStorageService posterStorageService;

    public PosterController(PosterStorageService posterStorageService) {
        this.posterStorageService = posterStorageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PosterUploadResponse upload(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file) throws IOException {
        UserContext user = UserContext.fromRequest(request);
        if (user.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing user identity");
        }
        if (!user.isOrganizer()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "organizer role required");
        }
        return posterStorageService.store(file);
    }

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> download(@PathVariable String filename) throws IOException {
        Resource resource = posterStorageService.load(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(posterStorageService.contentType(filename)))
                .contentLength(resource.contentLength())
                .body(resource);
    }
}
