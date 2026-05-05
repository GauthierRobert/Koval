package com.koval.trainingplannerbackend.club.test;

import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.test.dto.CreateIterationRequest;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Lifecycle for {@link ClubTestIteration}s. Each iteration freezes a snapshot of the parent test's
 * segments and rules so historic results stay interpretable when the parent definition evolves. */
@Service
public class ClubTestIterationService {

    private final ClubTestIterationRepository iterationRepository;
    private final ClubTestService testService;
    private final ClubAuthorizationService clubAuth;

    public ClubTestIterationService(ClubTestIterationRepository iterationRepository,
                                     ClubTestService testService,
                                     ClubAuthorizationService clubAuth) {
        this.iterationRepository = iterationRepository;
        this.testService = testService;
        this.clubAuth = clubAuth;
    }

    public ClubTestIteration getById(String iterationId) {
        return iterationRepository.findById(iterationId)
                .orElseThrow(() -> new ResourceNotFoundException("ClubTestIteration", iterationId));
    }

    public ClubTestIteration requireForTest(String iterationId, String testId) {
        ClubTestIteration it = getById(iterationId);
        if (!Objects.equals(it.getTestId(), testId)) {
            throw new ResourceNotFoundException("ClubTestIteration", iterationId);
        }
        return it;
    }

    public List<ClubTestIteration> listForTest(String userId, String clubId, String testId) {
        clubAuth.requireActiveMember(userId, clubId);
        testService.requireInClub(testId, clubId);
        return iterationRepository.findByTestIdOrderByCreatedAtDesc(testId);
    }

    public ClubTestIteration start(String userId, String clubId, String testId, CreateIterationRequest req) {
        clubAuth.requireAdminOrCoach(userId, clubId);
        ClubTest test = testService.requireInClub(testId, clubId);
        if (req.label() == null || req.label().isBlank()) {
            throw new ValidationException("Iteration label is required", "ITERATION_LABEL_REQUIRED");
        }
        iterationRepository.findByTestIdAndLabel(testId, req.label().trim()).ifPresent(existing -> {
            throw new ValidationException("Iteration label already used: " + req.label(),
                    "ITERATION_LABEL_DUPLICATE");
        });
        Optional<ClubTestIteration> openCurrent = iterationRepository.findByTestIdAndStatus(testId, IterationStatus.OPEN);
        if (openCurrent.isPresent()) {
            if (req.closeCurrent()) {
                ClubTestIteration current = openCurrent.get();
                current.setStatus(IterationStatus.CLOSED);
                current.setClosedAt(LocalDateTime.now());
                iterationRepository.save(current);
            } else {
                throw new ValidationException("An iteration is already open. Close it first or pass closeCurrent=true.",
                        "ITERATION_ALREADY_OPEN");
            }
        }
        ClubTestIteration it = new ClubTestIteration();
        it.setTestId(testId);
        it.setClubId(clubId);
        it.setLabel(req.label().trim());
        it.setStartDate(req.startDate());
        it.setEndDate(req.endDate());
        it.setStatus(IterationStatus.OPEN);
        it.setCreatedBy(userId);
        it.setCreatedAt(LocalDateTime.now());
        // Freeze the snapshot — independent copies so future edits to the parent don't leak in.
        it.setSegments(deepCopySegments(test.getSegments()));
        it.setReferenceUpdates(deepCopyRules(test.getReferenceUpdates()));
        ClubTestIteration saved = iterationRepository.save(it);
        testService.setCurrentIteration(testId, saved.getId());
        return saved;
    }

    public ClubTestIteration close(String userId, String clubId, String testId, String iterationId) {
        clubAuth.requireAdminOrCoach(userId, clubId);
        ClubTest test = testService.requireInClub(testId, clubId);
        ClubTestIteration it = requireForTest(iterationId, testId);
        if (it.getStatus() == IterationStatus.CLOSED) {
            return it;
        }
        it.setStatus(IterationStatus.CLOSED);
        it.setClosedAt(LocalDateTime.now());
        ClubTestIteration saved = iterationRepository.save(it);
        if (Objects.equals(test.getCurrentIterationId(), iterationId)) {
            testService.setCurrentIteration(testId, null);
        }
        return saved;
    }

    public Optional<ClubTestIteration> findOpenForTest(String testId) {
        return iterationRepository.findByTestIdAndStatus(testId, IterationStatus.OPEN);
    }

    public long countForTest(String testId) {
        return iterationRepository.countByTestId(testId);
    }

    private static List<TestSegment> deepCopySegments(List<TestSegment> src) {
        return src.stream().map(s -> {
            TestSegment c = new TestSegment();
            c.setId(s.getId());
            c.setOrder(s.getOrder());
            c.setLabel(s.getLabel());
            c.setSportType(s.getSportType());
            c.setDistanceMeters(s.getDistanceMeters());
            c.setDurationSeconds(s.getDurationSeconds());
            c.setResultUnit(s.getResultUnit());
            c.setNotes(s.getNotes());
            return c;
        }).toList();
    }

    private static List<ReferenceUpdateRule> deepCopyRules(List<ReferenceUpdateRule> src) {
        return src.stream().map(r -> {
            ReferenceUpdateRule c = new ReferenceUpdateRule();
            c.setId(r.getId());
            c.setTarget(r.getTarget());
            c.setCustomKey(r.getCustomKey());
            c.setLabel(r.getLabel());
            c.setUnit(r.getUnit());
            c.setFormulaExpression(r.getFormulaExpression());
            c.setAutoApply(r.isAutoApply());
            return c;
        }).toList();
    }
}
