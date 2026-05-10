package com.koval.trainingplannerbackend.club.feed.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ToggleReactionRequest(@NotBlank @Size(max = 16) String emoji) {}
