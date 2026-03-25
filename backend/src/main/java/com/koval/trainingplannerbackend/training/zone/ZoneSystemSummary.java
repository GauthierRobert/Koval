package com.koval.trainingplannerbackend.training.zone;

import com.koval.trainingplannerbackend.training.model.SportType;

import java.util.List;

/**
 * Lean summary of a ZoneSystem for list responses.
 * Use {@link ZoneToolService#getDefaultZoneSystem} for full zone definitions.
 */
public record ZoneSystemSummary(
        String id,
        String name,
        SportType sportType,
        ZoneReferenceType referenceType,
        int zoneCount,
        boolean isDefault,
        List<String> zoneLabels
) {
    public static ZoneSystemSummary from(ZoneSystem zs) {
        List<String> labels = zs.getZones() != null
                ? zs.getZones().stream().map(z -> z.label() + ":" + z.low() + "-" + z.high() + "%").toList()
                : List.of();
        return new ZoneSystemSummary(
                zs.getId(),
                zs.getName(),
                zs.getSportType(),
                zs.getReferenceType(),
                zs.getZones() != null ? zs.getZones().size() : 0,
                Boolean.TRUE.equals(zs.getDefaultForSport()),
                labels
        );
    }
}
