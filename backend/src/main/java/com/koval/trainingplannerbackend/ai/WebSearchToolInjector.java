package com.koval.trainingplannerbackend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Injects Anthropic's built-in web_search_20250305 tool into every request body.
 * Used for clients that need internet access (e.g. raceCompletionClient).
 * Anthropic executes the search server-side — no local tool callback required.
 */
public class WebSearchToolInjector implements ClientHttpRequestInterceptor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        return execution.execute(request, injectWebSearch(body));
    }

    private byte[] injectWebSearch(byte[] body) {
        try {
            ObjectNode root = (ObjectNode) MAPPER.readTree(body);
            ArrayNode tools = root.has("tools") && root.get("tools").isArray()
                    ? (ArrayNode) root.get("tools")
                    : root.putArray("tools");

            ObjectNode webSearch = MAPPER.createObjectNode();
            webSearch.put("type", "web_search_20250305");
            webSearch.put("name", "web_search");
            tools.add(webSearch);

            return MAPPER.writeValueAsBytes(root);
        } catch (IOException e) {
            return body; // fallback: send original if parsing fails
        }
    }
}
