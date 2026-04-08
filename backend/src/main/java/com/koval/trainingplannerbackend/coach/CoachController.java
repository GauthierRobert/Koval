package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.coach.dto.AthleteResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/coach")
public class CoachController {

    private final CoachService coachService;
    private final CoachGroupService coachGroupService;
    private final AthleteImportService athleteImportService;

    public CoachController(CoachService coachService,
                           CoachGroupService coachGroupService,
                           AthleteImportService athleteImportService) {
        this.coachService = coachService;
        this.coachGroupService = coachGroupService;
        this.athleteImportService = athleteImportService;
    }

    public record AssignmentRequest(
            String trainingId,
            List<String> athleteIds,
            LocalDate scheduledDate,
            String notes,
            Integer tss,
            Double intensityFactor,
            String clubId,
            String groupId
    ) {}

    @PostMapping("/assign")
    public ResponseEntity<List<ScheduledWorkout>> assignTraining(
            @RequestBody AssignmentRequest request) {
        String coachId = SecurityUtils.getCurrentUserId();
        List<ScheduledWorkout> assignments;
        if (request.clubId() != null && !request.clubId().isBlank()) {
            assignments = coachService.assignTrainingFromClub(
                    coachId,
                    request.trainingId(),
                    request.athleteIds(),
                    request.scheduledDate(),
                    request.notes(),
                    request.clubId());
        } else {
            assignments = coachService.assignTraining(
                    coachId,
                    request.trainingId(),
                    request.athleteIds(),
                    request.scheduledDate(),
                    request.notes(),
                    request.groupId());
        }
        return ResponseEntity.ok(assignments);
    }

    @DeleteMapping("/assign/{id}")
    public ResponseEntity<Void> unassignTraining(@PathVariable String id) {
        coachService.unassignTraining(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/athletes")
    public ResponseEntity<List<AthleteResponse>> getAthletes() {
        String coachId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(coachService.getAthletes(coachId));
    }

    @GetMapping(value = "/athletes", params = "page")
    public ResponseEntity<Page<AthleteResponse>> getAthletes(Pageable pageable) {
        String coachId = SecurityUtils.getCurrentUserId();
        List<AthleteResponse> all = coachService.getAthletes(coachId);

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), all.size());
        List<AthleteResponse> pageContent = start >= all.size() ? List.of() : all.subList(start, end);
        return ResponseEntity.ok(new PageImpl<>(pageContent, pageable, all.size()));
    }

    @PostMapping("/athletes/import")
    public ResponseEntity<AthleteImportService.ImportResult> importAthletes(
            @RequestParam("file") MultipartFile file) {
        String coachId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(athleteImportService.importFromCsv(file, coachId));
    }

    @DeleteMapping("/athletes/{athleteId}")
    public ResponseEntity<Void> removeAthlete(@PathVariable String athleteId) {
        String coachId = SecurityUtils.getCurrentUserId();
        coachGroupService.removeAthlete(coachId, athleteId);
        return ResponseEntity.noContent().build();
    }
}
