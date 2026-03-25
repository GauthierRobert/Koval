package com.koval.trainingplannerbackend.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Activates AI debugging tools:
 * - HTTP-level request/response logging ({@code app.ai.debug-calls=true})
 * - Prompt-level logging ({@code app.ai.log-prompts=true})
 */
@Configuration
class AIDebugConfig {

    @Bean
    @ConditionalOnProperty(name = "app.ai.debug-calls", havingValue = "true")
    RestClientCustomizer anthropicCallLoggerCustomizer() {
        AnthropicCallLogger logger = new AnthropicCallLogger();
        return builder -> builder.requestInterceptor(logger);
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.log-prompts", havingValue = "true")
    PromptLogger promptLogger() {
        return new PromptLogger();
    }
}
