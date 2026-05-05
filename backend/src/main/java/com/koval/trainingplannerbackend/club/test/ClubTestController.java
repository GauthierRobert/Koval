package com.koval.trainingplannerbackend.club.test;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.test.dto.ApplyReferencesRequest;
import com.koval.trainingplannerbackend.club.test.dto.ClubTestDetailResponse;
import com.koval.trainingplannerbackend.club.test.dto.ClubTestIterationResponse;
import com.koval.trainingplannerbackend.club.test.dto.ClubTestResultResponse;
import com.koval.trainingplannerbackend.club.test.dto.ClubTestSummaryResponse;
import com.koval.trainingplannerbackend.club.test.dto.CreateClubTestRequest;
import com.koval.trainingplannerbackend.club.test.dto.CreateIterationRequest;
import com.koval.trainingplannerbackend.club.test.dto.RecordResultRequest;
import com.koval.trainingplannerbackend.club.test.dto.TestPresetResponse;
import com.koval.trainingplannerbackend.club.test.dto.UpdateClubTestRequest;
import com.koval.trainingplannerbackend.club.test.formula.TestPresetCatalog;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clubs/{clubId}/tests")
public class ClubTestController {

    private final ClubTestService testService;
    private final ClubTestIterationService iterationService;
    private final ClubTestResultService resultService;
    private final TestPresetCatalog presetCatalog;
    private final UserService userService;
    private final ClubAuthorizationService clubAuth;

    public ClubTestController(ClubTestService testService,
                               ClubTestIterationService iterationService,
                               ClubTestResultService resultService,
                               TestPresetCatalog presetCatalog,
                               UserService userService,
                               ClubAuthorizationService clubAuth) {
        this.testService = testService;
        this.iterationService = iterationService;
        this.resultService = resultService;
        this.presetCatalog = presetCatalog;
        this.userService = userService;
        this.clubAuth = clubAuth;
    }

    @GetMapping("/presets")
    public ResponseEntity<List<TestPresetResponse>> listPresets(@PathVariable String clubId) {
        SecurityUtils.getCurrentUserId(); // require authenticated; presets aren't club-specific data
        return ResponseEntity.ok(presetCatalog.all().stream().map(ClubTestMapper::toDto).toList());
    }

    @PostMapping
    public ResponseEntity<ClubTestDetailResponse> create(@PathVariable String clubId,
                                                          @Valid @RequestBody CreateClubTestRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        ClubTest created = testService.create(userId, clubId, req);
        return ResponseEntity.ok(ClubTestMapper.toDetail(created, false));
    }

    @GetMapping
    public ResponseEntity<List<ClubTestSummaryResponse>> list(@PathVariable String clubId,
                                                                @RequestParam(defaultValue = "false") boolean includeArchived) {
        String userId = SecurityUtils.getCurrentUserId();
        List<ClubTest> tests = testService.listForClub(userId, clubId, includeArchived);
        return ResponseEntity.ok(tests.stream().map(t -> {
            long iterations = iterationService.countForTest(t.getId());
            String currentLabel = t.getCurrentIterationId() == null ? null
                    : iterationService.findOpenForTest(t.getId()).map(ClubTestIteration::getLabel).orElse(null);
            return ClubTestMapper.toSummary(t, iterations, currentLabel);
        }).toList());
    }

    @GetMapping("/{testId}")
    public ResponseEntity<ClubTestDetailResponse> getDetail(@PathVariable String clubId,
                                                              @PathVariable String testId) {
        String userId = SecurityUtils.getCurrentUserId();
        clubAuth.requireActiveMember(userId, clubId);
        ClubTest test = testService.requireInClub(testId, clubId);
        return ResponseEntity.ok(ClubTestMapper.toDetail(test, testService.hasResults(testId)));
    }

    @PutMapping("/{testId}")
    public ResponseEntity<ClubTestDetailResponse> update(@PathVariable String clubId,
                                                          @PathVariable String testId,
                                                          @RequestBody UpdateClubTestRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        ClubTest updated = testService.update(userId, clubId, testId, req);
        return ResponseEntity.ok(ClubTestMapper.toDetail(updated, testService.hasResults(testId)));
    }

    @PostMapping("/{testId}/archive")
    public ResponseEntity<ClubTestDetailResponse> archive(@PathVariable String clubId,
                                                           @PathVariable String testId) {
        String userId = SecurityUtils.getCurrentUserId();
        ClubTest archived = testService.archive(userId, clubId, testId);
        return ResponseEntity.ok(ClubTestMapper.toDetail(archived, testService.hasResults(testId)));
    }

    @GetMapping("/{testId}/iterations")
    public ResponseEntity<List<ClubTestIterationResponse>> listIterations(@PathVariable String clubId,
                                                                           @PathVariable String testId) {
        String userId = SecurityUtils.getCurrentUserId();
        List<ClubTestIteration> iterations = iterationService.listForTest(userId, clubId, testId);
        return ResponseEntity.ok(iterations.stream()
                .map(it -> ClubTestMapper.toDto(it, resultService.countForIteration(it.getId())))
                .toList());
    }

    @PostMapping("/{testId}/iterations")
    public ResponseEntity<ClubTestIterationResponse> startIteration(@PathVariable String clubId,
                                                                     @PathVariable String testId,
                                                                     @Valid @RequestBody CreateIterationRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        ClubTestIteration created = iterationService.start(userId, clubId, testId, req);
        return ResponseEntity.ok(ClubTestMapper.toDto(created, 0));
    }

    @PostMapping("/{testId}/iterations/{iterationId}/close")
    public ResponseEntity<ClubTestIterationResponse> closeIteration(@PathVariable String clubId,
                                                                     @PathVariable String testId,
                                                                     @PathVariable String iterationId) {
        String userId = SecurityUtils.getCurrentUserId();
        ClubTestIteration closed = iterationService.close(userId, clubId, testId, iterationId);
        return ResponseEntity.ok(ClubTestMapper.toDto(closed, resultService.countForIteration(iterationId)));
    }

    @GetMapping("/{testId}/iterations/{iterationId}/results")
    public ResponseEntity<List<ClubTestResultResponse>> listResults(@PathVariable String clubId,
                                                                     @PathVariable String testId,
                                                                     @PathVariable String iterationId) {
        String userId = SecurityUtils.getCurrentUserId();
        List<ClubTestResult> results = resultService.listForIteration(userId, clubId, testId, iterationId);
        return ResponseEntity.ok(toResponseList(results));
    }

    @PostMapping("/{testId}/iterations/{iterationId}/results")
    public ResponseEntity<ClubTestResultResponse> recordResult(@PathVariable String clubId,
                                                                @PathVariable String testId,
                                                                @PathVariable String iterationId,
                                                                @Valid @RequestBody RecordResultRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        ClubTestResult saved = resultService.recordOrUpdate(userId, clubId, testId, iterationId, req);
        return ResponseEntity.ok(toResponse(saved));
    }

    @PostMapping("/{testId}/iterations/{iterationId}/results/{resultId}/apply-references")
    public ResponseEntity<ClubTestResultResponse> applyReferences(@PathVariable String clubId,
                                                                    @PathVariable String testId,
                                                                    @PathVariable String iterationId,
                                                                    @PathVariable String resultId,
                                                                    @RequestBody(required = false) ApplyReferencesRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        List<String> ruleIds = req == null ? null : req.ruleIds();
        ClubTestResult saved = resultService.applyReferences(userId, clubId, testId, iterationId, resultId, ruleIds);
        return ResponseEntity.ok(toResponse(saved));
    }

    @GetMapping("/{testId}/me/history")
    public ResponseEntity<List<ClubTestResultResponse>> myHistory(@PathVariable String clubId,
                                                                    @PathVariable String testId) {
        String userId = SecurityUtils.getCurrentUserId();
        List<ClubTestResult> results = resultService.listMyHistoryForTest(userId, clubId, testId);
        return ResponseEntity.ok(toResponseList(results));
    }

    // ------------------------------------------------------------------ mapping helpers

    private List<ClubTestResultResponse> toResponseList(List<ClubTestResult> results) {
        if (results.isEmpty()) return List.of();
        List<String> athleteIds = results.stream().map(ClubTestResult::getAthleteId).distinct().toList();
        Map<String, User> usersById = new HashMap<>();
        for (User u : userService.findAllById(athleteIds)) usersById.put(u.getId(), u);
        return results.stream()
                .map(r -> ClubTestMapper.toDto(r, usersById.get(r.getAthleteId())))
                .toList();
    }

    private ClubTestResultResponse toResponse(ClubTestResult r) {
        User athlete = userService.findById(r.getAthleteId()).orElse(null);
        return ClubTestMapper.toDto(r, athlete);
    }
}
