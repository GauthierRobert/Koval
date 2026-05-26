package com.koval.trainingplannerbackend.mcp;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * Decorates a {@link ToolCallback} so its published {@link ToolDefinition#inputSchema() inputSchema}
 * has nested {@code $defs} hoisted to the document root (see {@link McpSchemaUtils#hoistDefs}).
 * Execution is delegated unchanged; only the advertised schema is repaired.
 */
class SchemaCorrectingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolDefinition correctedDefinition;

    SchemaCorrectingToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
        ToolDefinition original = delegate.getToolDefinition();
        this.correctedDefinition = ToolDefinition.builder()
                .name(original.name())
                .description(original.description())
                .inputSchema(McpSchemaUtils.hoistDefs(original.inputSchema()))
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return correctedDefinition;
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return delegate.call(toolInput, toolContext);
    }
}
