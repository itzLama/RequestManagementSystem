package com.requestmanagement.backend.security;

import com.requestmanagement.backend.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** JSON bodies above masar.http.max-json-body-bytes (256 KiB by default) are refused with 413. */
@SpringBootTest
@AutoConfigureMockMvc
class RequestBodySizeLimitIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ApiExceptionHandler handler;

    @Test
    void oversizedJsonIsRejectedBeforeParsingEvenOnPublicEndpoints() throws Exception {
        String huge = "{\"title\":\"x\",\"description\":\"" + "a".repeat(300_000) + "\"}";
        mvc.perform(post("/api/guest/requests/general").contentType("application/json").content(huge))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.message").value("Request body is too large."));
        mvc.perform(post("/api/auth/login").contentType("application/json").content(huge))
                .andExpect(status().is(413));
        // Normal-sized bodies continue to the usual checks (here: missing CSRF token).
        mvc.perform(post("/api/guest/requests/general").contentType("application/json").content("{\"title\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void bodiesWithoutContentLengthStopBeingReadAtTheLimit() throws Exception {
        RequestBodySizeLimitFilter filter = new RequestBodySizeLimitFilter(1024);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/anything") {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
        };
        request.setContentType("application/json");
        request.setContent("b".repeat(5000).getBytes(StandardCharsets.UTF_8));
        InputStream[] seen = new InputStream[1];
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> seen[0] = req.getInputStream());
        assertThatThrownBy(() -> seen[0].readAllBytes())
                .isInstanceOf(RequestBodySizeLimitFilter.RequestBodyTooLargeException.class);

        // ...and the handler turns the resulting parse failure into 413, other parse failures stay 400.
        var tooLarge = new HttpMessageNotReadableException("read failed",
                new RequestBodySizeLimitFilter.RequestBodyTooLargeException(), new MockHttpInputMessage(new byte[0]));
        assertThat(handler.handleUnreadableBody(tooLarge).getStatusCode().value()).isEqualTo(413);
        var malformed = new HttpMessageNotReadableException("bad json", new MockHttpInputMessage(new byte[0]));
        assertThat(handler.handleUnreadableBody(malformed).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void multipartUploadsAreNotAffected() throws Exception {
        RequestBodySizeLimitFilter filter = new RequestBodySizeLimitFilter(1024);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/projects/1/tasks/1/attachments");
        request.setContentType("multipart/form-data; boundary=x");
        request.setContent(new byte[5000]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] passed = {false};
        filter.doFilter(request, response, (req, res) -> passed[0] = true);
        assertThat(passed[0]).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
