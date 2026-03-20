package com.koval.trainingplannerbackend.club;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GroupLinkedTraining {
    private String clubGroupId;        // null = club-level
    private String clubGroupName;      // denormalized
    private String trainingId;
    private String trainingTitle;      // denormalized
    private String trainingDescription;// denormalized
}
