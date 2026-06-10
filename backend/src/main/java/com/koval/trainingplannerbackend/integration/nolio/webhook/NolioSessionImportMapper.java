package com.koval.trainingplannerbackend.integration.nolio.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.koval.trainingplannerbackend.integration.nolio.NolioSports;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.model.SportType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Maps an achieved workout from the direct Nolio API ({@code /get/training/})
 * to a {@link CompletedSession}. Nolio units: duration seconds, distance
 * kilometers, power watts.
 */
@Component
public class NolioSessionImportMapper {

    public CompletedSession map(JsonNode workout) {
        CompletedSession session = new CompletedSession();
        session.setNolioActivityId(workout.path("nolio_id").asText(null));
        session.setTitle(workout.path("name").asText("Nolio activity"));
        session.setSportType(sportTypeName(workout.path("sport_id").asInt(-1)));
        session.setCompletedAt(completedAt(workout));
        session.setSyntheticCompletion(false);

        int duration = workout.path("duration").asInt(0);
        session.setTotalDurationSeconds(duration);

        double distanceKm = workout.path("distance").asDouble(0);
        if (distanceKm > 0) {
            session.setTotalDistance(distanceKm * 1000.0);
            if (duration > 0) {
                session.setAvgSpeed(distanceKm * 1000.0 / duration);
            }
        }

        double avgWatt = workout.path("avg_watt").asDouble(0);
        session.setAvgPower(avgWatt);
        double np = workout.path("np").asDouble(0);
        if (np > 0) {
            session.setNormalizedPower(np);
        }
        int rpe = workout.path("rpe").asInt(0);
        if (rpe > 0) {
            session.setRpe(rpe);
        }
        double tss = workout.path("load_coggan").asDouble(0);
        if (tss > 0) {
            session.setTss(tss);
        }

        if (session.getTotalDistance() != null || duration > 0) {
            session.setBlockSummaries(List.of(new CompletedSession.BlockSummary(
                    session.getTitle(),
                    session.getSportType(),
                    duration,
                    0, session.getAvgPower(),
                    session.getAvgCadence(), session.getAvgHR(),
                    session.getTotalDistance() != null ? session.getTotalDistance() : 0)));
        }
        return session;
    }

    /** Refreshes a previously imported session in place after an updated_event. */
    public void applyUpdate(CompletedSession target, CompletedSession fromNolio) {
        target.setTitle(fromNolio.getTitle());
        target.setSportType(fromNolio.getSportType());
        if (fromNolio.getCompletedAt() != null) {
            target.setCompletedAt(fromNolio.getCompletedAt());
        }
        target.setTotalDurationSeconds(fromNolio.getTotalDurationSeconds());
        if (fromNolio.getTotalDistance() != null) {
            target.setTotalDistance(fromNolio.getTotalDistance());
        }
        if (fromNolio.getAvgPower() > 0) {
            target.setAvgPower(fromNolio.getAvgPower());
        }
        if (fromNolio.getNormalizedPower() != null) {
            target.setNormalizedPower(fromNolio.getNormalizedPower());
        }
        if (fromNolio.getRpe() != null) {
            target.setRpe(fromNolio.getRpe());
        }
        if (fromNolio.getTss() != null) {
            target.setTss(fromNolio.getTss());
        }
    }

    private static String sportTypeName(int nolioSportId) {
        SportType sport = NolioSports.fromNolioSportId(nolioSportId);
        return sport != null ? sport.name() : "OTHER";
    }

    private static LocalDateTime completedAt(JsonNode workout) {
        String dateStart = workout.path("date_start").asText(null);
        if (dateStart == null || dateStart.isBlank()) return null;
        try {
            LocalDate date = LocalDate.parse(dateStart);
            String hourStart = workout.path("hour_start").asText("");
            if (!hourStart.isBlank()) {
                return date.atTime(LocalTime.parse(hourStart));
            }
            return date.atStartOfDay();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
