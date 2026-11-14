package com.requestmanagement.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Caps non-multipart request bodies (JSON) before they are parsed: a declared Content-Length above
 * the limit is rejected with 413 straight away, and a body without one stops being read once it
 * passes the limit (the handler then answers 413, see ApiExceptionHandler). Multipart uploads keep
 * their own 25 MiB limits. Field-level limits (e.g. the 100 KB rich description) still apply.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RequestBodySizeLimitFilter extends OncePerRequestFilter {
    public static final String MESSAGE = "Request body is too large.";
    private final long maxBytes;

    public RequestBodySizeLimitFilter(@Value("${masar.http.max-json-body-bytes:262144}") long maxBytes) {
        if (maxBytes < 1024) throw new IllegalArgumentException("masar.http.max-json-body-bytes must be at least 1024.");
        this.maxBytes = maxBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        if (!(method.equals("POST") || method.equals("PUT") || method.equals("PATCH") || method.equals("DELETE"))) return true;
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"" + MESSAGE + "\"}");
            return;
        }
        filterChain.doFilter(new LimitedRequest(request, maxBytes), response);
    }

    /** Thrown while reading a body (without a usable Content-Length) that grows past the limit. */
    public static class RequestBodyTooLargeException extends IOException {
        RequestBodyTooLargeException() { super(MESSAGE); }
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private final long maxBytes;
        private ServletInputStream stream;

        LimitedRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) stream = new LimitedStream(super.getInputStream(), maxBytes);
            return stream;
        }
    }

    private static final class LimitedStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final long maxBytes;
        private long read;

        LimitedStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) count(1);
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = delegate.read(buffer, offset, length);
            if (count > 0) count(count);
            return count;
        }

        private void count(long bytes) throws IOException {
            read += bytes;
            if (read > maxBytes) throw new RequestBodyTooLargeException();
        }

        @Override public boolean isFinished() { return delegate.isFinished(); }
        @Override public boolean isReady() { return delegate.isReady(); }
        @Override public void setReadListener(ReadListener listener) { delegate.setReadListener(listener); }
    }
}
