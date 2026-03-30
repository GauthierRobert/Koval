package com.koval.trainingplannerbackend.club.tools;

import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.GroupLinkedTraining;

import java.time.LocalDateTime;
import java.util.List;

public record ClubSessionSummary(String id, String title, String sport,
                                 LocalDateTime scheduledAt, String location,
                                 int participantCount, Integer maxParticipants,
                                 String linkedTrainingTitle, String clubGroupId,
                                 boolean cancelled, Integer durationMinutes) {

    public static ClubSessionSummary from(ClubTrainingSession s) {
        String linkedTitle = null;
        List<GroupLinkedTraining> linked = s.getEffectiveLinkedTrainings();
        if (linked != null && !linked.isEmpty()) {
            linkedTitle = linked.stream()
                    .map(GroupLinkedTraining::getTrainingTitle)
                    .filter(t -> t != null && !t.isBlank())
                    .findFirst().orElse(null);
        }
        return new ClubSessionSummary(
                s.getId(), s.getTitle(), s.getSport(),
                s.getScheduledAt(), s.getLocation(),
                s.getParticipantIds().size(), s.getMaxParticipants(),
                linkedTitle, s.getClubGroupId(),
                s.isCancelled(), s.getDurationMinutes()
        );
    }
}
