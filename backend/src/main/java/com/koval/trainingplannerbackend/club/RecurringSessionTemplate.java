package com.koval.trainingplannerbackend.club;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
@Document(collection = "recurring_session_templates")
public class RecurringSessionTemplate {
    @Id
    private String id;

    @Indexed
    private String clubId;

    private String createdBy;
    private String title;
    private String sport;
    private DayOfWeek dayOfWeek;
    private LocalTime timeOfDay;
    private String location;
    private String description;
    private String linkedTrainingId;
    private Integer maxParticipants;
    private Integer durationMinutes;
    private String clubGroupId;
    private boolean openToAll;
    private Integer openToAllDelayValue;
    private OpenToAllDelayUnit openToAllDelayUnit;
    private String responsibleCoachId;
    private boolean active = true;
    private LocalDateTime createdAt;
}
