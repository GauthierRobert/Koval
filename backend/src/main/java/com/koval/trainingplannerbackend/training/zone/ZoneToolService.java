package com.koval.trainingplannerbackend.training.zone;

import com.koval.trainingplannerbackend.ai.action.ActionToolTracker;
import com.koval.trainingplannerbackend.auth.SecurityUtils;
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

    /** Creates a new zone system with the given zones and returns the persisted entity. */
    @Tool(description = "Create a zone system defining intensity zones for a sport and reference metric.")
    public ZoneSystem createZoneSystem(
            @ToolParam(description = "Zone system name") String name,
            @ToolParam(description = "CYCLING|RUNNING|SWIMMING|BRICK") SportType sportType,
            @ToolParam(description = "FTP|VO2MAX_POWER|THRESHOLD_PACE|VO2MAX_PACE|CSS|PACE_5K|PACE_10K|PACE_HALF_MARATHON|PACE_MARATHON|CUSTOM") ZoneReferenceType referenceType,
            @ToolParam(description = "Reference name (e.g. 'FTP', 'CSS')") String referenceName,
            @ToolParam(description = "Unit (e.g. 'W', 'sec/km'). Required for CUSTOM.") String referenceUnit,
            @ToolParam(description = "Zones: label, low (%), high (%), description") List<Zone> zones) {

        ActionToolTracker.markCalled();
        String coachId = SecurityUtils.getCurrentUserId();
        ZoneSystem zoneSystem = buildZoneSystem(coachId, name, sportType, referenceType, referenceName, referenceUnit, zones);
        return zoneSystemService.createZoneSystem(zoneSystem);
    }

    /** Lists all zone systems owned by the coach as lightweight summaries. */
    @Tool(description = "List coach's zone systems (summaries).")
    public List<ZoneSystemSummary> listZoneSystems() {
        String coachId = SecurityUtils.getCurrentUserId();
        return zoneSystemService.getZoneSystemsForCoach(coachId).stream()
                .map(ZoneSystemSummary::from).toList();
    }

    /** Returns the coach's default zone system for the given sport, or null if none is set. */
    @Tool(description = "Get default zone system for a sport (null if unset).")
    public ZoneSystem getDefaultZoneSystem(
            @ToolParam(description = "CYCLING|RUNNING|SWIMMING") SportType sportType) {
        String coachId = SecurityUtils.getCurrentUserId();
        return zoneSystemService.getDefaultZoneSystem(coachId, sportType).orElse(null);
    }

    private ZoneSystem buildZoneSystem(String coachId, String name, SportType sportType,
                                       ZoneReferenceType referenceType, String referenceName,
                                       String referenceUnit, List<Zone> zones) {
        ZoneSystem zoneSystem = new ZoneSystem();
        zoneSystem.setCoachId(coachId);
        zoneSystem.setName(name);
        zoneSystem.setSportType(sportType);
        zoneSystem.setReferenceType(referenceType);
        zoneSystem.setReferenceName(referenceName);
        zoneSystem.setReferenceUnit(referenceUnit);
        zoneSystem.setZones(zones);
        return zoneSystem;
    }
}
