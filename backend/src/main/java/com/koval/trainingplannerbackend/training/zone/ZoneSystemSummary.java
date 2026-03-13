package com.koval.trainingplannerbackend.training.zone;

import com.koval.trainingplannerbackend.training.model.SportType;

/**
 * Lean summary of a ZoneSystem for AI tool responses, avoiding full zone serialization.
 */
public record ZoneSystemSummary(
        String id,
        String name,
        SportType sportType,
        ZoneReferenceType referenceType,
        int zoneCount,
        boolean isDefault
) {
    public static ZoneSystemSummary from(ZoneSystem zs) {
        return new ZoneSystemSummary(
                zs.getId(),
                zs.getName(),
                zs.getSportType(),
                zs.getReferenceType(),
                zs.getZones() != null ? zs.getZones().size() : 0,
                Boolean.TRUE.equals(zs.getDefaultForSport())
        );
    }
}
