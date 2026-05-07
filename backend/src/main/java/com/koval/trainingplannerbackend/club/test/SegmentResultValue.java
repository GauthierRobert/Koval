package com.koval.trainingplannerbackend.club.test;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SegmentResultValue {
    private double value;
    private SegmentResultUnit unit;
    /** Optional link to a {@code CompletedSession} when the value was extracted from a recorded ride/run/swim. */
    private String completedSessionId;
}
