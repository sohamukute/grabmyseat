package com.grabmyseat.inventory.service;

import com.grabmyseat.inventory.dto.PosterUploadResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PosterStorageService {

    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final int MAX_WIDTH = 8192;
    private static final int MAX_HEIGHT = 8192;
    private static final long MAX_PIXELS = 20_000_000L;
    private static final int MAX_FRAMES = 100;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp");
    private static final Pattern SAFE_FILENAME = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$");

    private final Path uploadDir;

    public PosterStorageService(@Value("${poster.upload-dir:${POSTER_UPLOAD_DIR:uploads}}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public PosterUploadResponse store(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("poster file is empty");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("poster exceeds 5 MiB");
        }

        String contentType = file.getContentType();
        String extension = contentType == null ? null : EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new IllegalArgumentException("poster must be JPEG, PNG, or WebP");
        }

        refuseSymlinkedUploadPath();
        Files.createDirectories(uploadDir);
        refuseSymlinkedUploadPath();
        String filename = UUID.randomUUID() + "." + extension;
        Path target = uploadDir.resolve(filename);
        long size = 0;
        try {
            try (InputStream input = file.getInputStream();
                 OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (size + read > MAX_SIZE) {
                        throw new IllegalArgumentException("poster exceeds 5 MiB");
                    }
                    output.write(buffer, 0, read);
                    size += read;
                }
            }
            if (!isValidImage(target, contentType)) {
                throw new IllegalArgumentException("poster content is not a valid image");
            }
        } catch (IOException | RuntimeException ex) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanupError) {
                ex.addSuppressed(cleanupError);
            }
            throw ex;
        }

        return new PosterUploadResponse(
                "/api/inventory/posters/" + filename,
                contentType,
                size);
    }

    public Resource load(String filename) throws IOException {
        if (!SAFE_FILENAME.matcher(filename).matches()) {
            throw new IllegalArgumentException("invalid poster filename");
        }
        refuseSymlinkedUploadPath();
        Path path = uploadDir.resolve(filename);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new EntityNotFoundException("poster not found");
        }
        SeekableByteChannel channel = Files.newByteChannel(
                path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        long size = channel.size();
        return new InputStreamResource(Channels.newInputStream(channel)) {
            @Override
            public long contentLength() {
                return size;
            }

            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private void refuseSymlinkedUploadPath() {
        for (Path path = uploadDir; path != null; path = path.getParent()) {
            if (Files.isSymbolicLink(path)) {
                throw new IllegalArgumentException("poster upload directory must not contain symbolic links");
            }
        }
    }

    private boolean isValidImage(Path path, String expectedContentType) {
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null || !hasValidEnvelope(input, expectedContentType)) {
                return false;
            }
            input.seek(0);

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return false;
            }

            ImageReader reader = readers.next();
            boolean[] warned = {false};
            try {
                reader.addIIOReadWarningListener((source, warning) -> warned[0] = true);
                reader.setInput(input, false, true);
                if (!expectedContentType.equals(canonicalContentType(reader.getFormatName()))) {
                    return false;
                }

                int imageCount = reader.getNumImages(true);
                if (imageCount <= 0 || imageCount > MAX_FRAMES) {
                    throw new IllegalArgumentException("poster animation exceeds limits");
                }

                long totalPixels = 0;
                for (int imageIndex = 0; imageIndex < imageCount; imageIndex++) {
                    int width = reader.getWidth(imageIndex);
                    int height = reader.getHeight(imageIndex);
                    totalPixels += (long) width * height;
                    if (width <= 0 || height <= 0
                            || width > MAX_WIDTH || height > MAX_HEIGHT
                            || totalPixels > MAX_PIXELS) {
                        throw new IllegalArgumentException("poster dimensions exceed limits");
                    }
                }

                try {
                    for (int imageIndex = 0; imageIndex < imageCount; imageIndex++) {
                        if (reader.read(imageIndex) == null) {
                            return false;
                        }
                    }
                    return !warned[0];
                } catch (RuntimeException ex) {
                    return false;
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            return false;
        }
    }

    private boolean hasValidEnvelope(ImageInputStream input, String contentType) throws IOException {
        long length = input.length();
        if (length < 0) {
            return false;
        }
        return switch (contentType) {
            case "image/jpeg" -> hasValidJpegEnvelope(input, length);
            case "image/png" -> hasValidPngEnvelope(input, length);
            case "image/webp" -> hasValidWebpEnvelope(input, length);
            default -> false;
        };
    }

    private boolean hasValidJpegEnvelope(ImageInputStream input, long length) throws IOException {
        if (length < 4) {
            return false;
        }
        input.seek(0);
        if (input.readUnsignedShort() != 0xffd8) {
            return false;
        }
        input.seek(length - 2);
        return input.readUnsignedShort() == 0xffd9;
    }

    private boolean hasValidPngEnvelope(ImageInputStream input, long length) throws IOException {
        if (length < 20) {
            return false;
        }
        input.seek(0);
        if (input.readLong() != 0x89504e470d0a1a0aL) {
            return false;
        }

        boolean firstChunk = true;
        while (input.getStreamPosition() + 12 <= length) {
            long chunkLength = Integer.toUnsignedLong(input.readInt());
            int chunkType = input.readInt();
            long chunkEnd = input.getStreamPosition() + chunkLength + Integer.BYTES;
            if (chunkEnd > length || firstChunk && chunkType != 0x49484452) {
                return false;
            }
            firstChunk = false;
            input.seek(chunkEnd);
            if (chunkType == 0x49454e44) {
                return chunkLength == 0 && input.getStreamPosition() == length;
            }
        }
        return false;
    }

    private boolean hasValidWebpEnvelope(ImageInputStream input, long length) throws IOException {
        if (length < 12) {
            return false;
        }
        input.seek(0);
        if (input.readInt() != 0x52494646) {
            return false;
        }
        long declaredLength = Integer.toUnsignedLong(Integer.reverseBytes(input.readInt())) + 8;
        return input.readInt() == 0x57454250 && declaredLength == length;
    }

    private String canonicalContentType(String formatName) {
        return switch (formatName.toLowerCase(Locale.ROOT)) {
            case "jpeg", "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> null;
        };
    }

    public String contentType(String filename) {
        if (!SAFE_FILENAME.matcher(filename).matches()) {
            throw new IllegalArgumentException("invalid poster filename");
        }
        return CONTENT_TYPES.get(filename.substring(filename.lastIndexOf('.') + 1));
    }
}
