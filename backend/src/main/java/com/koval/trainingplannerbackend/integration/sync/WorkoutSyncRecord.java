package com.koval.trainingplannerbackend.integration.sync;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Tracks the outbound sync of one (athlete, source workout, provider) triple to an external
 * calendar / training-plan service (Polar Flow, Garmin Connect, ...).
 *
 * <p>The {@code sourceType} discriminates between a personal {@code SCHEDULED_WORKOUT}
 * (created via {@code ScheduleService}/{@code CoachService}) and a {@code CLUB_SESSION} the
 * athlete joined. In both cases we keep the provider-side external id so reschedules and
 * cancellations can call update/delete instead of creating duplicates.
 */
@Getter
@Setter
@Document(collection = "workout_sync_records")
@CompoundIndex(name = "sync_lookup_idx",
        def = "{'athleteId': 1, 'sourceType': 1, 'sourceId': 1, 'providerId': 1}",
        unique = true)
public class WorkoutSyncRecord {

    @Id
    private String id;

    @Indexed
    private String athleteId;

    private WorkoutSyncSourceType sourceType;
    private String sourceId;
    private String providerId;

    private String externalRef;
    private WorkoutSyncStatus status = WorkoutSyncStatus.PENDING;
    private String error;
    private LocalDateTime lastSyncedAt;
    private LocalDateTime createdAt = LocalDateTime.now();

    public WorkoutSyncRecord() {}

    public WorkoutSyncRecord(String athleteId, WorkoutSyncSourceType sourceType,
                             String sourceId, String providerId) {
        this.athleteId = athleteId;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.providerId = providerId;
    }
}
