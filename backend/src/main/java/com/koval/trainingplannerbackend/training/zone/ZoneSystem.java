package com.koval.trainingplannerbackend.training.zone;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "zone_systems")
public class ZoneSystem {
    @Id
    private String id;

    @Indexed
    private String coachId;

    private String name;
    private com.koval.trainingplannerbackend.training.SportType sportType = com.koval.trainingplannerbackend.training.SportType.CYCLING;
    private ZoneReferenceType referenceType;
    private List<Zone> zones = new ArrayList<>();

    private boolean isActive;
    // If true, this is the system used if no other active system is found or for
    // fallback
    private boolean isDefault;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
}
