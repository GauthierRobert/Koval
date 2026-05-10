package com.koval.trainingplannerbackend.club.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RedeemInviteCodeRequest(@NotBlank @Size(max = 32) String code) {}
