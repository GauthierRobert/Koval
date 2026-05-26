package com.koval.trainingplannerbackend.mcp;

import io.modelcontextprotocol.json.schema.JsonSchemaValidator.ValidationResponse;
import io.modelcontextprotocol.json.schema.jackson3.DefaultJsonSchemaValidator;
import org.junit.jupiter.api.Test;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link McpSchemaUtils#hoistDefs} repairs the {@code createTraining} input schema so the
 * recursive {@code #/$defs/McpWorkoutElementInput} reference resolves.
 *
 * <p>This guards the production bug where the MCP server's input validator
 * ({@code io.modelcontextprotocol.util.ToolInputValidator} →
 * {@code DefaultJsonSchemaValidator}) failed with
 * {@code InvalidSchemaRefException: Reference /$defs/McpWorkoutElementInput cannot be resolved}
 * because Spring AI emitted {@code $defs} nested under the parameter instead of the document root.
 * We exercise that same validator here so the fix can't regress.</p>
 */
class McpSchemaUtilsTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final DefaultJsonSchemaValidator VALIDATOR = new DefaultJsonSchemaValidator();

    /** A valid createTraining argument payload — a warmup leaf plus a 5x interval set. */
    private static final String ARGS = """
            {
              "create": {
                "sportType": "CYCLING",
                "title": "VO2max 5x3",
                "blocks": [
                  { "type": "WARMUP", "durationSeconds": 600, "label": "Warmup", "intensityTarget": 60 },
                  { "repetitions": 5, "restDurationSeconds": 180, "restIntensity": 60,
                    "elements": [
                      { "type": "INTERVAL", "durationSeconds": 180, "label": "VO2", "intensityTarget": 115 }
                    ] }
                ]
              }
            }
            """;

    @Test
    void rawSchema_hasUnresolvableNestedDefs() throws Exception {
        JsonNode root = MAPPER.readTree(rawCreateTrainingSchema());

        // Reproduce the bug: $defs lives under properties.create, not at the document root.
        assertThat(root.path("$defs").isMissingNode()).isTrue();
        assertThat(root.path("properties").path("create").path("$defs").has("McpWorkoutElementInput")).isTrue();
    }

    @Test
    void hoistDefs_movesNestedDefsToDocumentRoot() throws Exception {
        JsonNode root = MAPPER.readTree(McpSchemaUtils.hoistDefs(rawCreateTrainingSchema()));

        assertThat(root.path("$defs").has("McpWorkoutElementInput")).isTrue();
        assertThat(root.path("properties").path("create").path("$defs").isMissingNode()).isTrue();
    }

    @Test
    void rawSchema_failsTheMcpValidator() throws Exception {
        boolean failed;
        try {
            ValidationResponse response = VALIDATOR.validate(schemaMap(rawCreateTrainingSchema()), argsMap());
            failed = !response.valid();
        } catch (RuntimeException e) {
            // The unresolved $ref surfaces as a thrown InvalidSchemaRefException in production.
            failed = true;
        }
        assertThat(failed)
                .as("raw schema with nested $defs must not validate (this is the reported bug)")
                .isTrue();
    }

    @Test
    void hoistedSchema_passesTheMcpValidator() throws Exception {
        ValidationResponse response =
                VALIDATOR.validate(schemaMap(McpSchemaUtils.hoistDefs(rawCreateTrainingSchema())), argsMap());

        assertThat(response.valid())
                .as("hoisted schema should validate a well-formed payload; error=%s", response.errorMessage())
                .isTrue();
    }

    private static String rawCreateTrainingSchema() throws NoSuchMethodException {
        Method m = McpTrainingTools.class.getMethod("createTraining", McpTrainingInput.class);
        return JsonSchemaGenerator.generateForMethodInput(m);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemaMap(String schema) {
        return MAPPER.readValue(schema, Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> argsMap() {
        return MAPPER.readValue(ARGS, Map.class);
    }
}
