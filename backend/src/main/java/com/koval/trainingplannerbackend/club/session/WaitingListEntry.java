package com.koval.trainingplannerbackend.club.session;

import java.time.LocalDateTime;

public record WaitingListEntry(String userId, LocalDateTime joinedAt) {}
