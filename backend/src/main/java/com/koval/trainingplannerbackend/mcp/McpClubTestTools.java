package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.club.test.ClubTest;
import com.koval.trainingplannerbackend.club.test.ClubTestIteration;
import com.koval.trainingplannerbackend.club.test.ClubTestIterationService;
import com.koval.trainingplannerbackend.club.test.ClubTestResult;
import com.koval.trainingplannerbackend.club.test.ClubTestResultService;
import com.koval.trainingplannerbackend.club.test.ClubTestService;
import com.koval.trainingplannerbackend.club.test.IterationStatus;
import com.koval.trainingplannerbackend.club.test.ReferenceTarget;
import com.koval.trainingplannerbackend.club.test.SegmentResultUnit;
import com.koval.trainingplannerbackend.club.test.dto.CreateClubTestRequest;
import com.koval.trainingplannerbackend.club.test.dto.CreateIterationRequest;
import com.koval.trainingplannerbackend.club.test.dto.RecordResultRequest;
import com.koval.trainingplannerbackend.club.test.dto.ReferenceUpdateRuleDto;
import com.koval.trainingplannerbackend.club.test.dto.SegmentResultValueDto;
import com.koval.trainingplannerbackend.club.test.dto.TestSegmentDto;
import com.koval.trainingplannerbackend.training.model.SportType;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** MCP tool adapter for club test management. */
@Service
public class McpClubTestTools {

    private final ClubTestService testService;
    private final ClubTestIterationService iterationService;
    private final ClubTestResultService resultService;

    public McpClubTestTools(ClubTestService testService,
                             ClubTestIterationService iterationService,
                             ClubTestResultService resultService) {
        this.testService = testService;
        this.iterationService = iterationService;
        this.resultService = resultService;
    }

    @Tool(description = "Create a club test from a preset (e.g. 'swim-css', 'ftp-20min', 'vo2-5k', 'threshold-10k', 'cp-two-test'). Coach role required.")
    public Object createClubTestFromPreset(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Test name to display") String name,
            @ToolParam(description = "Preset id: swim-css | ftp-20min | vo2-5k | threshold-10k | cp-two-test") String presetId,
            @ToolParam(description = "Description (optional)") String description,
            @ToolParam(description = "Competition mode (rank athletes, all members can see)") boolean competitionMode) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";
        if (name == null || name.isBlank()) return "Error: name is required.";
        if (presetId == null || presetId.isBlank()) return "Error: presetId is required.";
        String userId = SecurityUtils.getCurrentUserId();
        CreateClubTestRequest req = new CreateClubTestRequest(
                name, description, competitionMode, null, null, null, null, null, presetId);
        ClubTest created = testService.create(userId, clubId, req);
        return ClubTestSummary.from(created);
    }

    @Tool(description = "List club tests for a club. Returns id, name, segment count, current iteration label.")
    public Object listClubTests(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Include archived tests") boolean includeArchived) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";
        String userId = SecurityUtils.getCurrentUserId();
        return testService.listForClub(userId, clubId, includeArchived).stream()
                .map(ClubTestSummary::from).toList();
    }

    @Tool(description = "Start a new iteration of a club test (e.g. label='2026'). Optionally closes any existing open iteration. Coach role required.")
    public Object startTestIteration(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Test ID") String testId,
            @ToolParam(description = "Iteration label, e.g. '2026'") String label,
            @ToolParam(description = "Start date YYYY-MM-DD (optional)") LocalDate startDate,
            @ToolParam(description = "Close any currently open iteration first") boolean closeCurrent) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";
        if (testId == null || testId.isBlank()) return "Error: testId is required.";
        if (label == null || label.isBlank()) return "Error: label is required.";
        String userId = SecurityUtils.getCurrentUserId();
        CreateIterationRequest req = new CreateIterationRequest(label, startDate, null, closeCurrent);
        ClubTestIteration created = iterationService.start(userId, clubId, testId, req);
        return IterationSummary.from(created);
    }

    @Tool(description = "Record a test result for the current user (or for an athlete the coach manages). Provide values keyed by segment id.")
    public Object recordTestResult(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Test ID") String testId,
            @ToolParam(description = "Iteration ID") String iterationId,
            @ToolParam(description = "Athlete user id (omit to record for current user)") String athleteId,
            @ToolParam(description = "Map of segmentId → numeric value (seconds for time, watts for power)") Map<String, Double> segmentValues,
            @ToolParam(description = "Notes (optional)") String notes) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";
        if (testId == null || testId.isBlank()) return "Error: testId is required.";
        if (iterationId == null || iterationId.isBlank()) return "Error: iterationId is required.";
        if (segmentValues == null || segmentValues.isEmpty()) return "Error: segmentValues is required.";
        String userId = SecurityUtils.getCurrentUserId();
        ClubTestIteration iteration = iterationService.requireForTest(iterationId, testId);
        Map<String, SegmentResultValueDto> segDtos = new HashMap<>();
        for (Map.Entry<String, Double> e : segmentValues.entrySet()) {
            SegmentResultUnit unit = iteration.getSegments().stream()
                    .filter(s -> s.getId().equals(e.getKey()))
                    .findFirst().map(s -> s.getResultUnit()).orElse(SegmentResultUnit.SECONDS);
            segDtos.put(e.getKey(), new SegmentResultValueDto(e.getValue(), unit, null));
        }
        RecordResultRequest req = new RecordResultRequest(athleteId, segDtos, notes);
        ClubTestResult saved = resultService.recordOrUpdate(userId, clubId, testId, iterationId, req);
        return TestResultSummary.from(saved);
    }

    @Tool(description = "Apply the test's reference value formulas to the athlete's profile (FTP, CSS, etc.). Returns the updated result with the audit log of writes.")
    public Object applyTestReferences(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Test ID") String testId,
            @ToolParam(description = "Iteration ID") String iterationId,
            @ToolParam(description = "Result ID") String resultId,
            @ToolParam(description = "Optional rule IDs subset (omit to apply all computable rules)") List<String> ruleIds) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";
        String userId = SecurityUtils.getCurrentUserId();
        ClubTestResult saved = resultService.applyReferences(userId, clubId, testId, iterationId, resultId, ruleIds);
        return TestResultSummary.from(saved);
    }

    @Tool(description = "Return the current user's history for one club test (all iterations, ordered most-recent first).")
    public Object getMyTestHistory(
            @ToolParam(description = "Club ID") String clubId,
            @ToolParam(description = "Test ID") String testId) {
        if (clubId == null || clubId.isBlank()) return "Error: clubId is required.";
        if (testId == null || testId.isBlank()) return "Error: testId is required.";
        String userId = SecurityUtils.getCurrentUserId();
        return resultService.listMyHistoryForTest(userId, clubId, testId).stream()
                .map(TestResultSummary::from).toList();
    }

    // ------------------------------------------------------------------ summary records

    public record ClubTestSummary(String id, String name, boolean competitionMode, int segmentCount,
                                    int ruleCount, String currentIterationId) {
        static ClubTestSummary from(ClubTest t) {
            return new ClubTestSummary(t.getId(), t.getName(), t.isCompetitionMode(),
                    t.getSegments().size(), t.getReferenceUpdates().size(), t.getCurrentIterationId());
        }
    }

    public record IterationSummary(String id, String testId, String label, IterationStatus status,
                                     List<TestSegmentDto> segments, List<ReferenceUpdateRuleDto> rules) {
        static IterationSummary from(ClubTestIteration it) {
            List<TestSegmentDto> segs = it.getSegments().stream()
                    .map(s -> new TestSegmentDto(s.getId(), s.getOrder(), s.getLabel(),
                            s.getSportType() != null ? s.getSportType() : SportType.CYCLING,
                            s.getDistanceMeters(), s.getDurationSeconds(), s.getResultUnit(), s.getNotes()))
                    .toList();
            List<ReferenceUpdateRuleDto> rules = it.getReferenceUpdates().stream()
                    .map(r -> new ReferenceUpdateRuleDto(r.getId(), r.getTarget(), r.getCustomKey(),
                            r.getLabel(), r.getUnit(), r.getFormulaExpression(), r.isAutoApply()))
                    .toList();
            return new IterationSummary(it.getId(), it.getTestId(), it.getLabel(), it.getStatus(), segs, rules);
        }
    }

    public record TestResultSummary(String id, String iterationId, String athleteId,
                                      Map<String, Double> segmentValues, Map<String, Double> computedReferences,
                                      Integer rank, List<AppliedSummary> appliedUpdates) {
        static TestResultSummary from(ClubTestResult r) {
            Map<String, Double> segs = new HashMap<>();
            if (r.getSegmentResults() != null) {
                r.getSegmentResults().forEach((k, v) -> segs.put(k, v == null ? 0.0 : v.getValue()));
            }
            List<AppliedSummary> applied = r.getAppliedUpdates() == null ? List.of() :
                    r.getAppliedUpdates().stream().map(a -> new AppliedSummary(
                            a.getRuleId(), a.getTarget(), a.getCustomKey(),
                            a.getPreviousValue(), a.getNewValue())).toList();
            return new TestResultSummary(r.getId(), r.getIterationId(), r.getAthleteId(),
                    segs, r.getComputedReferences(), r.getRank(), applied);
        }
    }

    public record AppliedSummary(String ruleId, ReferenceTarget target, String customKey,
                                   Integer previousValue, Integer newValue) {}
}
