package com.koval.trainingplannerbackend.club.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CalendarClubSessionResponse(
        String id, String clubId, String clubName, String title, String sport,
        LocalDateTime scheduledAt, String location, String description,
        Integer durationMinutes, List<String> participantIds,
        Integer maxParticipants, String clubGroupId, String clubGroupName,
        boolean joined, boolean onWaitingList, int waitingListPosition,
        LocalDateTime openToAllFrom
) {}
