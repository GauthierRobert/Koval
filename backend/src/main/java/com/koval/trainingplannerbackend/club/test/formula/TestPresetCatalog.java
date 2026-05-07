package com.koval.trainingplannerbackend.club.test.formula;

import com.koval.trainingplannerbackend.club.test.ReferenceTarget;
import com.koval.trainingplannerbackend.club.test.ReferenceUpdateRule;
import com.koval.trainingplannerbackend.club.test.SegmentResultUnit;
import com.koval.trainingplannerbackend.club.test.TestSegment;
import com.koval.trainingplannerbackend.training.model.SportType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Curated starter formulas. Each preset is a deep-copyable template of segments + rules. */
@Component
public class TestPresetCatalog {

    private final List<TestPreset> presets = List.of(
            swimCssPreset(),
            ftpTwentyMinutePreset(),
            vo2maxFromFiveKPreset(),
            thresholdPaceFromTenKPreset(),
            criticalPowerTwoTestPreset()
    );

    public List<TestPreset> all() {
        return presets;
    }

    public Optional<TestPreset> findById(String id) {
        return presets.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    /** Deep-copies a preset's segments and rules so callers can mutate without touching the catalog. */
    public PresetInstance instantiate(String presetId) {
        TestPreset preset = findById(presetId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown preset id: " + presetId));
        List<TestSegment> segments = preset.segments().stream().map(TestPresetCatalog::copySegment).toList();
        List<ReferenceUpdateRule> rules = preset.referenceUpdates().stream().map(TestPresetCatalog::copyRule).toList();
        return new PresetInstance(preset, segments, rules);
    }

    public record PresetInstance(TestPreset preset, List<TestSegment> segments, List<ReferenceUpdateRule> rules) {}

    // ------------------------------------------------------------------ presets

    private static TestPreset swimCssPreset() {
        TestSegment s200 = segment("swim200", 0, "200m swim", SportType.SWIMMING, 200, null, SegmentResultUnit.SECONDS);
        TestSegment s400 = segment("swim400", 1, "400m swim", SportType.SWIMMING, 400, null, SegmentResultUnit.SECONDS);
        ReferenceUpdateRule css = rule("rule_css", ReferenceTarget.CRITICAL_SWIM_SPEED,
                null, "CSS (sec/100m)", "s/100m",
                "#secondsPer100m(#seg_swim400 - #seg_swim200, 200)", true);
        return new TestPreset("swim-css",
                "CLUB_TESTS.PRESET_SWIM_CSS",
                "CLUB_TESTS.PRESET_SWIM_CSS_DESC",
                List.of(s200, s400),
                List.of(css));
    }

    private static TestPreset ftpTwentyMinutePreset() {
        TestSegment bike20 = segment("bike20", 0, "20-min FTP test", SportType.CYCLING, null, 20 * 60, SegmentResultUnit.WATTS);
        ReferenceUpdateRule ftp = rule("rule_ftp", ReferenceTarget.FTP, null, "FTP", "W",
                "#round(#seg_bike20 * 0.95)", true);
        return new TestPreset("ftp-20min",
                "CLUB_TESTS.PRESET_FTP_20MIN",
                "CLUB_TESTS.PRESET_FTP_20MIN_DESC",
                List.of(bike20),
                List.of(ftp));
    }

    private static TestPreset vo2maxFromFiveKPreset() {
        TestSegment run5k = segment("run5k", 0, "5k time trial", SportType.RUNNING, 5000, null, SegmentResultUnit.SECONDS);
        ReferenceUpdateRule vo2 = rule("rule_vo2pace", ReferenceTarget.VO2MAX_PACE, null, "VO2max pace", "s/km",
                "#round(#secondsPerKm(#seg_run5k, 5000))", true);
        return new TestPreset("vo2-5k",
                "CLUB_TESTS.PRESET_VO2_5K",
                "CLUB_TESTS.PRESET_VO2_5K_DESC",
                List.of(run5k),
                List.of(vo2));
    }

    private static TestPreset thresholdPaceFromTenKPreset() {
        TestSegment run10k = segment("run10k", 0, "10k time trial", SportType.RUNNING, 10000, null, SegmentResultUnit.SECONDS);
        ReferenceUpdateRule ftpRun = rule("rule_threshold_pace", ReferenceTarget.FUNCTIONAL_THRESHOLD_PACE, null,
                "Threshold pace", "s/km",
                "#round(#secondsPerKm(#seg_run10k, 10000) * 1.05)", true);
        return new TestPreset("threshold-10k",
                "CLUB_TESTS.PRESET_THRESHOLD_10K",
                "CLUB_TESTS.PRESET_THRESHOLD_10K_DESC",
                List.of(run10k),
                List.of(ftpRun));
    }

    /** Two segments feed POWER_3MIN/POWER_12MIN; UserService.applyTestReferenceUpdate then re-runs the
     * critical-power model to derive criticalPower and wPrimeJ on the User document. */
    private static TestPreset criticalPowerTwoTestPreset() {
        TestSegment p3 = segment("p3", 0, "3-min all-out", SportType.CYCLING, null, 180, SegmentResultUnit.WATTS);
        TestSegment p12 = segment("p12", 1, "12-min all-out", SportType.CYCLING, null, 720, SegmentResultUnit.WATTS);
        ReferenceUpdateRule p3Rule = rule("rule_p3", ReferenceTarget.POWER_3MIN, null, "3-min power", "W",
                "#round(#seg_p3)", true);
        ReferenceUpdateRule p12Rule = rule("rule_p12", ReferenceTarget.POWER_12MIN, null, "12-min power", "W",
                "#round(#seg_p12)", true);
        return new TestPreset("cp-two-test",
                "CLUB_TESTS.PRESET_CP_TWO_TEST",
                "CLUB_TESTS.PRESET_CP_TWO_TEST_DESC",
                List.of(p3, p12),
                List.of(p3Rule, p12Rule));
    }

    // ------------------------------------------------------------------ helpers

    private static TestSegment segment(String id, int order, String label, SportType sport,
                                       Integer distanceMeters, Integer durationSeconds, SegmentResultUnit unit) {
        TestSegment s = new TestSegment();
        s.setId(id);
        s.setOrder(order);
        s.setLabel(label);
        s.setSportType(sport);
        s.setDistanceMeters(distanceMeters);
        s.setDurationSeconds(durationSeconds);
        s.setResultUnit(unit);
        return s;
    }

    private static ReferenceUpdateRule rule(String id, ReferenceTarget target, String customKey,
                                            String label, String unit, String formula, boolean autoApply) {
        ReferenceUpdateRule r = new ReferenceUpdateRule();
        r.setId(id);
        r.setTarget(target);
        r.setCustomKey(customKey);
        r.setLabel(label);
        r.setUnit(unit);
        r.setFormulaExpression(formula);
        r.setAutoApply(autoApply);
        return r;
    }

    private static TestSegment copySegment(TestSegment src) {
        TestSegment copy = new TestSegment();
        copy.setId(src.getId());
        copy.setOrder(src.getOrder());
        copy.setLabel(src.getLabel());
        copy.setSportType(src.getSportType());
        copy.setDistanceMeters(src.getDistanceMeters());
        copy.setDurationSeconds(src.getDurationSeconds());
        copy.setResultUnit(src.getResultUnit());
        copy.setNotes(src.getNotes());
        return copy;
    }

    private static ReferenceUpdateRule copyRule(ReferenceUpdateRule src) {
        ReferenceUpdateRule copy = new ReferenceUpdateRule();
        copy.setId(src.getId() == null ? UUID.randomUUID().toString() : src.getId());
        copy.setTarget(src.getTarget());
        copy.setCustomKey(src.getCustomKey());
        copy.setLabel(src.getLabel());
        copy.setUnit(src.getUnit());
        copy.setFormulaExpression(src.getFormulaExpression());
        copy.setAutoApply(src.isAutoApply());
        return copy;
    }
}
