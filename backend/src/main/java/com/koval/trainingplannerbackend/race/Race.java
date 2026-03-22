package com.koval.trainingplannerbackend.race;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

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
    private String scheduledDate; // YYYY-MM-DD

    // Raw GPX stored as binary (typically 50-500KB each)
    private byte[] swimGpx;
    private byte[] bikeGpx;
    private byte[] runGpx;

    // Number of loops for each GPX (1 = single loop / full course, >1 = GPX is one lap repeated N times)
    private Integer swimGpxLoops;
    private Integer bikeGpxLoops;
    private Integer runGpxLoops;

    private String createdBy;    // userId
    private LocalDateTime createdAt;
    private boolean verified;
}
