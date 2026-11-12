package com.requestmanagement.backend.attachment;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
class AttachmentFileValidator {
    static final long MAX_BYTES = 25L * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 20_000_000L;
    private static final int MAX_ZIP_ENTRIES = 1_000;
    private static final long MAX_ZIP_EXPANDED_BYTES = 100L * 1024 * 1024;
    private static final long MAX_ZIP_ENTRY_BYTES = 50L * 1024 * 1024;
    private static final long MAX_ZIP_RATIO = 100;
    private static final int BUFFER_SIZE = 64 * 1024;

    private static final Map<String, String> TYPES = Map.of(
            "pdf", "application/pdf",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "webp", "image/webp");

    ValidatedAttachmentFile validate(MultipartFile multipart) {
        if (multipart == null) throw invalid("File part is required.");
        String filename = safeFilename(multipart.getOriginalFilename());
        String extension = extension(filename);
        String expectedMime = TYPES.get(extension);
        if (expectedMime == null) throw invalid("File type is not allowed.");
        String declared = declaredMime(multipart.getContentType(), expectedMime);
        if (multipart.getSize() > MAX_BYTES) throw invalid("File exceeds the 25 MiB limit.");

        Path temporary = null;
        try {
            temporary = Files.createTempFile("masar-attachment-", ".upload");
            DigestResult digest = copyBounded(multipart, temporary);
            validateStructure(temporary, extension, expectedMime);
            return new ValidatedAttachmentFile(temporary, filename, declared, expectedMime,
                    digest.size(), digest.sha256());
        } catch (AttachmentFileValidationException exception) {
            deleteQuietly(temporary);
            throw exception;
        } catch (IOException exception) {
            deleteQuietly(temporary);
            throw invalid("The uploaded file could not be processed.");
        }
    }

    private DigestResult copyBounded(MultipartFile multipart, Path target) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
        long total = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input = new BufferedInputStream(multipart.getInputStream());
             var output = Files.newOutputStream(target)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES) throw invalid("File exceeds the 25 MiB limit.");
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        if (total == 0) throw invalid("Empty files are not allowed.");
        return new DigestResult(total, HexFormat.of().formatHex(digest.digest()));
    }

    private void validateStructure(Path path, String extension, String mime) throws IOException {
        switch (extension) {
            case "pdf" -> validatePdf(path);
            case "docx" -> validateOffice(path, "word/document.xml", mime);
            case "xlsx" -> validateOffice(path, "xl/workbook.xml", mime);
            case "png", "jpg", "jpeg" -> validateImage(path, extension);
            case "webp" -> validateWebp(path);
            default -> throw invalid("File type is not allowed.");
        }
    }

    private void validatePdf(Path path) throws IOException {
        byte[] prefix = new byte[8];
        try (InputStream input = Files.newInputStream(path)) {
            if (input.read(prefix) < 5 || !new String(prefix, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
                throw invalid("PDF signature is invalid.");
            }
        }
        long size = Files.size(path);
        int tailSize = (int) Math.min(4096, size);
        byte[] tail = new byte[tailSize];
        try (var channel = Files.newByteChannel(path)) {
            channel.position(size - tailSize);
            java.nio.ByteBuffer bytes = java.nio.ByteBuffer.wrap(tail);
            while (bytes.hasRemaining() && channel.read(bytes) != -1) { }
        }
        if (!new String(tail, StandardCharsets.ISO_8859_1).contains("%%EOF")) {
            throw invalid("PDF structure is incomplete.");
        }
    }

    private void validateOffice(Path path, String requiredPart, String expectedMime) throws IOException {
        int entries = 0;
        long expandedTotal = 0;
        boolean hasContentTypes = false;
        boolean hasRequiredPart = false;
        try (ZipFile zip = new ZipFile(path.toFile())) {
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                entries++;
                if (entries > MAX_ZIP_ENTRIES) throw invalid("Office document contains too many entries.");
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../") || name.equals("..")
                        || name.matches("^[A-Za-z]:.*")) {
                    throw invalid("Office document contains an unsafe archive path.");
                }
                if (name.toLowerCase(Locale.ROOT).endsWith("vbaproject.bin")) {
                    throw invalid("Macro-enabled Office content is not allowed.");
                }
                long size = entry.getSize();
                long compressed = entry.getCompressedSize();
                if (size < 0 || size > MAX_ZIP_ENTRY_BYTES) {
                    throw invalid("Office document entry size is invalid.");
                }
                expandedTotal += size;
                if (expandedTotal > MAX_ZIP_EXPANDED_BYTES) {
                    throw invalid("Office document expands beyond the safe limit.");
                }
                if (compressed > 0 && size / compressed > MAX_ZIP_RATIO) {
                    throw invalid("Office document compression ratio is unsafe.");
                }
                hasContentTypes |= "[Content_Types].xml".equals(name);
                hasRequiredPart |= requiredPart.equals(name);
            }
            if (!hasContentTypes || !hasRequiredPart) throw invalid("Office document structure is invalid.");
            ZipEntry contentTypes = zip.getEntry("[Content_Types].xml");
            if (contentTypes.getSize() > 1024 * 1024) throw invalid("Office content types are too large.");
            try (InputStream input = zip.getInputStream(contentTypes)) {
                String xml = new String(input.readNBytes(1024 * 1024), StandardCharsets.UTF_8);
                if (!xml.contains(expectedMime)) throw invalid("Office document type does not match its extension.");
            }
        } catch (java.util.zip.ZipException exception) {
            throw invalid("Office document ZIP structure is invalid.");
        }
    }

    private void validateImage(Path path, String extension) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) throw invalid("Image structure is invalid.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("Image structure is invalid.");
            ImageReader reader = readers.next();
            try {
                String actualFormat = reader.getFormatName().toLowerCase(Locale.ROOT);
                boolean expectedFormat = "png".equals(extension) ? actualFormat.contains("png")
                        : actualFormat.contains("jpeg") || actualFormat.contains("jpg");
                if (!expectedFormat) throw invalid("Image content does not match its extension.");
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);
                if (reader.read(0) == null) throw invalid("Image could not be decoded.");
            } finally {
                reader.dispose();
            }
        } catch (RuntimeException exception) {
            if (exception instanceof AttachmentFileValidationException validation) throw validation;
            throw invalid("Image structure is invalid.");
        }
    }

    private void validateWebp(Path path) throws IOException {
        byte[] header;
        try (InputStream input = Files.newInputStream(path)) {
            header = input.readNBytes(30);
        }
        if (header.length < 30 || !ascii(header, 0, "RIFF") || !ascii(header, 8, "WEBP")) {
            throw invalid("WebP signature is invalid.");
        }
        long declaredSize = Integer.toUnsignedLong(littleEndianInt(header, 4)) + 8;
        if (declaredSize != Files.size(path)) throw invalid("WebP container size is invalid.");
        int width;
        int height;
        if (ascii(header, 12, "VP8X") && header.length >= 30) {
            width = 1 + unsigned24(header, 24);
            height = 1 + unsigned24(header, 27);
        } else if (ascii(header, 12, "VP8L") && header.length >= 25 && (header[20] & 0xff) == 0x2f) {
            int bits = littleEndianInt(header, 21);
            width = 1 + (bits & 0x3fff);
            height = 1 + ((bits >>> 14) & 0x3fff);
        } else if (ascii(header, 12, "VP8 ") && header.length >= 30
                && (header[23] & 0xff) == 0x9d && (header[24] & 0xff) == 0x01
                && (header[25] & 0xff) == 0x2a) {
            width = ((header[27] & 0x3f) << 8) | (header[26] & 0xff);
            height = ((header[29] & 0x3f) << 8) | (header[28] & 0xff);
        } else {
            throw invalid("WebP image structure is unsupported or malformed.");
        }
        validateDimensions(width, height);
    }

    private void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
            throw invalid("Image dimensions exceed the safe limit.");
        }
    }

    private String safeFilename(String original) {
        if (original == null) throw invalid("A filename is required.");
        String leaf = original.replace('\\', '/');
        leaf = leaf.substring(leaf.lastIndexOf('/') + 1);
        leaf = Normalizer.normalize(leaf, Normalizer.Form.NFC).trim();
        if (leaf.isEmpty() || leaf.length() > 255 || leaf.contains("..")
                || leaf.chars().anyMatch(Character::isISOControl)) {
            throw invalid("Filename is unsafe.");
        }
        return leaf;
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) throw invalid("File extension is required.");
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String declaredMime(String declared, String expected) {
        if (declared == null || declared.isBlank() || "application/octet-stream".equalsIgnoreCase(declared)) {
            return null;
        }
        String normalized = declared.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!expected.equals(normalized)) throw invalid("Declared file type does not match the filename.");
        return normalized;
    }

    private static boolean ascii(byte[] bytes, int offset, String value) {
        if (offset + value.length() > bytes.length) return false;
        for (int index = 0; index < value.length(); index++) {
            if ((byte) value.charAt(index) != bytes[offset + index]) return false;
        }
        return true;
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16) | ((bytes[offset + 3] & 0xff) << 24);
    }

    private static int unsigned24(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16);
    }

    private static AttachmentFileValidationException invalid(String message) {
        return new AttachmentFileValidationException(message);
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The operating system will still clean its temporary directory; no user data is exposed.
        }
    }

    private record DigestResult(long size, String sha256) { }
}
