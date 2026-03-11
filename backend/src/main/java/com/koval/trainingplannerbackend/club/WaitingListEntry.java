package com.koval.trainingplannerbackend.club;

import java.time.LocalDateTime;

public record WaitingListEntry(String userId, LocalDateTime joinedAt) {}
