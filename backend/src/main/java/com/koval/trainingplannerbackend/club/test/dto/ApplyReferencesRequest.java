package com.koval.trainingplannerbackend.club.test.dto;

import java.util.List;

/** When {@code ruleIds} is null or empty, every rule with a successfully computed reference is applied. */
public record ApplyReferencesRequest(List<String> ruleIds) {}
