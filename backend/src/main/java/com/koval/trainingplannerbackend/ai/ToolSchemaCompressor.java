package com.koval.trainingplannerbackend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.Iterator;

/**
 * Compresses tool schemas in Anthropic API requests to reduce input tokens.
 * <p>
 * Strips {@code description} fields from nested {@code input_schema} properties
 * and removes {@code additionalProperties} boilerplate. The tool-level
 * {@code description} is preserved. Field descriptions are redundant because
 * the system prompt already documents field semantics.
 * <p>
 * Registered as a {@link org.springframework.boot.restclient.RestClientCustomizer}
 * in {@link AIDebugConfig}.
 */
public class ToolSchemaCompressor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ToolSchemaCompressor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        return execution.execute(request, compressTools(body));
    }

    private byte[] compressTools(byte[] body) {
        try {
            ObjectNode root = (ObjectNode) MAPPER.readTree(body);
            if (!root.has("tools") || !root.get("tools").isArray()) {
                return body;
            }

            ArrayNode tools = (ArrayNode) root.get("tools");
            int beforeSize = body.length;

            for (JsonNode tool : tools) {
                if (tool.has("input_schema") && tool.get("input_schema").isObject()) {
                    stripSchemaDescriptions((ObjectNode) tool.get("input_schema"));
                }
            }

            byte[] compressed = MAPPER.writeValueAsBytes(root);
            if (log.isDebugEnabled()) {
                log.debug("Tool schema compressed: {} → {} bytes (saved {})",
                        beforeSize, compressed.length, beforeSize - compressed.length);
            }
            return compressed;
        } catch (IOException e) {
            log.warn("Tool schema compression failed, sending original: {}", e.getMessage());
            return body;
        }
    }

    /**
     * Recursively walks a JSON Schema node and removes:
     * - {@code description} fields from property definitions
     * - {@code additionalProperties} fields
     */
    private void stripSchemaDescriptions(ObjectNode schema) {
        schema.remove("additionalProperties");

        if (schema.has("properties") && schema.get("properties").isObject()) {
            ObjectNode props = (ObjectNode) schema.get("properties");
            Iterator<String> fieldNames = props.fieldNames();
            while (fieldNames.hasNext()) {
                String name = fieldNames.next();
                JsonNode prop = props.get(name);
                if (prop.isObject()) {
                    ObjectNode propObj = (ObjectNode) prop;
                    propObj.remove("description");
                    // Recurse into nested objects and arrays
                    stripSchemaDescriptions(propObj);
                }
            }
        }

        // Handle array items
        if (schema.has("items") && schema.get("items").isObject()) {
            stripSchemaDescriptions((ObjectNode) schema.get("items"));
        }

        // Handle anyOf / oneOf / allOf
        for (String keyword : new String[]{"anyOf", "oneOf", "allOf"}) {
            if (schema.has(keyword) && schema.get(keyword).isArray()) {
                for (JsonNode variant : schema.get(keyword)) {
                    if (variant.isObject()) {
                        stripSchemaDescriptions((ObjectNode) variant);
                    }
                }
            }
        }
    }
}
