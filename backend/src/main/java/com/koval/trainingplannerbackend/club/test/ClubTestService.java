package com.koval.trainingplannerbackend.club.test;

import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.test.dto.CreateClubTestRequest;
import com.koval.trainingplannerbackend.club.test.dto.UpdateClubTestRequest;
import com.koval.trainingplannerbackend.club.test.formula.ClubTestFormulaEvaluator;
import com.koval.trainingplannerbackend.club.test.formula.TestPresetCatalog;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** CRUD on {@link ClubTest} definitions plus invariant + edit-safety enforcement. */
@Service
public class ClubTestService {

    private final ClubTestRepository testRepository;
    private final ClubTestResultRepository resultRepository;
    private final ClubAuthorizationService clubAuth;
    private final ClubTestFormulaEvaluator formulaEvaluator;
    private final TestPresetCatalog presetCatalog;

    public ClubTestService(ClubTestRepository testRepository,
                           ClubTestResultRepository resultRepository,
                           ClubAuthorizationService clubAuth,
                           ClubTestFormulaEvaluator formulaEvaluator,
                           TestPresetCatalog presetCatalog) {
        this.testRepository = testRepository;
        this.resultRepository = resultRepository;
        this.clubAuth = clubAuth;
        this.formulaEvaluator = formulaEvaluator;
        this.presetCatalog = presetCatalog;
    }

    public ClubTest getById(String testId) {
        return testRepository.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("ClubTest", testId));
    }

    public ClubTest requireInClub(String testId, String clubId) {
        ClubTest t = getById(testId);
        if (!Objects.equals(t.getClubId(), clubId)) {
            throw new ResourceNotFoundException("ClubTest", testId);
        }
        return t;
    }

    public List<ClubTest> listForClub(String userId, String clubId, boolean includeArchived) {
        clubAuth.requireActiveMember(userId, clubId);
        return includeArchived
                ? testRepository.findByClubIdOrderByCreatedAtDesc(clubId)
                : testRepository.findByClubIdAndArchivedFalseOrderByCreatedAtDesc(clubId);
    }

    public ClubTest create(String userId, String clubId, CreateClubTestRequest req) {
        clubAuth.requireAdminOrCoach(userId, clubId);

        List<TestSegment> segments;
        List<ReferenceUpdateRule> rules;

        if (req.presetId() != null && !req.presetId().isBlank()) {
            TestPresetCatalog.PresetInstance pi = presetCatalog.instantiate(req.presetId());
            segments = new ArrayList<>(pi.segments());
            rules = new ArrayList<>(pi.rules());
        } else {
            segments = req.segments() == null ? List.of() : req.segments().stream()
                    .map(ClubTestMapper::fromDto).collect(Collectors.toList());
            rules = req.referenceUpdates() == null ? List.of() : req.referenceUpdates().stream()
                    .map(ClubTestMapper::fromDto).collect(Collectors.toList());
        }
        ensureSegmentIdsAndOrder(segments);
        ensureRuleIds(rules);
        validateDefinition(req.name(), segments, rules,
                req.competitionMode(), req.rankingMetric(), req.rankingTarget(), req.rankingDirection());

        ClubTest t = new ClubTest();
        t.setClubId(clubId);
        t.setName(req.name().trim());
        t.setDescription(req.description());
        t.setCreatedBy(userId);
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(t.getCreatedAt());
        t.setCompetitionMode(req.competitionMode());
        t.setRankingMetric(req.rankingMetric());
        t.setRankingTarget(req.rankingTarget());
        t.setRankingDirection(req.rankingDirection());
        t.setSegments(segments);
        t.setReferenceUpdates(rules);
        t.setArchived(false);
        return testRepository.save(t);
    }

    public ClubTest update(String userId, String clubId, String testId, UpdateClubTestRequest req) {
        clubAuth.requireAdminOrCoach(userId, clubId);
        ClubTest existing = requireInClub(testId, clubId);

        boolean hasResults = resultRepository.countByTestId(testId) > 0;
        List<TestSegment> newSegments = req.segments() == null
                ? existing.getSegments()
                : req.segments().stream().map(ClubTestMapper::fromDto).collect(Collectors.toList());
        List<ReferenceUpdateRule> newRules = req.referenceUpdates() == null
                ? existing.getReferenceUpdates()
                : req.referenceUpdates().stream().map(ClubTestMapper::fromDto).collect(Collectors.toList());
        ensureSegmentIdsAndOrder(newSegments);
        ensureRuleIds(newRules);

        if (hasResults) {
            assertNoDestructiveSegmentChanges(existing.getSegments(), newSegments);
            assertNoDestructiveRuleChanges(existing.getReferenceUpdates(), newRules);
        }

        String name = req.name() == null ? existing.getName() : req.name().trim();
        boolean competitionMode = req.competitionMode() != null ? req.competitionMode() : existing.isCompetitionMode();
        var rankingMetric = req.rankingMetric() != null ? req.rankingMetric() : existing.getRankingMetric();
        var rankingTarget = req.rankingTarget() != null ? req.rankingTarget() : existing.getRankingTarget();
        var rankingDirection = req.rankingDirection() != null ? req.rankingDirection() : existing.getRankingDirection();
        validateDefinition(name, newSegments, newRules, competitionMode, rankingMetric, rankingTarget, rankingDirection);

        existing.setName(name);
        if (req.description() != null) existing.setDescription(req.description());
        existing.setCompetitionMode(competitionMode);
        existing.setRankingMetric(rankingMetric);
        existing.setRankingTarget(rankingTarget);
        existing.setRankingDirection(rankingDirection);
        existing.setSegments(newSegments);
        existing.setReferenceUpdates(newRules);
        existing.setUpdatedAt(LocalDateTime.now());
        return testRepository.save(existing);
    }

    public ClubTest archive(String userId, String clubId, String testId) {
        clubAuth.requireAdminOrCoach(userId, clubId);
        ClubTest t = requireInClub(testId, clubId);
        t.setArchived(true);
        t.setUpdatedAt(LocalDateTime.now());
        return testRepository.save(t);
    }

    public ClubTest setCurrentIteration(String testId, String iterationId) {
        ClubTest t = getById(testId);
        t.setCurrentIterationId(iterationId);
        t.setUpdatedAt(LocalDateTime.now());
        return testRepository.save(t);
    }

    public boolean hasResults(String testId) {
        return resultRepository.countByTestId(testId) > 0;
    }

    // ------------------------------------------------------------------ validation

    private void validateDefinition(String name, List<TestSegment> segments, List<ReferenceUpdateRule> rules,
                                    boolean competitionMode, RankingMetric metric, String rankingTarget,
                                    RankingDirection direction) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Test name is required", "TEST_NAME_REQUIRED");
        }
        if (segments == null || segments.isEmpty()) {
            throw new ValidationException("At least one segment is required", "TEST_SEGMENTS_REQUIRED");
        }
        Set<String> ids = new HashSet<>();
        for (TestSegment s : segments) {
            if (s.getLabel() == null || s.getLabel().isBlank()) {
                throw new ValidationException("Segment label is required", "SEGMENT_LABEL_REQUIRED");
            }
            if (s.getSportType() == null) {
                throw new ValidationException("Segment sport is required", "SEGMENT_SPORT_REQUIRED");
            }
            if (s.getResultUnit() == null) {
                throw new ValidationException("Segment result unit is required", "SEGMENT_UNIT_REQUIRED");
            }
            if (s.getDistanceMeters() == null && s.getDurationSeconds() == null) {
                throw new ValidationException("Segment must have a distance or duration",
                        "SEGMENT_DISTANCE_OR_DURATION_REQUIRED");
            }
            if (!ids.add(s.getId())) {
                throw new ValidationException("Duplicate segment id: " + s.getId(), "SEGMENT_DUPLICATE_ID");
            }
        }
        if (rules != null) {
            Set<String> ruleIds = new HashSet<>();
            for (ReferenceUpdateRule r : rules) {
                if (r.getTarget() == null) {
                    throw new ValidationException("Rule target is required", "RULE_TARGET_REQUIRED");
                }
                if (r.getTarget() == ReferenceTarget.CUSTOM && (r.getCustomKey() == null || r.getCustomKey().isBlank())) {
                    throw new ValidationException("customKey is required for CUSTOM rules", "RULE_CUSTOM_KEY_REQUIRED");
                }
                if (!ruleIds.add(r.getId())) {
                    throw new ValidationException("Duplicate rule id: " + r.getId(), "RULE_DUPLICATE_ID");
                }
                formulaEvaluator.validate(r.getFormulaExpression(), segments);
            }
        }
        if (competitionMode) {
            if (metric == null || direction == null) {
                throw new ValidationException("Competition tests require ranking metric and direction",
                        "RANKING_CONFIG_REQUIRED");
            }
            if (metric == RankingMetric.TIME_OF_SEGMENT) {
                if (rankingTarget == null || segments.stream().noneMatch(s -> rankingTarget.equals(s.getId()))) {
                    throw new ValidationException("rankingTarget must reference an existing segment id",
                            "RANKING_TARGET_INVALID");
                }
            } else if (metric == RankingMetric.COMPUTED_REFERENCE) {
                if (rankingTarget == null || rules == null
                        || rules.stream().noneMatch(r -> rankingTarget.equals(r.getId()))) {
                    throw new ValidationException("rankingTarget must reference an existing rule id",
                            "RANKING_TARGET_INVALID");
                }
            }
        }
    }

    private static void ensureSegmentIdsAndOrder(List<TestSegment> segments) {
        if (segments == null) return;
        for (int i = 0; i < segments.size(); i++) {
            TestSegment s = segments.get(i);
            if (s.getId() == null || s.getId().isBlank()) {
                s.setId(UUID.randomUUID().toString());
            }
            s.setOrder(i);
        }
    }

    private static void ensureRuleIds(List<ReferenceUpdateRule> rules) {
        if (rules == null) return;
        for (ReferenceUpdateRule r : rules) {
            if (r.getId() == null || r.getId().isBlank()) {
                r.setId(UUID.randomUUID().toString());
            }
        }
    }

    private static void assertNoDestructiveSegmentChanges(List<TestSegment> oldSegs, List<TestSegment> newSegs) {
        Map<String, TestSegment> oldById = oldSegs.stream().collect(Collectors.toMap(TestSegment::getId, s -> s));
        Map<String, TestSegment> newById = newSegs.stream().collect(Collectors.toMap(TestSegment::getId, s -> s));
        for (String id : oldById.keySet()) {
            if (!newById.containsKey(id)) {
                throw new ValidationException("Cannot remove segment with id " + id + " — results already exist",
                        "SEGMENT_REMOVE_BLOCKED");
            }
            TestSegment a = oldById.get(id);
            TestSegment b = newById.get(id);
            if (a.getSportType() != b.getSportType()
                    || !Objects.equals(a.getDistanceMeters(), b.getDistanceMeters())
                    || !Objects.equals(a.getDurationSeconds(), b.getDurationSeconds())
                    || a.getResultUnit() != b.getResultUnit()) {
                throw new ValidationException("Cannot change segment shape after results exist (id=" + id + ")",
                        "SEGMENT_SHAPE_LOCKED");
            }
        }
        for (String id : newById.keySet()) {
            if (!oldById.containsKey(id)) {
                throw new ValidationException("Cannot add segment after results exist", "SEGMENT_ADD_BLOCKED");
            }
        }
    }

    private static void assertNoDestructiveRuleChanges(List<ReferenceUpdateRule> oldRules,
                                                        List<ReferenceUpdateRule> newRules) {
        Set<String> oldIds = oldRules.stream().map(ReferenceUpdateRule::getId).collect(Collectors.toSet());
        Set<String> newIds = newRules.stream().map(ReferenceUpdateRule::getId).collect(Collectors.toSet());
        for (String id : oldIds) {
            if (!newIds.contains(id)) {
                throw new ValidationException("Cannot remove reference rule " + id + " after results exist",
                        "RULE_REMOVE_BLOCKED");
            }
        }
        for (String id : newIds) {
            if (!oldIds.contains(id)) {
                throw new ValidationException("Cannot add reference rule after results exist",
                        "RULE_ADD_BLOCKED");
            }
        }
    }

}
