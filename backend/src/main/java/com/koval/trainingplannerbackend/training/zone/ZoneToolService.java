package com.koval.trainingplannerbackend.training.zone;

import com.koval.trainingplannerbackend.training.model.SportType;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI-facing tool service for Zone System operations (Coach only).
 */
@Service
public class ZoneToolService {

    private final ZoneSystemService zoneSystemService;

    public ZoneToolService(ZoneSystemService zoneSystemService) {
        this.zoneSystemService = zoneSystemService;
    }

    @Tool(description = """
            Create a new training zone system for the coach. \
            A zone system defines intensity zones (e.g., Z1-Z7) for a specific sport and reference metric. \
            Each zone has a label, low/high bounds (as % of reference), and an optional description. \
            Common examples: Coggan power zones (FTP), running pace zones (Threshold Pace), swimming zones (CSS).""")
    public ZoneSystem createZoneSystem(
            @ToolParam(description = "The coach's user ID") String coachId,
            @ToolParam(description = "Name of the zone system (e.g., 'Coggan Power Zones', '5-Zone Run')") String name,
            @ToolParam(description = "Sport type: CYCLING, RUNNING, SWIMMING, or BRICK") SportType sportType,
            @ToolParam(description = "Reference metric: FTP, VO2MAX_POWER, THRESHOLD_PACE, VO2MAX_PACE, CSS, PACE_5K, PACE_10K, PACE_HALF_MARATHON, PACE_MARATHON, or CUSTOM") ZoneReferenceType referenceType,
            @ToolParam(description = "Human-readable reference name (e.g., 'FTP', 'Threshold Pace', 'CSS')") String referenceName,
            @ToolParam(description = "List of zones, each with label (e.g., 'Z1'), low (% lower bound), high (% upper bound), and description") List<Zone> zones) {

        ZoneSystem zoneSystem = new ZoneSystem();
        zoneSystem.setCoachId(coachId);
        zoneSystem.setName(name);
        zoneSystem.setSportType(sportType);
        zoneSystem.setReferenceType(referenceType);
        zoneSystem.setReferenceName(referenceName);
        zoneSystem.setZones(zones);

        return zoneSystemService.createZoneSystem(zoneSystem);
    }

    @Tool(description = "List all zone systems owned by the coach. Returns full zone definitions.")
    public List<ZoneSystem> listZoneSystems(
            @ToolParam(description = "The coach's user ID") String coachId) {
        return zoneSystemService.getZoneSystemsForCoach(coachId);
    }
}
