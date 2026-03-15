package com.koval.trainingplannerbackend.club.dto;

import com.koval.trainingplannerbackend.club.ClubActivityType;

import java.time.LocalDateTime;

public record ClubActivityResponse(String id, ClubActivityType type, String actorId,
                                   String actorName, String targetId, String targetTitle,
                                   LocalDateTime occurredAt) {}
