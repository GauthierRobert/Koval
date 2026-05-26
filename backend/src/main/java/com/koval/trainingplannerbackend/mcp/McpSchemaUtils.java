package com.koval.trainingplannerbackend.mcp;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Repairs the JSON Schema that Spring AI generates for MCP tool inputs.
 *
 * <p><b>The bug being fixed:</b> {@code JsonSchemaGenerator.generateForMethodInput} builds a wrapper
 * object schema with one property per tool parameter. For a parameter whose type is complex or
 * <em>recursive</em> (e.g. {@link McpWorkoutElementInput}, which nests itself via {@code elements}),
 * the underlying generator emits a {@code $defs} block <em>inside</em> that parameter's sub-schema
 * (at {@code properties.<param>.$defs}) while the corresponding references use document-root pointers
 * ({@code "$ref": "#/$defs/McpWorkoutElementInput"}). Because the document root has no {@code $defs},
 * those pointers resolve to nothing — strict MCP clients reject the tool with an
 * "unresolved $ref / McpWorkoutElementInput non résolu" schema error.
 *
 * <p>{@link #hoistDefs(String)} relocates every nested {@code $defs} entry to a single {@code $defs}
 * object at the document root, so the existing {@code #/$defs/...} references resolve. Schemas that
 * don't contain a nested {@code $defs} are returned unchanged.</p>
 */
public final class McpSchemaUtils {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String DEFS = "$defs";

    private McpSchemaUtils() {}

    /**
     * Returns an equivalent schema with all nested {@code $defs} definitions hoisted to the document
     * root. Input that is blank, not a JSON object, or already free of nested {@code $defs} is returned
     * verbatim (modulo re-serialization).
     */
    public static String hoistDefs(String inputSchema) {
        if (inputSchema == null || inputSchema.isBlank()) {
            return inputSchema;
        }
        JsonNode root = MAPPER.readTree(inputSchema);
        if (!root.isObject()) {
            return inputSchema;
        }
        ObjectNode rootObj = (ObjectNode) root;

        ObjectNode rootDefs = rootObj.has(DEFS) && rootObj.get(DEFS).isObject()
                ? (ObjectNode) rootObj.get(DEFS)
                : MAPPER.createObjectNode();

        boolean changed = collectNestedDefs(rootObj, rootDefs, true);
        if (!changed) {
            return inputSchema;
        }

        if (!rootDefs.isEmpty()) {
            rootObj.set(DEFS, rootDefs);
        }
        return MAPPER.writeValueAsString(rootObj);
    }

    /**
     * Walks {@code node}, moving every {@code $defs} entry it finds (except the root document's own
     * {@code $defs}) into {@code rootDefs}. Returns whether any nested {@code $defs} was relocated.
     */
    private static boolean collectNestedDefs(JsonNode node, ObjectNode rootDefs, boolean isRoot) {
        boolean changed = false;
        if (node instanceof ObjectNode obj) {
            if (!isRoot && obj.has(DEFS) && obj.get(DEFS).isObject()) {
                ObjectNode nestedDefs = (ObjectNode) obj.remove(DEFS);
                for (Map.Entry<String, JsonNode> def : nestedDefs.properties()) {
                    if (!rootDefs.has(def.getKey())) {
                        rootDefs.set(def.getKey(), def.getValue());
                    }
                }
                changed = true;
            }
            // Snapshot field names: we recurse into a possibly-mutated object.
            List<String> fields = new ArrayList<>(obj.propertyNames());
            for (String field : fields) {
                changed |= collectNestedDefs(obj.get(field), rootDefs, false);
            }
        } else if (node instanceof ArrayNode array) {
            for (JsonNode child : array) {
                changed |= collectNestedDefs(child, rootDefs, false);
            }
        }
        return changed;
    }
}
