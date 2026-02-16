package com.koval.trainingplannerbackend.training.zone;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Zone {
    private String label;
    @com.fasterxml.jackson.annotation.JsonAlias("lowerPercent")
    private int low; // Generic lower bound (%, sec/km, sec/100m)
    @com.fasterxml.jackson.annotation.JsonAlias("upperPercent")
    private int high; // Generic upper bound
    private String description;

    // Deprecated but kept for backward compatibility/clarity if needed
    // or mapped to low/high via getters/setters
    // private int lowerPercent;
    // private int upperPercent;
}
