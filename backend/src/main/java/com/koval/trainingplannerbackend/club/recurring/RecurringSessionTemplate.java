package com.koval.trainingplannerbackend.club.recurring;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.koval.trainingplannerbackend.club.session.GroupLinkedTraining;
import com.koval.trainingplannerbackend.club.session.OpenToAllDelayUnit;
import com.koval.trainingplannerbackend.club.session.SessionCategory;
import com.koval.trainingplannerbackend.pacing.dto.RouteCoordinate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

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
    private Double meetingPointLat;
    private Double meetingPointLon;
    private String description;
    private String linkedTrainingId;
    private List<GroupLinkedTraining> linkedTrainings = new ArrayList<>();
    private Integer maxParticipants;
    private Integer durationMinutes;
    private String clubGroupId;
    private boolean openToAll;
    private Integer openToAllDelayValue;
    private OpenToAllDelayUnit openToAllDelayUnit;
    private String responsibleCoachId;
    private LocalDate endDate;
    private boolean active = true;
    private LocalDateTime createdAt;

    private SessionCategory category = SessionCategory.SCHEDULED;

    @JsonIgnore
    private byte[] gpxData;
    private String gpxFileName;
    private List<RouteCoordinate> routeCoordinates;
}
