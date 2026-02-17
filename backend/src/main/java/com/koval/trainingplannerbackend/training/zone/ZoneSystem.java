package com.koval.trainingplannerbackend.training.zone;

import com.koval.trainingplannerbackend.training.model.SportType;
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
    private SportType sportType = SportType.CYCLING;
    private ZoneReferenceType referenceType;
    private String referenceName;
    private List<Zone> zones = new ArrayList<>();
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
}
