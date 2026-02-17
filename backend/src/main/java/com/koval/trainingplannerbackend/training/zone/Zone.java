package com.koval.trainingplannerbackend.training.zone;

import com.fasterxml.jackson.annotation.JsonAlias;


/**
 * @param low  Generic lower bound (%, sec/km, sec/100m)
 * @param high Generic upper bound
 */
public record Zone(String label,
                   @JsonAlias("lowerPercent") int low,
                   @JsonAlias("upperPercent") int high,
                   String description) {

}
