package com.koval.trainingplannerbackend.race;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

@Getter
@Setter
@Document(collection = "races")
public class Race {

    @Id
    private String id;

    @NotBlank
    @Indexed
    private String title;

    private String sport;        // CYCLING | RUNNING | SWIMMING | TRIATHLON | OTHER
    private String location;
    private String country;
    private String region;
    private String distance;     // display string e.g. "140.6 miles"

    private Double swimDistanceM;
    private Double bikeDistanceM;
    private Double runDistanceM;
    private Integer elevationGainM;

    private String description;
    private String website;
    private Integer typicalMonth; // 1-12

    // Raw GPX stored as binary (typically 50-500KB each)
    private byte[] swimGpx;
    private byte[] bikeGpx;
    private byte[] runGpx;

    private String createdBy;    // userId
    private LocalDateTime createdAt;
    private boolean verified;
}
