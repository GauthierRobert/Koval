package com.koval.trainingplannerbackend.training.zone;

import com.koval.trainingplannerbackend.training.model.SportType;

import java.util.List;

/**
 * Built-in {@link ZoneSystem} fallbacks per sport. Used when a training has no
 * {@code zoneSystemId} and the creator has not configured a default — gives
 * {@code matchZone} something to resolve labels like "Z1"/"Z2"/"Z4" against.
 *
 * <p>The returned instances are shared and must be treated as read-only.</p>
 */
public final class ZoneUtils {

    private ZoneUtils() {}

    /** Coggan 7-zone power model — % FTP. */
    private static final ZoneSystem CYCLING_DEFAULT = build(
            SportType.CYCLING, "Default Cycling Zones (% FTP)",
            ZoneReferenceType.FTP, "FTP", "%",
            new Zone("Z1", 0,   55,  "Active Recovery"),
            new Zone("Z2", 56,  75,  "Endurance"),
            new Zone("Z3", 76,  90,  "Tempo"),
            new Zone("Z4", 91,  105, "Threshold"),
            new Zone("Z5", 106, 120, "VO2max"),
            new Zone("Z6", 121, 150, "Anaerobic"),
            new Zone("Z7", 151, 200, "Neuromuscular"));

    /** 5-zone running model — % threshold pace. */
    private static final ZoneSystem RUNNING_DEFAULT = build(
            SportType.RUNNING, "Default Running Zones (% Threshold Pace)",
            ZoneReferenceType.THRESHOLD_PACE, "Threshold Pace", "%",
            new Zone("Z1", 0,   75,  "Easy"),
            new Zone("Z2", 76,  85,  "Endurance"),
            new Zone("Z3", 86,  95,  "Tempo"),
            new Zone("Z4", 96,  105, "Threshold"),
            new Zone("Z5", 106, 130, "VO2max"));

    /** 6-zone swim model — % of CSS speed (higher % = faster than CSS pace). */
    private static final ZoneSystem SWIMMING_DEFAULT = build(
            SportType.SWIMMING, "Default Swim Zones (% CSS)",
            ZoneReferenceType.CSS, "CSS", "%",
            new Zone("Z1", 70,  80,  "Recovery"),
            new Zone("Z2", 81,  88,  "Endurance"),
            new Zone("Z3", 89,  95,  "Tempo"),
            new Zone("Z4", 96,  102, "Threshold"),
            new Zone("Z5", 103, 110, "VO2max"),
            new Zone("Z6", 111, 130, "Sprint"));

    /** Brick uses cycling defaults — bike legs dominate intensity targets. */
    private static final ZoneSystem BRICK_DEFAULT = CYCLING_DEFAULT;

    /**
     * Returns the built-in default zone system for the given sport. Never {@code null};
     * falls back to cycling when {@code sport} is {@code null}.
     */
    public static ZoneSystem getDefaultZoneSystem(SportType sport) {
        if (sport == null) return CYCLING_DEFAULT;
        return switch (sport) {
            case CYCLING  -> CYCLING_DEFAULT;
            case RUNNING  -> RUNNING_DEFAULT;
            case SWIMMING -> SWIMMING_DEFAULT;
            case BRICK    -> BRICK_DEFAULT;
        };
    }

    private static ZoneSystem build(SportType sport, String name,
                                    ZoneReferenceType refType, String refName, String refUnit,
                                    Zone... zones) {
        ZoneSystem zs = new ZoneSystem();
        zs.setName(name);
        zs.setSportType(sport);
        zs.setReferenceType(refType);
        zs.setReferenceName(refName);
        zs.setReferenceUnit(refUnit);
        zs.setZones(List.of(zones));
        return zs;
    }
}
