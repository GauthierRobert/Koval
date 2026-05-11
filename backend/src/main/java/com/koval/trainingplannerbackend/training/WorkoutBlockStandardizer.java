package com.koval.trainingplannerbackend.training;

import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.WorkoutElement;
import com.koval.trainingplannerbackend.training.zone.Zone;
import com.koval.trainingplannerbackend.training.zone.ZoneSystem;
import com.koval.trainingplannerbackend.training.zone.ZoneSystemService;
import com.koval.trainingplannerbackend.training.zone.ZoneUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Normalizes workout blocks coming from external sources (AI, imports) into the
 * canonical form expected by the rest of the system:
 *
 * <ul>
 *   <li>Promotes blocks with both start and end intensities to {@link BlockType#RAMP}.</li>
 *   <li>Resolves blocks with no intensity via zone matching against the training's
 *       zone system; falls back to {@link BlockType#PAUSE} when no zone matches.</li>
 *   <li>Preserves {@link BlockType#TRANSITION} blocks (intentional no-intensity).</li>
 * </ul>
 *
 * <p>Extracted from {@code TrainingService} so the CRUD service remains a thin
 * orchestrator over persistence.
 */
@Component
public class WorkoutBlockStandardizer {

    private static final Map<String, Pattern> TOKEN_PATTERNS = new ConcurrentHashMap<>();

    private final ZoneSystemService zoneSystemService;

    public WorkoutBlockStandardizer(ZoneSystemService zoneSystemService) {
        this.zoneSystemService = zoneSystemService;
    }

    /**
     * Resolves the zone system attached to a training: the custom one referenced
     * by {@code zoneSystemId}, otherwise the creator's default for the sport, otherwise
     * the built-in fallback from {@link ZoneUtils}. Always non-null.
     */
    public ZoneSystem resolveZoneSystem(Training training, String userId) {
        if (training.getZoneSystemId() != null && !training.getZoneSystemId().isBlank()) {
            try {
                return zoneSystemService.getZoneSystem(training.getZoneSystemId());
            } catch (Exception ignored) {
                // fall through to default
            }
        }
        SportType sport = Optional.ofNullable(training.getSportType()).orElse(SportType.CYCLING);
        String createdBy = Optional.ofNullable(training.getCreatedBy()).orElse(userId);
        return zoneSystemService.getDefaultZoneSystem(createdBy, sport)
                .orElseGet(() -> ZoneUtils.getDefaultZoneSystem(sport));
    }

    /**
     * Standardizes every block in {@code blocks} (recursing into sets) against {@code zoneSystem}.
     */
    public List<WorkoutElement> standardize(List<WorkoutElement> blocks, ZoneSystem zoneSystem) {
        return blocks.stream().map(b -> standardizeBlock(b, zoneSystem)).toList();
    }

    /**
     * Because AI can be wrong — recurses into sets. When a leaf block has no
     * intensity, attempts to resolve it from a known zone (matched against the
     * training's custom zone system or the creator's default for the sport)
     * via {@code zoneTarget} or {@code label}; falls back to {@link BlockType#PAUSE}
     * if no zone matches.
     */
    public WorkoutElement standardizeBlock(WorkoutElement element, ZoneSystem zoneSystem) {
        if (element.isSet()) {
            List<WorkoutElement> standardizedChildren = element.elements().stream()
                    .map(child -> standardizeBlock(child, zoneSystem))
                    .toList();
            return element.withElements(standardizedChildren);
        }

        if (isPositive(element.intensityEnd()) && isPositive(element.intensityStart())) {
            return element.updateType(BlockType.RAMP);
        }

        // Preserve TRANSITION blocks — they intentionally have no intensity
        if (element.type() == BlockType.TRANSITION) {
            return element;
        }

        if (!isPositive(element.intensityEnd()) && !isPositive(element.intensityStart())
                && !isPositive(element.intensityTarget())) {
            Zone matched = matchZone(element, zoneSystem);
            if (matched != null) {
                int midpoint = (matched.low() + matched.high()) / 2;
                return element.withResolvedIntensity(midpoint, formatZoneDisplayLabel(matched));
            }
            // Unmatched zoneTarget is preserved for later enrichment (zone system may
            // change, or be absent at create time).
            if (element.zoneTarget() != null && !element.zoneTarget().isBlank()) {
                return element;
            }
            return element.updateType(BlockType.PAUSE);
        }
        return element;
    }

    private static boolean isPositive(Integer val) {
        return val != null && val > 0;
    }

    /**
     * Tries to find a zone in {@code zoneSystem} matching the element's
     * {@code zoneTarget} (first) or {@code label} (fallback). Exact matches win
     * over token-contains matches (e.g. "Z4" inside "Z4 Threshold").
     */
    private static Zone matchZone(WorkoutElement element, ZoneSystem zoneSystem) {
        if (zoneSystem == null || zoneSystem.getZones() == null || zoneSystem.getZones().isEmpty()) {
            return null;
        }
        Zone matched = matchZoneInText(element.zoneTarget(), zoneSystem);
        if (matched != null) return matched;
        return matchZoneInText(element.label(), zoneSystem);
    }

    private static Zone matchZoneInText(String text, ZoneSystem zoneSystem) {
        if (text == null || text.isBlank()) return null;
        String upper = text.trim().toUpperCase();
        List<Zone> labeled = zoneSystem.getZones().stream()
                .filter(z -> z.label() != null && !z.label().isBlank())
                .toList();
        return labeled.stream()
                .filter(z -> upper.equals(z.label().trim().toUpperCase()))
                .findFirst()
                .or(() -> labeled.stream()
                        .filter(z -> containsAsToken(upper, z.label().trim().toUpperCase()))
                        .findFirst())
                .orElse(null);
    }

    /** Word-boundary contains: "Z4" matches "Z4 Threshold" but not "Z40". */
    private static boolean containsAsToken(String text, String token) {
        Pattern pattern = TOKEN_PATTERNS.computeIfAbsent(token,
                t -> Pattern.compile("(?:^|[^A-Z0-9])" + Pattern.quote(t) + "(?:[^A-Z0-9]|$)"));
        return pattern.matcher(text).find();
    }

    private static String formatZoneDisplayLabel(Zone z) {
        String desc = Optional.ofNullable(z.description())
                .filter(d -> !d.isBlank())
                .map(d -> " - " + d)
                .orElse("");
        return z.label() + desc + " (" + z.low() + "-" + z.high() + "%)";
    }
}
