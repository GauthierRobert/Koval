package com.koval.trainingplannerbackend.plan;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/plans")
public class TrainingPlanController {

    private final TrainingPlanService planService;

    public TrainingPlanController(TrainingPlanService planService) {
        this.planService = planService;
    }

    @PostMapping
    public ResponseEntity<TrainingPlan> createPlan(@RequestBody TrainingPlan plan) {
        String userId = SecurityUtils.getCurrentUserId();
        TrainingPlan created = planService.createPlan(plan, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<TrainingPlan>> listPlans() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(planService.listPlans(userId));
    }

    @GetMapping("/active")
    public ResponseEntity<ActivePlanSummary> getActivePlan() {
        String userId = SecurityUtils.getCurrentUserId();
        ActivePlanSummary summary = planService.getActivePlan(userId);
        if (summary == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getPlan(@PathVariable String id,
                                     @RequestParam(defaultValue = "false") boolean populate) {
        if (populate) {
            return ResponseEntity.ok(planService.populatePlan(id));
        }
        return ResponseEntity.ok(planService.getPlan(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TrainingPlan> updatePlan(@PathVariable String id,
                                                   @RequestBody TrainingPlan updates) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(planService.updatePlan(id, updates, userId));
    }

    @PostMapping("/{id}/activate")
    @SuppressWarnings("unchecked")
    public ResponseEntity<TrainingPlan> activatePlan(@PathVariable String id,
                                                      @RequestBody(required = false) Map<String, Object> body) {
        String userId = SecurityUtils.getCurrentUserId();
        LocalDate startDate = null;
        List<String> athleteIds = null;
        if (body != null) {
            if (body.containsKey("startDate")) {
                startDate = LocalDate.parse((String) body.get("startDate"));
            }
            if (body.containsKey("athleteIds")) {
                athleteIds = (List<String>) body.get("athleteIds");
            }
        }
        return ResponseEntity.ok(planService.activatePlan(id, userId, startDate, athleteIds));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<TrainingPlan> pausePlan(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(planService.pausePlan(id, userId));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<TrainingPlan> completePlan(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(planService.completePlan(id, userId));
    }

    @GetMapping("/{id}/progress")
    public ResponseEntity<PlanProgress> getProgress(@PathVariable String id) {
        return ResponseEntity.ok(planService.getProgress(id));
    }

    @GetMapping("/{id}/analytics")
    public ResponseEntity<PlanAnalytics> getAnalytics(@PathVariable String id) {
        return ResponseEntity.ok(planService.getAnalytics(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlan(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        planService.deletePlan(id, userId);
        return ResponseEntity.noContent().build();
    }
}
