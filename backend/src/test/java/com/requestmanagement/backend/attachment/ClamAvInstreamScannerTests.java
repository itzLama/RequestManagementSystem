package com.requestmanagement.backend.attachment;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class ClamAvInstreamScannerTests {

    @Test
    void acceptsOnlyAnExactCleanVerdictAfterStreamingAllBytes() throws Exception {
        byte[] content = "safe attachment".getBytes(StandardCharsets.UTF_8);
        try (FakeClamd server = new FakeClamd("stream: OK\0")) {
            MalwareScanResult result = scanner(server.port(), 1024).scan(
                    new ByteArrayInputStream(content), content.length);

            assertThat(result.verdict()).isEqualTo(MalwareScanResult.Verdict.CLEAN);
            assertThat(result.bytesScanned()).isEqualTo(content.length);
            assertThat(server.received()).containsExactly(content);
        }
    }

    @Test
    void returnsInfectedOnlyForAClamAvFoundVerdict() throws Exception {
        byte[] content = "EICAR test bytes".getBytes(StandardCharsets.UTF_8);
        try (FakeClamd server = new FakeClamd("stream: Eicar-Signature FOUND\0")) {
            MalwareScanResult result = scanner(server.port(), 1024).scan(
                    new ByteArrayInputStream(content), content.length);

            assertThat(result.verdict()).isEqualTo(MalwareScanResult.Verdict.INFECTED);
            assertThat(result.details()).isEqualTo("Eicar-Signature");
        }
    }

    @Test
    void malformedOrIncompleteVerdictsFailClosed() throws Exception {
        byte[] content = "data".getBytes(StandardCharsets.UTF_8);
        try (FakeClamd malformed = new FakeClamd("stream: suspicious\0")) {
            assertThat(scanner(malformed.port(), 1024).scan(new ByteArrayInputStream(content), content.length)
                    .verdict()).isEqualTo(MalwareScanResult.Verdict.ERROR);
        }
        try (FakeClamd incomplete = new FakeClamd("stream: OK")) {
            assertThat(scanner(incomplete.port(), 1024).scan(new ByteArrayInputStream(content), content.length)
                    .verdict()).isEqualTo(MalwareScanResult.Verdict.ERROR);
        }
    }

    @Test
    void unavailableTruncatedAndOversizedStreamsFailClosed() throws Exception {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) { unusedPort = socket.getLocalPort(); }
        assertThat(scanner(unusedPort, 1024).scan(new ByteArrayInputStream(new byte[]{1}), 1).verdict())
                .isEqualTo(MalwareScanResult.Verdict.ERROR);

        assertThat(scanner(unusedPort, 1024).scan(new ByteArrayInputStream(new byte[]{1}), 2).verdict())
                .isEqualTo(MalwareScanResult.Verdict.ERROR);
        assertThat(scanner(unusedPort, 25L * 1024 * 1024)
                .scan(new ByteArrayInputStream(new byte[]{1}), 25L * 1024 * 1024 + 1).verdict())
                .isEqualTo(MalwareScanResult.Verdict.ERROR);
    }

    private ClamAvInstreamScanner scanner(int port, long maxBytes) {
        AttachmentScanningProperties properties = new AttachmentScanningProperties();
        properties.setHost("127.0.0.1");
        properties.setPort(port);
        properties.setConnectionTimeout(Duration.ofSeconds(1));
        properties.setScanTimeout(Duration.ofSeconds(1));
        properties.setMaxConcurrentScans(1);
        properties.setMaxStreamBytes(maxBytes);
        return new ClamAvInstreamScanner(properties);
    }

    private static final class FakeClamd implements AutoCloseable {
        private final ServerSocket server;
        private final CompletableFuture<byte[]> received;

        private FakeClamd(String response) throws IOException {
            server = new ServerSocket(0);
            received = CompletableFuture.supplyAsync(() -> serve(response));
        }

        private int port() { return server.getLocalPort(); }

        private byte[] received() { return received.join(); }

        private byte[] serve(String response) {
            try (Socket client = server.accept()) {
                DataInputStream input = new DataInputStream(client.getInputStream());
                byte[] command = input.readNBytes("zINSTREAM\0".length());
                if (!new String(command, StandardCharsets.US_ASCII).equals("zINSTREAM\0")) {
                    throw new IOException("Unexpected command");
                }
                var content = new java.io.ByteArrayOutputStream();
                int length;
                while ((length = input.readInt()) != 0) content.write(input.readNBytes(length));
                client.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
                client.getOutputStream().flush();
                return content.toByteArray();
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }

        @Override
        public void close() throws Exception {
            server.close();
            received.get();
        }
    }
}
