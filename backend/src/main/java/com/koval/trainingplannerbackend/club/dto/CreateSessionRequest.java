package com.koval.trainingplannerbackend.club.dto;

import com.koval.trainingplannerbackend.club.OpenToAllDelayUnit;

import java.time.LocalDateTime;

public record CreateSessionRequest(String title, String sport, LocalDateTime scheduledAt,
                                   String location, String description, String linkedTrainingId,
                                   Integer maxParticipants, Integer durationMinutes,
                                   String clubGroupId, String responsibleCoachId,
                                   Boolean openToAll, Integer openToAllDelayValue,
                                   OpenToAllDelayUnit openToAllDelayUnit) {}
