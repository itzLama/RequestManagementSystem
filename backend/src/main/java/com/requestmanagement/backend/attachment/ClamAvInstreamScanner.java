package com.requestmanagement.backend.attachment;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;

final class ClamAvInstreamScanner implements AttachmentMalwareScanner {
    private static final byte[] INSTREAM = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);
    private static final int CHUNK_SIZE = 64 * 1024;
    private static final int MAX_RESPONSE = 4096;
    private final AttachmentScanningProperties properties;
    private final Semaphore permits;

    ClamAvInstreamScanner(AttachmentScanningProperties properties) {
        this.properties = properties;
        this.permits = new Semaphore(properties.getMaxConcurrentScans(), true);
    }

    @Override
    public MalwareScanResult scan(InputStream content, long expectedLength) {
        if (expectedLength < 0 || expectedLength > properties.getMaxStreamBytes()) {
            return MalwareScanResult.error("Attachment exceeds the scanner stream limit.", 0);
        }
        if (!permits.tryAcquire()) return MalwareScanResult.error("Scanner capacity is currently unavailable.", 0);
        long sent = 0;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(properties.getHost(), properties.getPort()),
                    Math.toIntExact(properties.getConnectionTimeout().toMillis()));
            socket.setSoTimeout(Math.toIntExact(properties.getScanTimeout().toMillis()));
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.write(INSTREAM);
            byte[] buffer = new byte[CHUNK_SIZE];
            int read;
            while ((read = content.read(buffer)) != -1) {
                sent += read;
                if (sent > properties.getMaxStreamBytes() || sent > expectedLength) {
                    return MalwareScanResult.error("Attachment stream length is invalid.", sent);
                }
                output.writeInt(read);
                output.write(buffer, 0, read);
            }
            if (sent != expectedLength) return MalwareScanResult.error("Attachment stream was truncated.", sent);
            output.writeInt(0);
            output.flush();
            return parseResponse(socket.getInputStream(), sent);
        } catch (IOException | RuntimeException exception) {
            return MalwareScanResult.error("Malware scanner is unavailable.", sent);
        } finally {
            permits.release();
        }
    }

    @Override
    public boolean ready() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(properties.getHost(), properties.getPort()),
                    Math.toIntExact(properties.getConnectionTimeout().toMillis()));
            socket.setSoTimeout(Math.toIntExact(properties.getConnectionTimeout().toMillis()));
            socket.getOutputStream().write("zPING\0".getBytes(StandardCharsets.US_ASCII));
            return "PONG".equals(readRecord(socket.getInputStream()));
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private MalwareScanResult parseResponse(InputStream input, long sent) throws IOException {
        String response = readRecord(input);
        if ("stream: OK".equals(response)) return MalwareScanResult.clean("ClamAV", sent);
        if (response.startsWith("stream: ") && response.endsWith(" FOUND")) {
            return MalwareScanResult.infected("ClamAV",
                    response.substring("stream: ".length(), response.length() - " FOUND".length()), sent);
        }
        return MalwareScanResult.error("Malware scanner returned an invalid response.", sent);
    }

    private String readRecord(InputStream input) throws IOException {
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        int next;
        while ((next = input.read()) != -1 && next != 0) {
            if (response.size() >= MAX_RESPONSE) throw new IOException("Scanner response is too long.");
            response.write(next);
        }
        if (next != 0 || response.size() == 0) throw new IOException("Scanner response is incomplete.");
        return response.toString(StandardCharsets.US_ASCII);
    }
}
