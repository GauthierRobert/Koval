package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.zone.Zone;
import com.koval.trainingplannerbackend.training.zone.ZoneReferenceType;
import com.koval.trainingplannerbackend.training.zone.ZoneSystem;
import com.koval.trainingplannerbackend.training.zone.ZoneSystemService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MCP tool adapter for zone system management.
 * Zone systems define intensity zones (e.g. Z1-Z7) for training prescription.
 */
@Service
public class McpZoneTools {

    private final ZoneSystemService zoneSystemService;

    public McpZoneTools(ZoneSystemService zoneSystemService) {
        this.zoneSystemService = zoneSystemService;
    }

    @Tool(description = "List the user's zone systems. Zone systems define intensity zones (e.g. Z1=55-75%, Z2=75-90%) for a specific sport and reference metric (FTP, CSS, threshold pace, etc.).")
    public List<ZoneSystemSummary> listZoneSystems() {
        String userId = SecurityUtils.getCurrentUserId();
        return zoneSystemService.getZoneSystemsForCoach(userId).stream()
                .map(ZoneSystemSummary::from)
                .toList();
    }

    @Tool(description = "Get the default zone system for a specific sport. Returns null if no default is set.")
    public ZoneSystem getDefaultZoneSystem(
            @ToolParam(description = "Sport type: CYCLING, RUNNING, SWIMMING, or BRICK") SportType sportType) {
        String userId = SecurityUtils.getCurrentUserId();
        return zoneSystemService.getDefaultZoneSystem(userId, sportType).orElse(null);
    }

    @Tool(description = "Get a full zone system by id: name, sport, reference metric, and the ordered list of zones with their label and percentage bounds.")
    public ZoneSystem getZoneSystem(
            @ToolParam(description = "Zone system id") String systemId) {
        String userId = SecurityUtils.getCurrentUserId();
        return zoneSystemService.getZoneSystemWithAccess(systemId, userId);
    }

    @Tool(description = "Delete one of the user's zone systems by id. Cannot be undone.")
    public String deleteZoneSystem(
            @ToolParam(description = "Zone system id to delete") String systemId) {
        String userId = SecurityUtils.getCurrentUserId();
        zoneSystemService.deleteZoneSystem(systemId, userId);
        return "Zone system deleted.";
    }

    @Tool(description = "Given a raw value (e.g. watts for a power zone system, seconds/km for a pace zone system) and a reference value (FTP, threshold pace, CSS), resolve which zone label the value falls in. Returns the zone label, low/high percentage bounds and zone index. Returns 'Error:' if the zone system is not found or the value does not fall in any zone.")
    public String resolveZone(
            @ToolParam(description = "Zone system id") String systemId,
            @ToolParam(description = "Raw value to classify (e.g. 250 for 250W)") double value,
            @ToolParam(description = "Reference value (e.g. FTP in watts)") double referenceValue) {
        if (referenceValue <= 0) return "Error: referenceValue must be > 0.";
        String userId = SecurityUtils.getCurrentUserId();
        ZoneSystem zs = zoneSystemService.getZoneSystemWithAccess(systemId, userId);
        if (zs.getZones() == null || zs.getZones().isEmpty()) return "Error: zone system has no zones.";
        double percent = 100.0 * value / referenceValue;
        for (int i = 0; i < zs.getZones().size(); i++) {
            Zone z = zs.getZones().get(i);
            if (percent >= z.low() && percent <= z.high()) {
                return String.format(java.util.Locale.US,
                        "Zone %d (%s): %d-%d%% of reference — value %.1f = %.1f%%",
                        i + 1, z.label(), z.low(), z.high(), value, percent);
            }
        }
        return String.format(java.util.Locale.US,
                "Error: value %.1f (%.1f%% of reference) does not fall in any zone.", value, percent);
    }

    @Tool(description = "Create a new zone system defining intensity zones for a sport. Each zone has a label, low/high percentage bounds, and optional description. Reference types: FTP, VO2MAX_POWER, THRESHOLD_PACE, VO2MAX_PACE, CSS, PACE_5K, PACE_10K, CUSTOM.")
    public ZoneSystem createZoneSystem(
            @ToolParam(description = "Zone system name (e.g. 'Coggan Power Zones')") String name,
            @ToolParam(description = "Sport: CYCLING, RUNNING, SWIMMING, or BRICK") SportType sportType,
            @ToolParam(description = "Reference metric type") ZoneReferenceType referenceType,
            @ToolParam(description = "Reference display name (e.g. 'FTP', 'CSS')") String referenceName,
            @ToolParam(description = "Unit (e.g. 'W', 'sec/km'). Required for CUSTOM type.") String referenceUnit,
            @ToolParam(description = "List of zones with label, low (%), high (%), and description") List<Zone> zones) {
        String userId = SecurityUtils.getCurrentUserId();
        ZoneSystem zs = new ZoneSystem();
        zs.setCoachId(userId);
        zs.setName(name);
        zs.setSportType(sportType);
        zs.setReferenceType(referenceType);
        zs.setReferenceName(referenceName);
        zs.setReferenceUnit(referenceUnit);
        zs.setZones(zones);
        return zoneSystemService.createZoneSystem(zs);
    }

    public record ZoneSystemSummary(String id, String name, String sportType, String referenceType,
                                     int zoneCount) {
        public static ZoneSystemSummary from(ZoneSystem zs) {
            return new ZoneSystemSummary(
                    zs.getId(), zs.getName(),
                    zs.getSportType() != null ? zs.getSportType().name() : null,
                    zs.getReferenceType() != null ? zs.getReferenceType().name() : null,
                    zs.getZones() != null ? zs.getZones().size() : 0);
        }
    }
}
