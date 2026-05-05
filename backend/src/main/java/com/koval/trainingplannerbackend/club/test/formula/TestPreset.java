package com.koval.trainingplannerbackend.club.test.formula;

import com.koval.trainingplannerbackend.club.test.ReferenceUpdateRule;
import com.koval.trainingplannerbackend.club.test.TestSegment;

import java.util.List;

/** Read-only template a coach picks to autofill a new test's segments + rules. */
public record TestPreset(
        String id,
        String labelKey,
        String descriptionKey,
        List<TestSegment> segments,
        List<ReferenceUpdateRule> referenceUpdates
) {}
