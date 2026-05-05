package com.koval.trainingplannerbackend.club.test;

import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMemberRole;
import com.koval.trainingplannerbackend.club.test.dto.RecordResultRequest;
import com.koval.trainingplannerbackend.club.test.dto.SegmentResultValueDto;
import com.koval.trainingplannerbackend.club.test.formula.ClubTestFormulaEvaluator;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** Records athlete results, runs reference formulas, and writes back to {@code User.applyTestReferenceUpdate}. */
@Service
public class ClubTestResultService {

    private final ClubTestResultRepository resultRepository;
    private final ClubTestService testService;
    private final ClubTestIterationService iterationService;
    private final ClubAuthorizationService clubAuth;
    private final ClubTestFormulaEvaluator formulaEvaluator;
    private final UserService userService;

    public ClubTestResultService(ClubTestResultRepository resultRepository,
                                  ClubTestService testService,
                                  ClubTestIterationService iterationService,
                                  ClubAuthorizationService clubAuth,
                                  ClubTestFormulaEvaluator formulaEvaluator,
                                  UserService userService) {
        this.resultRepository = resultRepository;
        this.testService = testService;
        this.iterationService = iterationService;
        this.clubAuth = clubAuth;
        this.formulaEvaluator = formulaEvaluator;
        this.userService = userService;
    }

    public ClubTestResult getById(String resultId) {
        return resultRepository.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("ClubTestResult", resultId));
    }

    public List<ClubTestResult> listForIteration(String userId, String clubId, String testId, String iterationId) {
        ClubMembership membership = clubAuth.requireActiveMember(userId, clubId);
        ClubTest test = testService.requireInClub(testId, clubId);
        ClubTestIteration iteration = iterationService.requireForTest(iterationId, testId);
        List<ClubTestResult> all = resultRepository.findByIterationId(iteration.getId());
        boolean isCoach = isAdminOrCoach(membership);
        if (isCoach || test.isCompetitionMode()) {
            return all;
        }
        return all.stream().filter(r -> Objects.equals(r.getAthleteId(), userId)).toList();
    }

    public long countForIteration(String iterationId) {
        return resultRepository.countByIterationId(iterationId);
    }

    public List<ClubTestResult> listMyHistoryForTest(String userId, String clubId, String testId) {
        clubAuth.requireActiveMember(userId, clubId);
        testService.requireInClub(testId, clubId);
        return resultRepository.findByTestIdAndAthleteIdOrderByCreatedAtDesc(testId, userId);
    }

    public ClubTestResult recordOrUpdate(String userId, String clubId, String testId, String iterationId,
                                          RecordResultRequest req) {
        ClubMembership membership = clubAuth.requireActiveMember(userId, clubId);
        ClubTest test = testService.requireInClub(testId, clubId);
        ClubTestIteration iteration = iterationService.requireForTest(iterationId, testId);
        if (iteration.getStatus() != IterationStatus.OPEN) {
            throw new ValidationException("Iteration is closed; results can no longer be recorded",
                    "ITERATION_CLOSED");
        }

        String targetAthleteId = (req.athleteId() == null || req.athleteId().isBlank())
                ? userId
                : req.athleteId();
        boolean isCoach = isAdminOrCoach(membership);
        if (!Objects.equals(targetAthleteId, userId) && !isCoach) {
            throw new ForbiddenOperationException("Only coaches can record results for other athletes");
        }
        if (req.segmentResults() == null || req.segmentResults().isEmpty()) {
            throw new ValidationException("segmentResults is required", "SEGMENT_RESULTS_REQUIRED");
        }
        // Validate every key references a real segment in the iteration snapshot.
        var validIds = iteration.getSegments().stream().map(TestSegment::getId).collect(Collectors.toSet());
        for (String segId : req.segmentResults().keySet()) {
            if (!validIds.contains(segId)) {
                throw new ValidationException("Unknown segment id in results: " + segId, "SEGMENT_ID_UNKNOWN");
            }
        }

        ClubTestResult result = resultRepository.findByIterationIdAndAthleteId(iteration.getId(), targetAthleteId)
                .orElseGet(() -> {
                    ClubTestResult fresh = new ClubTestResult();
                    fresh.setIterationId(iteration.getId());
                    fresh.setTestId(testId);
                    fresh.setClubId(clubId);
                    fresh.setAthleteId(targetAthleteId);
                    fresh.setCreatedAt(LocalDateTime.now());
                    return fresh;
                });

        Map<String, SegmentResultValue> segMap = new HashMap<>();
        for (Map.Entry<String, SegmentResultValueDto> e : req.segmentResults().entrySet()) {
            segMap.put(e.getKey(), ClubTestMapper.fromDto(e.getValue()));
        }
        result.setSegmentResults(segMap);
        result.setNotes(req.notes());
        result.setRecordedBy(userId);
        result.setUpdatedAt(LocalDateTime.now());

        // Compute reference values from rules (best-effort; rules whose deps are missing yield no entry).
        Map<String, Double> computed = computeReferences(iteration, segMap);
        result.setComputedReferences(computed);

        ClubTestResult saved = resultRepository.save(result);

        if (test.isCompetitionMode()) {
            recomputeRanksForIteration(test, iteration);
            // Re-read the saved row to pick up any rank update written during the recompute.
            return resultRepository.findById(saved.getId()).orElse(saved);
        }
        return saved;
    }

    public ClubTestResult applyReferences(String userId, String clubId, String testId, String iterationId,
                                           String resultId, List<String> ruleIds) {
        ClubMembership membership = clubAuth.requireActiveMember(userId, clubId);
        testService.requireInClub(testId, clubId);
        ClubTestIteration iteration = iterationService.requireForTest(iterationId, testId);
        ClubTestResult result = getById(resultId);
        if (!Objects.equals(result.getIterationId(), iterationId) || !Objects.equals(result.getClubId(), clubId)) {
            throw new ResourceNotFoundException("ClubTestResult", resultId);
        }
        boolean isCoach = isAdminOrCoach(membership);
        if (!Objects.equals(result.getAthleteId(), userId) && !isCoach) {
            throw new ForbiddenOperationException("You can only apply references on your own result");
        }

        Map<String, ReferenceUpdateRule> rulesById = iteration.getReferenceUpdates().stream()
                .collect(Collectors.toMap(ReferenceUpdateRule::getId, r -> r));
        List<String> targetRuleIds = (ruleIds == null || ruleIds.isEmpty())
                ? new ArrayList<>(rulesById.keySet())
                : ruleIds;

        Map<String, Double> segValues = toSegmentValuesMap(result.getSegmentResults());
        for (String ruleId : targetRuleIds) {
            ReferenceUpdateRule rule = rulesById.get(ruleId);
            if (rule == null) continue;
            Optional<Double> value = formulaEvaluator.evaluate(rule.getFormulaExpression(), segValues);
            if (value.isEmpty()) continue;
            int rounded = (int) Math.round(value.get());
            Integer previous = userService.applyTestReferenceUpdate(
                    result.getAthleteId(), rule.getTarget(), rule.getCustomKey(), rounded);
            AppliedReferenceUpdate audit = new AppliedReferenceUpdate();
            audit.setRuleId(ruleId);
            audit.setTarget(rule.getTarget());
            audit.setCustomKey(rule.getCustomKey());
            audit.setPreviousValue(previous);
            audit.setNewValue(rounded);
            audit.setAppliedAt(LocalDateTime.now());
            audit.setAppliedBy(userId);
            result.getAppliedUpdates().add(audit);
        }
        result.setUpdatedAt(LocalDateTime.now());
        return resultRepository.save(result);
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Double> computeReferences(ClubTestIteration iteration,
                                                   Map<String, SegmentResultValue> segValues) {
        Map<String, Double> values = toSegmentValuesMap(segValues);
        Map<String, Double> out = new LinkedHashMap<>();
        for (ReferenceUpdateRule r : iteration.getReferenceUpdates()) {
            formulaEvaluator.evaluate(r.getFormulaExpression(), values)
                    .ifPresent(v -> out.put(r.getId(), v));
        }
        return out;
    }

    private static Map<String, Double> toSegmentValuesMap(Map<String, SegmentResultValue> segResults) {
        if (segResults == null) return Map.of();
        Map<String, Double> out = new HashMap<>();
        segResults.forEach((id, v) -> out.put(id, v == null ? 0.0 : v.getValue()));
        return out;
    }

    private void recomputeRanksForIteration(ClubTest test, ClubTestIteration iteration) {
        List<ClubTestResult> all = resultRepository.findByIterationId(iteration.getId());
        if (all.isEmpty()) return;

        Comparator<ClubTestResult> comparator = buildRankingComparator(test);
        if (comparator == null) {
            // Ranking misconfigured — clear ranks rather than guess.
            for (ClubTestResult r : all) r.setRank(null);
            resultRepository.saveAll(all);
            return;
        }
        all.sort(comparator);

        // Dense rank: equal scores share a rank.
        int rank = 0;
        Double previousScore = null;
        for (int i = 0; i < all.size(); i++) {
            ClubTestResult r = all.get(i);
            Double score = scoreFor(test, r);
            if (score == null) {
                r.setRank(null);
                continue;
            }
            if (previousScore == null || !previousScore.equals(score)) {
                rank = i + 1;
                previousScore = score;
            }
            r.setRank(rank);
        }
        resultRepository.saveAll(all);
    }

    private static Comparator<ClubTestResult> buildRankingComparator(ClubTest test) {
        if (test.getRankingMetric() == null || test.getRankingDirection() == null) return null;
        Comparator<ClubTestResult> base = Comparator.comparing(
                r -> {
                    Double s = scoreFor(test, r);
                    return s == null ? Double.POSITIVE_INFINITY : s;
                },
                Comparator.naturalOrder());
        return test.getRankingDirection() == RankingDirection.ASC ? base : base.reversed();
    }

    private static Double scoreFor(ClubTest test, ClubTestResult r) {
        switch (test.getRankingMetric()) {
            case TIME_OF_SEGMENT -> {
                if (test.getRankingTarget() == null || r.getSegmentResults() == null) return null;
                SegmentResultValue v = r.getSegmentResults().get(test.getRankingTarget());
                return v == null ? null : v.getValue();
            }
            case SUM_OF_TIMES -> {
                if (r.getSegmentResults() == null || r.getSegmentResults().isEmpty()) return null;
                return r.getSegmentResults().values().stream()
                        .filter(Objects::nonNull)
                        .mapToDouble(SegmentResultValue::getValue)
                        .sum();
            }
            case COMPUTED_REFERENCE -> {
                if (test.getRankingTarget() == null || r.getComputedReferences() == null) return null;
                return r.getComputedReferences().get(test.getRankingTarget());
            }
        }
        return null;
    }

    private static boolean isAdminOrCoach(ClubMembership m) {
        return m.getRole() != ClubMemberRole.MEMBER;
    }
}
