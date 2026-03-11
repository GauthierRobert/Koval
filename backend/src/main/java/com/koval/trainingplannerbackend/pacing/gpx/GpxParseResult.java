package com.koval.trainingplannerbackend.pacing.gpx;

import com.koval.trainingplannerbackend.pacing.dto.RouteCoordinate;

import java.util.List;

public record GpxParseResult(List<CourseSegment> segments, List<RouteCoordinate> routeCoordinates) {}
