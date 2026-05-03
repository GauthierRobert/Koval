package com.koval.trainingplannerbackend.training;

import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Caches Training id → title lookups so AI tool services don't re-issue a {@code findById}
 * per turn just to resolve a display label. Backed by Spring's default cache manager
 * (in-memory ConcurrentMap unless overridden).
 */
@Component
public class TrainingTitleResolver {

    private static final String UNKNOWN_TITLE = "Unknown";

    private final TrainingRepository trainingRepository;

    public TrainingTitleResolver(TrainingRepository trainingRepository) {
        this.trainingRepository = trainingRepository;
    }

    @Cacheable(value = "trainingTitles", unless = "#result == 'Unknown'")
    public String resolveTitle(String trainingId) {
        if (trainingId == null || trainingId.isBlank()) return UNKNOWN_TITLE;
        return trainingRepository.findById(trainingId)
                .map(Training::getTitle)
                .orElse(UNKNOWN_TITLE);
    }
}
