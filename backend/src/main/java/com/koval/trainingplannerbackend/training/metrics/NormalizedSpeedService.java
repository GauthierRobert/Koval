package com.koval.trainingplannerbackend.training.metrics;

import com.koval.trainingplannerbackend.training.model.SportType;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.stereotype.Service;

import java.util.OptionalDouble;

/**
 * Reads a session's FIT file and produces a sport-appropriate normalized speed:
 * NGP for running, NSS for swimming. Returns empty for cycling (TSS uses power
 * directly) and on any read or parse failure.
 */
@Service
public class NormalizedSpeedService {

    private static final Logger log = LoggerFactory.getLogger(NormalizedSpeedService.class);

    private final GridFsOperations gridFsOperations;

    public NormalizedSpeedService(GridFsOperations gridFsOperations) {
        this.gridFsOperations = gridFsOperations;
    }

    public OptionalDouble computeFromFit(String fitFileId, SportType sport) {
        if (fitFileId == null || sport == null) return OptionalDouble.empty();
        if (sport == SportType.CYCLING) return OptionalDouble.empty();

        try {
            GridFSFile gridFile = gridFsOperations.findOne(
                    Query.query(Criteria.where("_id").is(new ObjectId(fitFileId))));
            if (gridFile == null) return OptionalDouble.empty();
            GridFsResource resource = gridFsOperations.getResource(gridFile);
            byte[] bytes = resource.getInputStream().readAllBytes();
            FitRecordExtractor.Samples samples = FitRecordExtractor.extract(bytes);
            if (samples.isEmpty()) return OptionalDouble.empty();

            double normalized = switch (sport) {
                case RUNNING, BRICK -> NormalizedSpeedCalculator.computeNgp(
                        samples.speedMps(), samples.altitudeMeters());
                case SWIMMING -> NormalizedSpeedCalculator.computeNss(samples.speedMps());
                case CYCLING -> 0.0;
            };
            return normalized > 0 ? OptionalDouble.of(normalized) : OptionalDouble.empty();
        } catch (Exception e) {
            log.warn("Failed to compute normalized speed for fitFileId={}: {}", fitFileId, e.getMessage());
            return OptionalDouble.empty();
        }
    }
}
