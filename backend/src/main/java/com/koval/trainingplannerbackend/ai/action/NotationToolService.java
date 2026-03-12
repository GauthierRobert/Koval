package com.koval.trainingplannerbackend.ai.action;

import com.koval.trainingplannerbackend.club.ClubController;
import com.koval.trainingplannerbackend.club.ClubService;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.model.BrickTraining;
import com.koval.trainingplannerbackend.training.model.CyclingTraining;
import com.koval.trainingplannerbackend.training.model.RunningTraining;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.SwimmingTraining;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.TrainingType;
import com.koval.trainingplannerbackend.training.model.WorkoutBlock;
import com.koval.trainingplannerbackend.training.notation.CompactNotationParser;
import com.koval.trainingplannerbackend.training.zone.ZoneSystem;
import com.koval.trainingplannerbackend.training.zone.ZoneSystemService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AI tool service: converts a compact notation string into a persisted Training,
 * with optional club session creation when a club context is provided.
 */
@Component
public class NotationToolService {

    private final ZoneSystemService zoneSystemService;
    private final TrainingService trainingService;
    private final ClubService clubService;

    public NotationToolService(ZoneSystemService zoneSystemService,
                               TrainingService trainingService,
                               ClubService clubService) {
        this.zoneSystemService = zoneSystemService;
        this.trainingService = trainingService;
        this.clubService = clubService;
    }

    @Tool(description = "Parse compact notation → create Training + optional club session. Call ONCE.")
    public String createTrainingFromNotation(
            @ToolParam(description = "from context") String userId,
            @ToolParam(description = "e.g. 10minWARM + 5x300m85%/R:200m60% + 5minCOOL") String notation,
            @ToolParam(description = "CYCLING|RUNNING|SWIMMING|BRICK") String sport,
            String title,
            @ToolParam(description = "VO2MAX|THRESHOLD|SWEET_SPOT|ENDURANCE|SPRINT|RECOVERY|MIXED|TEST") String type,
            @ToolParam(description = "zone ID or \"null\"") String zoneSystemId,
            @ToolParam(description = "from context or \"null\"") String clubId,
            @ToolParam(description = "from context or \"null\"") String clubGroupId,
            @ToolParam(description = "ISO-8601 or \"null\"") String scheduledAt,
            @ToolParam(description = "from context or \"null\"") String sessionId) {

        // 1. Parse notation → raw blocks
        List<WorkoutBlock> rawBlocks = CompactNotationParser.parse(notation);

        // 2. Resolve zone system
        ZoneSystem zoneSystem = resolveZoneSystem(userId, sport, zoneSystemId);

        // 3. Resolve intensities
        Map<String, Integer> zoneMidpoints = buildZoneMidpointMap(zoneSystem);
        List<WorkoutBlock> resolvedBlocks = rawBlocks.stream()
                .map(block -> resolveBlockIntensity(block, zoneMidpoints))
                .collect(Collectors.toList());

        // 4. Build Training entity
        String resolvedClubId = isPresent(clubId) ? clubId : null;

        Training training = createTrainingInstance(sport);
        training.setTitle(title);
        training.setTrainingType(parseTrainingType(type));
        training.setBlocks(resolvedBlocks);
        training.setGroupIds(new ArrayList<>());
        training.setClubId(resolvedClubId);

        List<String> clubGroupIds = new ArrayList<>();
        if (isPresent(clubGroupId)) clubGroupIds.add(clubGroupId);
        training.setClubGroupIds(clubGroupIds);

        if (zoneSystem != null) {
            training.setZoneSystemId(zoneSystem.getId());
        } else if (isPresent(zoneSystemId)) {
            training.setZoneSystemId(zoneSystemId);
        }

        // 5. Persist
        Training saved = trainingService.createTraining(training, userId);

        // 6. Link to existing session or create a new club session
        if (isPresent(sessionId) && resolvedClubId != null) {
            try {
                clubService.linkTrainingToSession(userId, resolvedClubId, sessionId, saved.getId());
                return "Created '" + saved.getTitle() + "' and linked to existing session [" + sessionId + "]";
            } catch (Exception e) {
                return "Created '" + saved.getTitle() + "' [" + saved.getId() + "] (linking failed: " + e.getMessage() + ")";
            }
        }

        if (resolvedClubId != null) {
            LocalDateTime scheduledDateTime = null;
            if (isPresent(scheduledAt)) {
                try {
                    scheduledDateTime = LocalDateTime.parse(scheduledAt);
                } catch (Exception ignored) {}
            }
            ClubController.CreateSessionRequest sessionReq = new ClubController.CreateSessionRequest(
                    title, sport, scheduledDateTime, null, null, saved.getId(), null, null);
            try {
                var session = clubService.createSession(userId, resolvedClubId, sessionReq);
                return "Created '" + saved.getTitle() + "' with club session [" + session.getId() + "]";
            } catch (Exception e) {
                // Session creation failed (e.g. permission) — training still saved
                return "Created '" + saved.getTitle() + "' [" + saved.getId() + "] (session creation failed: " + e.getMessage() + ")";
            }
        }

        return "Created '" + saved.getTitle() + "' [" + saved.getId() + "]";
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Zone resolution helpers
    // ─────────────────────────────────────────────────────────────────────────

    private ZoneSystem resolveZoneSystem(String userId, String sport, String zoneSystemId) {
        if (isPresent(zoneSystemId)) {
            try {
                return zoneSystemService.getZoneSystem(zoneSystemId);
            } catch (Exception ignored) {}
        }
        try {
            SportType sportType = SportType.valueOf(sport.trim().toUpperCase());
            Optional<ZoneSystem> defaultZs = zoneSystemService.getDefaultZoneSystem(userId, sportType);
            return defaultZs.orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Map<String, Integer> buildZoneMidpointMap(ZoneSystem zoneSystem) {
        if (zoneSystem == null || zoneSystem.getZones() == null) return Map.of();
        return zoneSystem.getZones().stream()
                .filter(z -> z.label() != null)
                .collect(Collectors.toMap(
                        z -> z.label().toUpperCase(),
                        z -> (z.low() + z.high()) / 2,
                        (a, _) -> a));
    }

    private WorkoutBlock resolveBlockIntensity(WorkoutBlock block, Map<String, Integer> zoneMidpoints) {
        if (block.intensityTarget() != null) return block;
        if (block.label() == null) return block;

        String upperLabel = block.label().toUpperCase();
        Integer intensity = zoneMidpoints.get(upperLabel);
        if (intensity == null) intensity = hardcodedFallback(upperLabel);
        if (intensity == null) return block;

        return block.withResolvedIntensity(intensity, null);
    }

    private static Integer hardcodedFallback(String upperLabel) {
        if (upperLabel.equals("FC"))   return 95;
        if (upperLabel.equals("SC"))   return 85;
        if (upperLabel.equals("BC"))   return 80;
        if (upperLabel.equals("E5"))   return 85;
        if (upperLabel.equals("E4"))   return 75;
        if (upperLabel.equals("E3"))   return 70;
        if (upperLabel.equals("E2"))   return 65;
        if (upperLabel.equals("E1") || upperLabel.equals("E")) return 60;
        if (upperLabel.startsWith("WARM")) return 60;
        if (upperLabel.startsWith("COOL")) return 55;
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static boolean isPresent(String s) {
        return s != null && !s.isBlank() && !"null".equalsIgnoreCase(s);
    }

    private static Training createTrainingInstance(String sport) {
        if (sport == null) return new CyclingTraining();
        return switch (sport.trim().toUpperCase()) {
            case "RUNNING"  -> new RunningTraining();
            case "SWIMMING" -> new SwimmingTraining();
            case "BRICK"    -> new BrickTraining();
            default         -> new CyclingTraining();
        };
    }

    private static TrainingType parseTrainingType(String type) {
        if (type == null) return TrainingType.MIXED;
        try {
            return TrainingType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TrainingType.MIXED;
        }
    }
}
