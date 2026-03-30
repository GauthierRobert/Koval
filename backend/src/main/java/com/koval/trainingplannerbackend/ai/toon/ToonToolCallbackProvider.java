package com.koval.trainingplannerbackend.ai.toon;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.stream.Stream;

/**
 * Wraps a {@link ToolCallbackProvider} to convert all tool responses from JSON to TOON format.
 * <p>
 * Each tool callback is wrapped so that after the original tool executes and returns JSON,
 * the response is converted to TOON before being sent back to the LLM.
 */
public class ToonToolCallbackProvider implements ToolCallbackProvider {

    private final ToolCallbackProvider delegate;

    public ToonToolCallbackProvider(ToolCallbackProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return Stream.of(delegate.getToolCallbacks())
                .map(ToonToolCallback::new)
                .toArray(ToolCallback[]::new);
    }

    private static class ToonToolCallback implements ToolCallback {

        private final ToolCallback delegate;

        ToonToolCallback(ToolCallback delegate) {
            this.delegate = delegate;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String toolInput) {
            return ToonConverter.convert(delegate.call(toolInput));
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return ToonConverter.convert(delegate.call(toolInput, toolContext));
        }
    }
}
