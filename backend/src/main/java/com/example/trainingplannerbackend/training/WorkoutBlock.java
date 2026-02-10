package com.example.trainingplannerbackend.training;

import java.util.List;

public class WorkoutBlock {
    private BlockType type;
    private int durationSeconds;
    private Integer powerTargetPercent;
    private Integer powerStartPercent;
    private Integer powerEndPercent;
    private Integer cadenceTarget;
    private Integer repeats;
    private String label;

    public WorkoutBlock() {
    }

    public WorkoutBlock(BlockType type, int durationSeconds, Integer powerTargetPercent, Integer powerStartPercent,
            Integer powerEndPercent, Integer cadenceTarget, Integer repeats, String label) {
        this.type = type;
        this.durationSeconds = durationSeconds;
        this.powerTargetPercent = powerTargetPercent;
        this.powerStartPercent = powerStartPercent;
        this.powerEndPercent = powerEndPercent;
        this.cadenceTarget = cadenceTarget;
        this.repeats = repeats;
        this.label = label;
    }

    // Getters and Setters simplified for brevity in verification, usually generate
    // all
    public BlockType getType() {
        return type;
    }

    public void setType(BlockType type) {
        this.type = type;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Integer getPowerTargetPercent() {
        return powerTargetPercent;
    }

    public void setPowerTargetPercent(Integer powerTargetPercent) {
        this.powerTargetPercent = powerTargetPercent;
    }

    // ... add more if needed strictly, otherwise Jackson might use field access if
    // configured, but setters are safer
    public Integer getPowerStartPercent() {
        return powerStartPercent;
    }

    public void setPowerStartPercent(Integer powerStartPercent) {
        this.powerStartPercent = powerStartPercent;
    }

    public Integer getPowerEndPercent() {
        return powerEndPercent;
    }

    public void setPowerEndPercent(Integer powerEndPercent) {
        this.powerEndPercent = powerEndPercent;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
