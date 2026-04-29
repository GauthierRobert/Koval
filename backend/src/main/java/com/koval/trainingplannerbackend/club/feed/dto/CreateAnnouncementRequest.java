package com.koval.trainingplannerbackend.club.feed.dto;

import java.util.List;

public record CreateAnnouncementRequest(String content, List<String> mediaIds) {}
