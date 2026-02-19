package com.koval.trainingplannerbackend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Logs every raw HTTP request/response to the Anthropic API.
 * <p>
 * Enabled via {@code app.ai.debug-calls: true} in application.yml.
 * Captures both legs of a tool-use cycle:
 *   Call 1 → user msg + tool schemas  →  Claude responds with tool_use
 *   Call 2 → tool_result              →  Claude responds with final text
 */
class AnthropicCallLogger implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AnthropicCallLogger.class);
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private int callIndex = 0;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        int idx = ++callIndex;
        logRequest(idx, body);
        ClientHttpResponse response = execution.execute(request, body);
        return logAndBuffer(idx, response);
    }

    // ── request ─────────────────────────────────────────────────────────

    private void logRequest(int idx, byte[] body) {
        if (!log.isDebugEnabled()) return;
        log.debug("\n╔══ Anthropic CALL #{} ▶ REQUEST ══════════════════════════════╗\n{}\n╚═══════════════════════════════════════════════════════════════╝",
                idx, prettyJson(body));
    }

    // ── response ────────────────────────────────────────────────────────

    private ClientHttpResponse logAndBuffer(int idx, ClientHttpResponse response) throws IOException {
        byte[] body = StreamUtils.copyToByteArray(response.getBody());
        if (log.isDebugEnabled()) {
            log.debug("\n╔══ Anthropic CALL #{} ◀ RESPONSE ═════════════════════════════╗\n{}\n╚═══════════════════════════════════════════════════════════════╝",
                    idx, prettyJson(body));
        }
        return new BufferedResponse(response, body);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private String prettyJson(byte[] raw) {
        try {
            Object node = JSON.readValue(raw, Object.class);
            return JSON.writeValueAsString(node);
        } catch (Exception e) {
            return new String(raw);
        }
    }

    /** Re-wraps a consumed response so the body can still be read by Spring AI. */
    private record BufferedResponse(ClientHttpResponse delegate, byte[] body) implements ClientHttpResponse {

        @Override public InputStream getBody()                             { return new ByteArrayInputStream(body); }
        @Override public org.springframework.http.HttpHeaders getHeaders() { return delegate.getHeaders(); }
        @Override public HttpStatusCode getStatusCode() throws IOException  { return delegate.getStatusCode(); }
        @Override public String getStatusText() throws IOException         { return delegate.getStatusText(); }
        @Override public void close()                                      { delegate.close(); }
    }
}