package com.grabmyseat.inventory.service;

import com.grabmyseat.inventory.dto.PosterUploadResponse;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HexFormat;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PosterStorageServiceTest {

    private static final int MAX_POSTER_SIZE = 5 * 1024 * 1024;
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    private static final byte[] WEBP = Base64.getDecoder().decode(
            "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
    private static final byte[] ANIMATED_WEBP = Base64.getDecoder().decode(
            "UklGRoQAAABXRUJQVlA4WAoAAAACAAAAAAAAAAAAQU5JTQYAAAAAAAAAAABBTk1GKAAAAAAAAAAAAAAAAAAAAGQAAAJWUDhMDwAAAC8AAAAABxD9j/4HIqL/AQBBTk1GKAAAAAAAAAAAAAAAAAAAAGQAAABWUDhMDwAAAC8AAAAABxDR//4HIqL/AQA=");
    private static final byte[] FORGED_VP8_WEBP = HexFormat.of().parseHex(
            "524946461800000057454250565038200b0000003000009d012a010001000000");

    @TempDir
    Path uploadDir;

    @Test
    void storesPngWithGeneratedFilename() throws Exception {
        PosterStorageService service = new PosterStorageService(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.png", "image/png", PNG);

        PosterUploadResponse response = service.store(file);

        assertEquals("image/png", response.contentType());
        assertEquals(PNG.length, response.size());
        assertTrue(response.url().matches(
                "/api/inventory/posters/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png"));
        String filename = response.url().substring(response.url().lastIndexOf('/') + 1);
        assertTrue(Files.exists(uploadDir.resolve(filename)));
        assertArrayEquals(file.getBytes(), service.load(filename).getContentAsByteArray());
    }

    @Test
    void storesImageAtFiveMebibyteLimit() throws Exception {
        PosterStorageService service = new PosterStorageService(uploadDir.toString());
        byte[] atLimit = pngAtSize(MAX_POSTER_SIZE);
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.png", "image/png", atLimit);

        PosterUploadResponse response = service.store(file);

        assertEquals(MAX_POSTER_SIZE, response.size());
    }

    @Test
    void rejectsExecutableContentType() {
        PosterStorageService service = new PosterStorageService(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.exe", "application/x-msdownload", new byte[]{0x4d, 0x5a});

        assertThrows(IllegalArgumentException.class, () -> service.store(file));
    }

    @Test
    void rejectsExecutableBytesDisguisedAsPng() {
        PosterStorageService service = new PosterStorageService(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.png", "image/png", new byte[]{0x4d, 0x5a});

        assertThrows(IllegalArgumentException.class, () -> service.store(file));
    }

    @Test
    void rejectsPngBytesLabeledAsWebp() {
        PosterStorageService service = new PosterStorageService(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.webp", "image/webp", PNG);

        assertThrows(IllegalArgumentException.class, () -> service.store(file));
    }

    @Test
    void rejectsTruncatedPngSignature() {
        PosterStorageService service = new PosterStorageService(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a});

        assertThrows(IllegalArgumentException.class, () -> service.store(file));
    }

    @Test
    void rejectsTruncatedJpegSignature() {
        PosterStorageService service = new PosterStorageService(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});

        assertThrows(IllegalArgumentException.class, () -> service.store(file));
    }

    @Test
    void rejectsDecodableJpegWithoutEndMarker() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "jpeg", output);
        byte[] truncated = java.util.Arrays.copyOf(output.toByteArray(), output.size() - 2);
        PosterStorageService service = new PosterStorageService(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.jpg", "image/jpeg", truncated);

        assertThrows(IllegalArgumentException.class, () -> service.store(file));
    }

    @Test
    void rejectsBytesAfterPngEnd() {
        PosterStorageService service = new PosterStorageService(uploadDir.toString());
        byte[] withTrailingBytes = java.util.Arrays.copyOf(PNG, PNG.length + 1);
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.png", "image/png", withTrailingBytes);

        assertThrows(IllegalArgumentException.class, () -> service.store(file));
    }

    @Test
    void rejectsForgedWebpSignature() {
        PosterStorageService service = new PosterStorageService(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.webp", "image/webp",
                new byte[]{'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'E', 'B', 'P'});

        assertThrows(IllegalArgumentException.class, () -> service.store(file));
    }

    @Test
    void rejectsForgedWebpFrameHeaderWithoutFrameData() {
        PosterStorageService service = new PosterStorageService(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.webp", "image/webp", FORGED_VP8_WEBP);

        assertThrows(IllegalArgumentException.class, () -> service.store(file));
    }

    @Test
    void rejectsTruncatedWebp() {
        PosterStorageService service = new PosterStorageService(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.webp", "image/webp",
                java.util.Arrays.copyOf(WEBP, WEBP.length - 8));

        assertThrows(IllegalArgumentException.class, () -> service.store(file));
    }

    @Test
    void storesStructurallyValidWebp() throws Exception {
        PosterStorageService service = new PosterStorageService(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.webp", "image/webp", WEBP);

        PosterUploadResponse response = service.store(file);

        assertEquals("image/webp", response.contentType());
        assertEquals(WEBP.length, response.size());
        assertTrue(response.url().endsWith(".webp"));
    }

    @Test
    void storesStructurallyValidAnimatedWebp() throws Exception {
        PosterStorageService service = new PosterStorageService(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.webp", "image/webp", ANIMATED_WEBP);

        PosterUploadResponse response = service.store(file);

        assertEquals("image/webp", response.contentType());
        assertEquals(ANIMATED_WEBP.length, response.size());
    }

    @Test
    void rejectsAnimatedWebpWithTruncatedLaterFrame() {
        PosterStorageService service = new PosterStorageService(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.webp", "image/webp",
                java.util.Arrays.copyOf(ANIMATED_WEBP, ANIMATED_WEBP.length - 8));

        assertThrows(IllegalArgumentException.class, () -> service.store(file));
    }

    @Test
    void rejectsExcessiveWidthBeforeDecodingPixels() {
        assertDimensionLimit(8193, 1);
    }

    @Test
    void rejectsExcessiveHeightBeforeDecodingPixels() {
        assertDimensionLimit(1, 8193);
    }

    @Test
    void rejectsExcessivePixelCountBeforeDecodingPixels() {
        assertDimensionLimit(4473, 4473);
    }

    @Test
    void rejectsMoreThanHundredAnimatedWebpFrames() {
        PosterStorageService service = new PosterStorageService(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.webp", "image/webp", animatedWebpWithFrames(101));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> service.store(file));

        assertEquals("poster animation exceeds limits", exception.getMessage());
    }

    @Test
    void rejectsFileOverFiveMebibytes() {
        PosterStorageService service = new PosterStorageService(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.png", "image/png", new byte[MAX_POSTER_SIZE + 1]);

        assertThrows(IllegalArgumentException.class, () -> service.store(file));
    }

    @Test
    void removesRejectedUploadFromStorage() throws Exception {
        PosterStorageService service = new PosterStorageService(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.png", "image/png", new byte[]{0x4d, 0x5a});

        assertThrows(IllegalArgumentException.class, () -> service.store(file));

        try (var files = Files.list(uploadDir)) {
            assertFalse(files.findAny().isPresent());
        }
    }

    @Test
    void refusesSymlinkInUploadDirectoryPath() throws Exception {
        Path realRoot = uploadDir.resolve("real");
        Files.createDirectories(realRoot.resolve("posters"));
        Path linkedRoot = uploadDir.resolve("linked");
        Files.createSymbolicLink(linkedRoot, realRoot);
        PosterStorageService service = new PosterStorageService(linkedRoot.resolve("posters").toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.png", "image/png", PNG);

        assertThrows(IllegalArgumentException.class, () -> service.store(file));

        try (var files = Files.list(realRoot.resolve("posters"))) {
            assertFalse(files.findAny().isPresent());
        }
    }

    @Test
    void refusesGeneratedFilenameThatIsASymlink() throws Exception {
        PosterStorageService service = new PosterStorageService(uploadDir.toString());
        Path source = uploadDir.resolve("source.png");
        Files.write(source, PNG);
        String filename = "0f6ca1df-3e7a-472f-99ab-1702be96d9ca.png";
        Files.createSymbolicLink(uploadDir.resolve(filename), source);

        assertThrows(EntityNotFoundException.class, () -> service.load(filename));
    }

    @Test
    void refusesNonGeneratedFilename() {
        PosterStorageService service = new PosterStorageService(uploadDir.toString());

        assertThrows(IllegalArgumentException.class, () -> service.load("../application.yml"));
        assertThrows(IllegalArgumentException.class, () -> service.load("poster.png"));
    }

    @Test
    void reportsMissingGeneratedPosterAsNotFound() {
        PosterStorageService service = new PosterStorageService(uploadDir.toString());

        assertThrows(EntityNotFoundException.class, () ->
                service.load("0f6ca1df-3e7a-472f-99ab-1702be96d9ca.png"));
    }

    private byte[] animatedWebpWithFrames(int frameCount) {
        byte[] header = java.util.Arrays.copyOfRange(ANIMATED_WEBP, 0, 44);
        byte[] frame = java.util.Arrays.copyOfRange(ANIMATED_WEBP, 44, 92);
        ByteArrayOutputStream output = new ByteArrayOutputStream(header.length + frame.length * frameCount);
        output.writeBytes(header);
        for (int index = 0; index < frameCount; index++) {
            output.writeBytes(frame);
        }
        byte[] result = output.toByteArray();
        ByteBuffer.wrap(result, 4, Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(result.length - 8);
        return result;
    }

    private byte[] pngAtSize(int size) {
        int chunkDataSize = size - PNG.length - 12;
        ByteBuffer bytes = ByteBuffer.allocate(size);
        bytes.put(PNG, 0, PNG.length - 12);
        bytes.putInt(chunkDataSize);
        int typeOffset = bytes.position();
        bytes.putInt(0x70614464);
        bytes.position(bytes.position() + chunkDataSize);
        CRC32 crc = new CRC32();
        crc.update(bytes.array(), typeOffset, Integer.BYTES + chunkDataSize);
        bytes.putInt((int) crc.getValue());
        bytes.put(PNG, PNG.length - 12, 12);
        return bytes.array();
    }

    private void assertDimensionLimit(int width, int height) {
        PosterStorageService service = new PosterStorageService(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "poster.png", "image/png", pngHeader(width, height));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> service.store(file));

        assertEquals("poster dimensions exceed limits", exception.getMessage());
    }

    private byte[] pngHeader(int width, int height) {
        ByteBuffer bytes = ByteBuffer.allocate(45);
        bytes.putLong(0x89504e470d0a1a0aL);
        bytes.putInt(13);
        bytes.putInt(0x49484452);
        bytes.putInt(width);
        bytes.putInt(height);
        bytes.put(new byte[]{8, 2, 0, 0, 0});
        CRC32 crc = new CRC32();
        crc.update(bytes.array(), 12, 17);
        bytes.putInt((int) crc.getValue());
        bytes.putInt(0);
        bytes.putInt(0x49454e44);
        bytes.putInt(0xae426082);
        return bytes.array();
    }
}
