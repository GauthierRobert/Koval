package com.koval.trainingplannerbackend.club.session;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.koval.trainingplannerbackend.pacing.dto.RouteCoordinate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
@Document(collection = "club_training_sessions")
@CompoundIndex(name = "participant_linked_idx", def = "{'participantIds': 1, 'linkedTrainingId': 1, 'cancelled': 1}")
public class ClubTrainingSession {
    @Id
    private String id;

    @Indexed
    private String clubId;

    private String createdBy;
    private String title;
    private String sport;
    private LocalDateTime scheduledAt;
    private String location;
    private Double meetingPointLat;
    private Double meetingPointLon;
    private String description;
    private String linkedTrainingId;
    private List<String> participantIds = new ArrayList<>();
    private LocalDateTime createdAt;
    private String recurringTemplateId;
    private String clubGroupId;
    private boolean openToAll;
    private Integer openToAllDelayValue;
    private OpenToAllDelayUnit openToAllDelayUnit;
    private String responsibleCoachId;
    private Integer maxParticipants;
    private Integer durationMinutes;
    private String linkedTrainingTitle;
    private String linkedTrainingDescription;
    private List<GroupLinkedTraining> linkedTrainings = new ArrayList<>();
    private List<WaitingListEntry> waitingList = new ArrayList<>();
    private boolean cancelled;
    private String cancellationReason;
    private LocalDateTime cancelledAt;

    private SessionCategory category = SessionCategory.SCHEDULED;

    @JsonIgnore
    private byte[] gpxData;
    private String gpxFileName;
    private List<RouteCoordinate> routeCoordinates;

    public LocalDateTime computeOpenToAllFrom() {
        if (!openToAll || scheduledAt == null) return null;
        int delay = openToAllDelayValue != null ? openToAllDelayValue : 2;
        OpenToAllDelayUnit unit = openToAllDelayUnit != null ? openToAllDelayUnit : OpenToAllDelayUnit.DAYS;
        return switch (unit) {
            case HOURS -> scheduledAt.minusHours(delay);
            case DAYS -> scheduledAt.minusDays(delay);
        };
    }

    public boolean isFull() {
        return maxParticipants != null && participantIds.size() >= maxParticipants;
    }

    public boolean isOnWaitingList(String userId) {
        return waitingList.stream().anyMatch(e -> Objects.equals(e.userId(), userId));
    }

    public List<GroupLinkedTraining> getEffectiveLinkedTrainings() {
        if (linkedTrainings != null && !linkedTrainings.isEmpty()) {
            return linkedTrainings;
        }
        if (linkedTrainingId != null && !linkedTrainingId.isBlank()) {
            GroupLinkedTraining glt = new GroupLinkedTraining();
            glt.setTrainingId(linkedTrainingId);
            glt.setTrainingTitle(linkedTrainingTitle);
            glt.setTrainingDescription(linkedTrainingDescription);
            return List.of(glt);
        }
        return List.of();
    }
}
