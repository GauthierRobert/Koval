package com.koval.trainingplannerbackend.club.dto;

import com.koval.trainingplannerbackend.club.session.OpenToAllDelayUnit;
import com.koval.trainingplannerbackend.club.session.SessionCategory;

import java.time.LocalDateTime;

public record CreateSessionRequest(SessionCategory category, String title, String sport, LocalDateTime scheduledAt,
                                   String location, Double meetingPointLat, Double meetingPointLon,
                                   String description, String linkedTrainingId,
                                   Integer maxParticipants, Integer durationMinutes,
                                   String clubGroupId, String responsibleCoachId,
                                   Boolean openToAll, Integer openToAllDelayValue,
                                   OpenToAllDelayUnit openToAllDelayUnit) {}
