package com.requestmanagement.backend.attachment;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentFileValidatorTests {
    private final AttachmentFileValidator validator = new AttachmentFileValidator();

    @Test
    void acceptsApprovedPdfOfficeAndImageFormats() throws Exception {
        assertValid(file("report.pdf", "application/pdf", "%PDF-1.7\nbody\n%%EOF".getBytes()), "application/pdf");
        assertValid(file("report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                office("word/document.xml",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertValid(file("sheet.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                office("xl/workbook.xml",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertValid(file("image.png", "image/png", image("png")), "image/png");
        assertValid(file("image.jpeg", "image/jpeg", image("jpeg")), "image/jpeg");
        assertValid(file("image.webp", "image/webp", webp()), "image/webp");
    }

    @Test
    void rejectsEmptyUnsupportedMismatchedAndUnsafeFiles() {
        assertRejected(file("empty.pdf", "application/pdf", new byte[0]));
        assertRejected(file("payload.exe", "application/octet-stream", new byte[]{1, 2, 3}));
        assertRejected(file("report.pdf", "text/html", "%PDF-1.7\n%%EOF".getBytes()));
        assertRejected(file("report.pdf", "application/pdf", "not pdf".getBytes()));
        assertRejected(file("bad\r\nname.pdf", "application/pdf", "%PDF-1.7\n%%EOF".getBytes()));
        assertRejected(file("oversized.pdf", "application/pdf",
                new byte[(int) AttachmentFileValidator.MAX_BYTES + 1]));
    }

    @Test
    void rejectsMalformedOfficeTraversalBombAndInvalidImages() throws Exception {
        assertRejected(file("bad.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "not zip".getBytes()));
        assertRejected(file("traversal.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                zip(new Entry("[Content_Types].xml", contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
                        new Entry("word/document.xml", "<doc/>"), new Entry("../escape", "bad"))));
        assertRejected(file("bomb.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", zipBomb()));
        assertRejected(file("bad.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'}));
        assertRejected(file("mismatch.png", "image/png", image("jpeg")));
        assertRejected(file("bad.webp", "image/webp", "RIFFbad WEBP".getBytes()));
    }

    @Test
    void computesTrustedSizeAndDigestAndStripsClientPaths() throws Exception {
        byte[] pdf = "%PDF-1.7\ntrusted\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        try (ValidatedAttachmentFile validated = validator.validate(
                file("C:\\fakepath\\report.pdf", "application/pdf", pdf))) {
            assertThat(validated.filename()).isEqualTo("report.pdf");
            assertThat(validated.size()).isEqualTo(pdf.length);
            assertThat(validated.sha256()).matches("[0-9a-f]{64}");
            assertThat(validated.detectedMimeType()).isEqualTo("application/pdf");
        }
    }

    private void assertValid(MockMultipartFile file, String mime) throws Exception {
        try (ValidatedAttachmentFile validated = validator.validate(file)) {
            assertThat(validated.detectedMimeType()).isEqualTo(mime);
            assertThat(validated.size()).isPositive();
        }
    }

    private void assertRejected(MockMultipartFile file) {
        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(AttachmentFileValidationException.class);
    }

    private MockMultipartFile file(String name, String mime, byte[] content) {
        return new MockMultipartFile("file", name, mime, content);
    }

    private byte[] image(String format) throws IOException {
        BufferedImage image = new BufferedImage(2, 2,
                "jpeg".equals(format) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }

    private byte[] webp() {
        byte[] bytes = new byte[30];
        putAscii(bytes, 0, "RIFF");
        int riffSize = 22;
        bytes[4] = (byte) riffSize;
        putAscii(bytes, 8, "WEBP");
        putAscii(bytes, 12, "VP8X");
        bytes[16] = 10;
        bytes[24] = 1;
        bytes[27] = 1;
        return bytes;
    }

    private byte[] office(String marker, String mime) throws IOException {
        return zip(new Entry("[Content_Types].xml", contentType(mime)), new Entry(marker, "<root/>"));
    }

    private String contentType(String mime) {
        return "<Types><Override ContentType=\"" + mime + "\"/></Types>";
    }

    private byte[] zipBomb() throws IOException {
        return zip(new Entry("[Content_Types].xml",
                        contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
                new Entry("xl/workbook.xml", "x".repeat(2_000_000)));
    }

    private byte[] zip(Entry... entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Entry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                zip.write(entry.content().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private void putAscii(byte[] bytes, int offset, String value) {
        byte[] ascii = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(ascii, 0, bytes, offset, ascii.length);
    }

    private record Entry(String name, String content) { }
}
