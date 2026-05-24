package com.koval.trainingplannerbackend.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the JSON Schema {@code required} arrays generated for the {@code createTraining} and
 * {@code updateTraining} MCP tools.
 *
 * <p>The bug this guards against: Spring AI's {@link JsonSchemaGenerator} treats every property as
 * required by default, which marked all 18 fields of {@link McpWorkoutElementInput} required (a
 * leaf/set element only ever populates a subset) and over-constrained the top-level training input.
 * Strict MCP clients / structured-output providers reject or junk-fill such calls. The fix annotates
 * the conditional fields {@code @JsonProperty(required = false)}; this test asserts the resulting
 * schema so it can't regress (notably across the Spring AI 2.0 GA upgrade).</p>
 *
 * <p>This exercises the exact code path the MCP server uses to publish a tool's {@code inputSchema}
 * ({@link JsonSchemaGenerator#generateForMethodInput}), so it stays faithful without booting Spring.</p>
 */
class McpTrainingSchemaTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void createTrainingSchema_marksOnlyTitleSportTypeAndBlocksRequired() throws Exception {
        JsonNode trainingInput = trainingInputNode(method("createTraining", McpTrainingInput.class));

        assertThat(requiredOf(trainingInput))
                .containsExactlyInAnyOrder("title", "sportType", "blocks");
    }

    @Test
    void updateTrainingSchema_marksOnlyTitleSportTypeAndBlocksRequired() throws Exception {
        JsonNode trainingInput = trainingInputNode(method("updateTraining", String.class, McpTrainingInput.class));

        assertThat(requiredOf(trainingInput))
                .containsExactlyInAnyOrder("title", "sportType", "blocks");
    }

    @Test
    void workoutElementSchema_hasNoRequiredFields() throws Exception {
        JsonNode trainingInput = trainingInputNode(method("createTraining", McpTrainingInput.class));
        JsonNode workoutElement = trainingInput.get("$defs").get("McpWorkoutElementInput");

        // A workout element is either a leaf block or a set, so no field is always required:
        // the polymorphism means the required array must be empty (victools omits it entirely).
        assertThat(requiredOf(workoutElement)).isEmpty();
    }

    @Test
    void schemaStillCarriesFieldDescriptions() throws Exception {
        JsonNode trainingInput = trainingInputNode(method("createTraining", McpTrainingInput.class));

        assertThat(trainingInput.get("properties").get("title").get("description").asString())
                .isNotBlank();
        JsonNode workoutElement = trainingInput.get("$defs").get("McpWorkoutElementInput");
        assertThat(workoutElement.get("properties").get("type").get("description").asString())
                .isNotBlank();
    }

    private static Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return McpTrainingTools.class.getMethod(name, parameterTypes);
    }

    /**
     * Locate the {@link McpTrainingInput} object schema within a generated method-input schema. It is
     * the single parameter property whose value declares {@code McpWorkoutElementInput} under
     * {@code $defs}, so we don't depend on retained parameter names.
     */
    private static JsonNode trainingInputNode(Method method) {
        JsonNode root = MAPPER.readTree(JsonSchemaGenerator.generateForMethodInput(method));
        JsonNode properties = root.get("properties");
        for (var entry : properties.properties()) {
            JsonNode candidate = entry.getValue();
            if (candidate.path("$defs").has("McpWorkoutElementInput")) {
                return candidate;
            }
        }
        throw new AssertionError("No McpTrainingInput parameter found in schema: " + root);
    }

    private static Set<String> requiredOf(JsonNode objectSchema) {
        Set<String> required = new HashSet<>();
        JsonNode requiredArray = objectSchema.get("required");
        if (requiredArray != null) {
            requiredArray.forEach(node -> required.add(node.asString()));
        }
        return required;
    }
}
