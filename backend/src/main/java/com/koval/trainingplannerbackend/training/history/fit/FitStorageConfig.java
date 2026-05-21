package com.koval.trainingplannerbackend.training.history.fit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers {@link FitStorageProperties} so {@code storage.fit.*} binds correctly. */
@Configuration
@EnableConfigurationProperties(FitStorageProperties.class)
public class FitStorageConfig {
}
