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
