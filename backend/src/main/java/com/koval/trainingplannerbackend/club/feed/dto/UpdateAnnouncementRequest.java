package com.koval.trainingplannerbackend.club.feed.dto;

import java.util.List;

public record UpdateAnnouncementRequest(String content, List<String> mediaIds) {}
