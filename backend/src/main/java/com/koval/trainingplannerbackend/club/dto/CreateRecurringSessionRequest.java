package com.koval.trainingplannerbackend.club.dto;

import com.koval.trainingplannerbackend.club.session.OpenToAllDelayUnit;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record CreateRecurringSessionRequest(String title, String sport, DayOfWeek dayOfWeek,
                                            LocalTime timeOfDay, String location, String description,
                                            String linkedTrainingId, Integer maxParticipants,
                                            Integer durationMinutes, String clubGroupId,
                                            String responsibleCoachId,
                                            Boolean openToAll, Integer openToAllDelayValue,
                                            OpenToAllDelayUnit openToAllDelayUnit) {}
