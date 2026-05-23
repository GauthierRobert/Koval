package com.koval.trainingplannerbackend.training.metrics;

import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.fit.FitFileStore;
import com.koval.trainingplannerbackend.training.model.SportType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Reads a session's FIT file and produces sport-appropriate normalized metrics:
 * NGP for running, NSS for swimming, NP for cycling. Returns empty on any
 * read or parse failure.
 */
@Service
public class NormalizedSpeedService {

    private static final Logger log = LoggerFactory.getLogger(NormalizedSpeedService.class);

    private final FitFileStore fitFileStore;

    public NormalizedSpeedService(FitFileStore fitFileStore) {
        this.fitFileStore = fitFileStore;
    }

    public OptionalDouble computeFromFit(CompletedSession session, SportType sport) {
        if (session == null || sport == null) return OptionalDouble.empty();
        if (sport == SportType.CYCLING) return OptionalDouble.empty();

        try {
            Optional<byte[]> bytes = fitFileStore.read(session);
            if (bytes.isEmpty()) return OptionalDouble.empty();
            FitRecordExtractor.Samples samples = FitRecordExtractor.extract(bytes.get());
            if (samples.isEmpty()) return OptionalDouble.empty();

            double normalized = switch (sport) {
                case RUNNING, BRICK -> NormalizedSpeedCalculator.computeNgp(samples.speedMps());
                case SWIMMING -> NormalizedSpeedCalculator.computeNss(samples.speedMps());
                case CYCLING -> 0.0;
            };
            return normalized > 0 ? OptionalDouble.of(normalized) : OptionalDouble.empty();
        } catch (Exception e) {
            log.warn("Failed to compute normalized speed for session {}: {}", session.getId(), e.getMessage());
            return OptionalDouble.empty();
        }
    }

    /**
     * Cycling-only Normalized Power in watts, computed from the FIT power stream.
     * Returns empty for non-cycling sports, missing FIT files, FITs without power
     * data, or any parse failure.
     */
    public OptionalDouble computeNormalizedPowerFromFit(CompletedSession session, SportType sport) {
        if (session == null || sport == null) return OptionalDouble.empty();
        if (sport != SportType.CYCLING && sport != SportType.BRICK) return OptionalDouble.empty();

        try {
            Optional<byte[]> bytes = fitFileStore.read(session);
            if (bytes.isEmpty()) return OptionalDouble.empty();
            List<Integer> watts = FitPowerExtractor.extractPower(bytes.get());
            if (watts.isEmpty()) return OptionalDouble.empty();
            double np = NormalizedPowerCalculator.computeNp(watts);
            return np > 0 ? OptionalDouble.of(np) : OptionalDouble.empty();
        } catch (Exception e) {
            log.warn("Failed to compute normalized power for session {}: {}", session.getId(), e.getMessage());
            return OptionalDouble.empty();
        }
    }
}
