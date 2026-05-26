package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import org.springframework.stereotype.Service;

/**
 * Resolves the subject of a role-aware MCP read tool.
 *
 * <p>Many capabilities are useful both to an athlete acting on themselves and to a coach acting
 * on a managed athlete. Rather than expose two near-identical tools per capability, tools take a
 * single optional {@code athleteId} parameter and delegate the gate to this resolver: omit it to
 * act on yourself; pass a coached athlete's id to require the COACH role <em>and</em> a verified
 * coaching relationship.
 */
@Service
public class McpAccessResolver {

    private final CoachService coachService;

    public McpAccessResolver(CoachService coachService) {
        this.coachService = coachService;
    }

    /**
     * Resolve who a read targets. A blank, null, or self {@code athleteId} resolves to the caller.
     * Any other id requires the caller to hold the COACH role and to coach that athlete.
     *
     * @throws ForbiddenOperationException if the caller is not the coach of {@code athleteId}
     */
    public ResolvedSubject resolve(String athleteId) {
        String callerId = SecurityUtils.getCurrentUserId();
        if (athleteId == null || athleteId.isBlank() || athleteId.equals(callerId)) {
            return new ResolvedSubject(callerId, callerId, false);
        }
        SecurityUtils.requireCoach();
        if (!coachService.isCoachOfAthlete(callerId, athleteId)) {
            throw new ForbiddenOperationException(
                    "Not authorized: you are not the coach of this athlete");
        }
        return new ResolvedSubject(callerId, athleteId, true);
    }

    /**
     * Outcome of {@link #resolve(String)}.
     *
     * @param callerId  the authenticated user making the call
     * @param subjectId whose data the tool should read (caller or coached athlete)
     * @param coachView true when the caller is reading a coached athlete (not themselves)
     */
    public record ResolvedSubject(String callerId, String subjectId, boolean coachView) {}
}
