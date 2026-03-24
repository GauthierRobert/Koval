package com.koval.trainingplannerbackend.plan;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// @RestController //TODO temporary — plans disabled
@RequestMapping("/api/plans")
@CrossOrigin(origins = "*")
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
    public ResponseEntity<TrainingPlan> activatePlan(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(planService.activatePlan(id, userId));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<TrainingPlan> pausePlan(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(planService.pausePlan(id, userId));
    }

    @GetMapping("/{id}/progress")
    public ResponseEntity<PlanProgress> getProgress(@PathVariable String id) {
        return ResponseEntity.ok(planService.getProgress(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlan(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        planService.deletePlan(id, userId);
        return ResponseEntity.noContent().build();
    }
}
