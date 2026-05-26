package com.koval.trainingplannerbackend.context;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.config.Provenance;
import com.koval.trainingplannerbackend.context.ContextService.CoachAthleteContext;
import com.koval.trainingplannerbackend.context.ContextService.ContextEntry;
import com.koval.trainingplannerbackend.context.ContextService.MyContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ContextController {

    private final ContextService contextService;

    public ContextController(ContextService contextService) {
        this.contextService = contextService;
    }

    @GetMapping("/context/me")
    public MyContext getMyContext() {
        return contextService.getMyContext(SecurityUtils.getCurrentUserId());
    }

    @PutMapping("/context/me")
    public MyContext updateMyContext(@RequestBody UpdateContextRequest request) {
        return contextService.upsertMyContext(
                SecurityUtils.getCurrentUserId(), request.sections(), Provenance.web());
    }

    /** The caller's athlete self-context. Available to any role — a coach is also an athlete. */
    @GetMapping("/context/me/athlete")
    public MyContext getMyAthleteContext() {
        return contextService.getMyAthleteContext(SecurityUtils.getCurrentUserId());
    }

    @PutMapping("/context/me/athlete")
    public MyContext updateMyAthleteContext(@RequestBody UpdateContextRequest request) {
        return contextService.upsertMyAthleteContext(
                SecurityUtils.getCurrentUserId(), request.sections(), Provenance.web());
    }

    /** The caller's coaching philosophy. Coaches only. */
    @GetMapping("/context/me/coach")
    public MyContext getMyCoachContext() {
        SecurityUtils.requireCoach();
        return contextService.getMyCoachContext(SecurityUtils.getCurrentUserId());
    }

    @PutMapping("/context/me/coach")
    public MyContext updateMyCoachContext(@RequestBody UpdateContextRequest request) {
        SecurityUtils.requireCoach();
        return contextService.upsertMyCoachContext(
                SecurityUtils.getCurrentUserId(), request.sections(), Provenance.web());
    }

    @GetMapping("/coach/athletes/{athleteId}/context")
    public CoachAthleteContext getAthleteContext(@PathVariable String athleteId) {
        SecurityUtils.requireCoach();
        return contextService.getCoachViewOfAthlete(SecurityUtils.getCurrentUserId(), athleteId);
    }

    @PutMapping("/coach/athletes/{athleteId}/context")
    public ContextEntry updateAthleteContext(@PathVariable String athleteId,
                                             @RequestBody UpdateContextRequest request) {
        SecurityUtils.requireCoach();
        AthleteContext saved = contextService.upsertCoachAthleteContext(
                SecurityUtils.getCurrentUserId(), athleteId, request.sections(), Provenance.web());
        return ContextEntry.from(saved);
    }

    public record UpdateContextRequest(Map<String, String> sections) {}
}
