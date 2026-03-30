package com.koval.trainingplannerbackend.ai.toon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Converts JSON strings to TOON (Token-Oriented Object Notation) format.
 * <p>
 * TOON is a compact tabular format that reduces token count for structured data:
 * <pre>
 * Array:  [n]{key1,key2,...}:\n  val1,val2\n  val3,val4
 * Object: {key1,key2,...}:val1,val2
 * Other:  pass through unchanged
 * </pre>
 */
public final class ToonConverter {

    private static final Logger log = LoggerFactory.getLogger(ToonConverter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToonConverter() {}

    /**
     * Converts a JSON string to TOON format.
     * Returns the original string unchanged if it cannot be parsed or is not an object/array.
     */
    public static String convert(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node.isArray()) {
                return arrayToToon((ArrayNode) node);
            }
            if (node.isObject()) {
                return objectToToon((ObjectNode) node);
            }
            // Primitive — pass through
            return json;
        } catch (Exception e) {
            log.debug("TOON conversion skipped (not valid JSON): {}", e.getMessage());
            return json;
        }
    }

    private static String arrayToToon(ArrayNode array) {
        if (array.isEmpty()) {
            return "[]";
        }
        // Check if all elements are objects with compatible keys
        if (!allObjects(array)) {
            return array.toString();
        }
        Set<String> keys = collectKeys(array);
        if (keys.isEmpty()) {
            return array.toString();
        }

        List<String> keyList = new ArrayList<>(keys);
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(array.size()).append("]{");
        sb.append(String.join(",", keyList));
        sb.append("}:\n");

        for (JsonNode element : array) {
            sb.append("  ");
            ObjectNode obj = (ObjectNode) element;
            for (int i = 0; i < keyList.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(formatValue(obj.get(keyList.get(i))));
            }
            sb.append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private static String objectToToon(ObjectNode obj) {
        List<String> keys = new ArrayList<>();
        obj.fieldNames().forEachRemaining(keys::add);
        if (keys.isEmpty()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append(String.join(",", keys));
        sb.append("}:");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(formatValue(obj.get(keys.get(i))));
        }
        return sb.toString();
    }

    private static String formatValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.isNumber()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asText();
        }
        if (node.isTextual()) {
            String text = node.asText();
            // Quote if value contains comma, newline, or is empty
            if (text.contains(",") || text.contains("\n") || text.isEmpty()) {
                return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            }
            return text;
        }
        // Nested object or array — keep as inline JSON
        return node.toString();
    }

    private static boolean allObjects(ArrayNode array) {
        for (JsonNode element : array) {
            if (!element.isObject()) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> collectKeys(ArrayNode array) {
        Set<String> keys = new LinkedHashSet<>();
        for (JsonNode element : array) {
            element.fieldNames().forEachRemaining(keys::add);
        }
        return keys;
    }
}
