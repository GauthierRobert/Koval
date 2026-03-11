package com.koval.trainingplannerbackend.club;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
@Document(collection = "club_training_sessions")
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
    private String description;
    private String linkedTrainingId;
    private List<String> participantIds = new ArrayList<>();
    private LocalDateTime createdAt;
    private String recurringTemplateId;
    private Integer maxParticipants;
    private List<WaitingListEntry> waitingList = new ArrayList<>();

    public boolean isFull() {
        return maxParticipants != null && participantIds.size() >= maxParticipants;
    }

    public boolean isOnWaitingList(String userId) {
        return waitingList.stream().anyMatch(e -> Objects.equals(e.userId(), userId));
    }
}
