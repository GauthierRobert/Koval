package com.koval.trainingplannerbackend.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Activates HTTP-level Anthropic call logging when {@code app.ai.debug-calls=true}.
 * <p>
 * Spring AI's {@code AnthropicAutoConfiguration} applies {@link RestClientCustomizer} beans
 * to the RestClient it builds, so this interceptor is injected automatically.
 */
@Configuration
@ConditionalOnProperty(name = "app.ai.debug-calls", havingValue = "true")
class AIDebugConfig {

    @Bean
    RestClientCustomizer anthropicCallLoggerCustomizer() {
        AnthropicCallLogger logger = new AnthropicCallLogger();
        return builder -> builder.requestInterceptor(logger);
    }
}
