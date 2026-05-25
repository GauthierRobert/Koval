package com.koval.trainingplannerbackend.config;

import com.koval.trainingplannerbackend.mcp.McpServerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against the failure mode where an MCP tool adapter is wired into
 * {@link McpServerConfig#mcpTools} but its return-type records are never given native
 * reflection hints in {@link NativeImageHints}. That mismatch is invisible on the JVM
 * (and in our integration tests) but makes Jackson fail to serialize the tool result in
 * the GraalVM native image — the prod-only "serialization bug" that hit getAthleteContext.
 *
 * <p>The MCP tool list is read from {@code McpServerConfig} itself so the two stay in sync
 * without a second hand-maintained list to drift against.
 */
class NativeImageHintsTest {

    @Test
    void everyMcpToolAdapterAndItsInnerRecordsHaveReflectionHints() {
        RuntimeHints hints = new RuntimeHints();
        new NativeImageHints.Registrar().registerHints(hints, getClass().getClassLoader());

        List<Class<?>> mcpToolClasses = mcpToolClassesFromServerConfig();
        assertThat(mcpToolClasses)
                .as("McpServerConfig.mcpTools must declare the MCP tool adapters")
                .isNotEmpty();

        for (Class<?> toolClass : mcpToolClasses) {
            assertThat(RuntimeHintsPredicates.reflection()
                    .onType(toolClass)
                    .withMemberCategory(MemberCategory.INVOKE_DECLARED_METHODS)
                    .test(hints))
                    .as("%s is registered as an MCP tool but missing from NativeImageHints "
                            + "(add it to the toolServices array)", toolClass.getSimpleName())
                    .isTrue();

            // Inner records are the tool's return-type DTOs; Jackson serializes them
            // reflectively in the native image, so each needs declared-method access.
            for (Class<?> inner : toolClass.getDeclaredClasses()) {
                if (!inner.isRecord()) {
                    continue;
                }
                assertThat(RuntimeHintsPredicates.reflection()
                        .onType(inner)
                        .withMemberCategory(MemberCategory.INVOKE_DECLARED_METHODS)
                        .test(hints))
                        .as("Inner record %s.%s lacks native reflection hints; it will fail "
                                + "Jackson serialization in the native image",
                                toolClass.getSimpleName(), inner.getSimpleName())
                        .isTrue();
            }
        }
    }

    private static List<Class<?>> mcpToolClassesFromServerConfig() {
        Method mcpTools = Arrays.stream(McpServerConfig.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("mcpTools"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("McpServerConfig.mcpTools method not found"));
        return Arrays.<Class<?>>stream(mcpTools.getParameterTypes())
                .filter(p -> p.getSimpleName().startsWith("Mcp"))
                .toList();
    }
}
