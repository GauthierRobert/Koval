package com.koval.trainingplannerbackend.context;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRole;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.config.Provenance;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reads and upserts athlete/coach context. Enforces the privacy rule that coach-authored
 * context about an athlete is only ever returned to that coach — never to the athlete and
 * never to other coaches.
 */
@Service
public class ContextService {

    static final int MAX_SECTIONS = 30;
    static final int MAX_TITLE_LENGTH = 120;
    static final int MAX_SECTION_LENGTH = 10_000;

    private final AthleteContextRepository athleteRepo;
    private final CoachContextRepository coachRepo;
    private final CoachService coachService;
    private final UserService userService;

    public ContextService(AthleteContextRepository athleteRepo,
                          CoachContextRepository coachRepo,
                          CoachService coachService,
                          UserService userService) {
        this.athleteRepo = athleteRepo;
        this.coachRepo = coachRepo;
        this.coachService = coachService;
        this.userService = userService;
    }

    /**
     * The caller's own context: a coach gets their coaching philosophy, an athlete gets their
     * self-context. {@code sections} is null when nothing has been written yet.
     */
    public MyContext getMyContext(String userId) {
        User user = userService.getUserById(userId);
        return user.getRole() == UserRole.COACH
                ? getMyCoachContext(userId)
                : getMyAthleteContext(userId);
    }

    /**
     * The caller's own athlete self-context, regardless of role. Everyone has one — a coach is
     * also an athlete who trains themselves — so this never gates on role.
     */
    public MyContext getMyAthleteContext(String userId) {
        return athleteRepo.findByAthleteIdAndAuthorId(userId, userId)
                .map(MyContext::athlete)
                .orElseGet(() -> MyContext.empty(UserRole.ATHLETE));
    }

    /** The caller's own coaching philosophy. */
    public MyContext getMyCoachContext(String userId) {
        return coachRepo.findByCoachId(userId)
                .map(MyContext::coach)
                .orElseGet(() -> MyContext.empty(UserRole.COACH));
    }

    /** Role-aware upsert of the caller's own context (coach philosophy or athlete self-context). */
    public MyContext upsertMyContext(String userId, Map<String, String> sections, Provenance provenance) {
        validate(sections); // fail fast on bad input before resolving the user
        User user = userService.getUserById(userId);
        return user.getRole() == UserRole.COACH
                ? upsertMyCoachContext(userId, sections, provenance)
                : upsertMyAthleteContext(userId, sections, provenance);
    }

    /** Upsert the caller's own athlete self-context, regardless of role. */
    public MyContext upsertMyAthleteContext(String userId, Map<String, String> sections, Provenance provenance) {
        validate(sections);
        LocalDateTime now = LocalDateTime.now();
        Provenance prov = provenance != null ? provenance : Provenance.web();

        AthleteContext a = athleteRepo.findByAthleteIdAndAuthorId(userId, userId)
                .orElseGet(AthleteContext::new);
        if (a.getId() == null) {
            a.setAthleteId(userId);
            a.setAuthorId(userId);
            a.setAuthorRole(ContextAuthorRole.ATHLETE);
            a.setCreatedAt(now);
        }
        a.setSections(new LinkedHashMap<>(sections));
        a.setProvenance(prov);
        a.setUpdatedAt(now);
        return MyContext.athlete(athleteRepo.save(a));
    }

    /** Upsert the caller's own coaching philosophy. */
    public MyContext upsertMyCoachContext(String userId, Map<String, String> sections, Provenance provenance) {
        validate(sections);
        LocalDateTime now = LocalDateTime.now();
        Provenance prov = provenance != null ? provenance : Provenance.web();

        CoachContext c = coachRepo.findByCoachId(userId).orElseGet(CoachContext::new);
        if (c.getId() == null) {
            c.setCoachId(userId);
            c.setCreatedAt(now);
        }
        c.setSections(new LinkedHashMap<>(sections));
        c.setProvenance(prov);
        c.setUpdatedAt(now);
        return MyContext.coach(coachRepo.save(c));
    }

    /** The athlete's own self-authored context, if any. Coach-authored entries are excluded. */
    public Optional<AthleteContext> getAthleteSelfContext(String athleteId) {
        return athleteRepo.findByAthleteIdAndAuthorId(athleteId, athleteId);
    }

    /** A coach's coaching philosophy, if any. */
    public Optional<CoachContext> getCoachPhilosophy(String coachId) {
        return coachRepo.findByCoachId(coachId);
    }

    /**
     * The coach's view of an athlete: the athlete's self-context (read-only) plus the coach's
     * own private context about them. Never includes other coaches' entries. Requires the
     * caller to coach the athlete.
     */
    public CoachAthleteContext getCoachViewOfAthlete(String coachId, String athleteId) {
        requireCoachOf(coachId, athleteId);
        ContextEntry athleteSelf = athleteRepo.findByAthleteIdAndAuthorId(athleteId, athleteId)
                .map(ContextEntry::from).orElse(null);
        ContextEntry coachEntry = athleteRepo.findByAthleteIdAndAuthorId(athleteId, coachId)
                .map(ContextEntry::from).orElse(null);
        return new CoachAthleteContext(athleteSelf, coachEntry);
    }

    /** Upsert a coach's private context about an athlete. Requires the caller to coach them. */
    public AthleteContext upsertCoachAthleteContext(String coachId, String athleteId,
                                                    Map<String, String> sections, Provenance provenance) {
        validate(sections);
        requireCoachOf(coachId, athleteId);
        LocalDateTime now = LocalDateTime.now();
        AthleteContext a = athleteRepo.findByAthleteIdAndAuthorId(athleteId, coachId)
                .orElseGet(AthleteContext::new);
        if (a.getId() == null) {
            a.setAthleteId(athleteId);
            a.setAuthorId(coachId);
            a.setAuthorRole(ContextAuthorRole.COACH);
            a.setCreatedAt(now);
        }
        a.setSections(new LinkedHashMap<>(sections));
        a.setProvenance(provenance != null ? provenance : Provenance.web());
        a.setUpdatedAt(now);
        return athleteRepo.save(a);
    }

    private void requireCoachOf(String coachId, String athleteId) {
        if (!coachService.isCoachOfAthlete(coachId, athleteId)) {
            throw new ForbiddenOperationException(
                    "Not authorized: you are not the coach of this athlete");
        }
    }

    private void validate(Map<String, String> sections) {
        if (sections == null || sections.isEmpty()) {
            throw new ValidationException("sections is required");
        }
        if (sections.size() > MAX_SECTIONS) {
            throw new ValidationException("too many sections (max " + MAX_SECTIONS + ")");
        }
        for (Map.Entry<String, String> e : sections.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) {
                throw new ValidationException("section title is required");
            }
            if (e.getKey().length() > MAX_TITLE_LENGTH) {
                throw new ValidationException("section title too long (max " + MAX_TITLE_LENGTH + ")");
            }
            if (e.getValue() != null && e.getValue().length() > MAX_SECTION_LENGTH) {
                throw new ValidationException(
                        "section '" + e.getKey() + "' too long (max " + MAX_SECTION_LENGTH + " chars)");
            }
        }
    }

    /** A single context document's content. */
    public record ContextEntry(Map<String, String> sections, Provenance provenance, String updatedAt) {
        public static ContextEntry from(AthleteContext a) {
            return new ContextEntry(a.getSections(), a.getProvenance(),
                    a.getUpdatedAt() != null ? a.getUpdatedAt().toString() : null);
        }

        public static ContextEntry from(CoachContext c) {
            return new ContextEntry(c.getSections(), c.getProvenance(),
                    c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : null);
        }
    }

    /** The caller's own context plus the role that determines which kind it is. */
    public record MyContext(String role, Map<String, String> sections, Provenance provenance,
                            String updatedAt) {
        static MyContext empty(UserRole role) {
            return new MyContext(role.name(), null, null, null);
        }

        static MyContext coach(CoachContext c) {
            return new MyContext(UserRole.COACH.name(), c.getSections(), c.getProvenance(),
                    c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : null);
        }

        static MyContext athlete(AthleteContext a) {
            return new MyContext(UserRole.ATHLETE.name(), a.getSections(), a.getProvenance(),
                    a.getUpdatedAt() != null ? a.getUpdatedAt().toString() : null);
        }
    }

    /** A coach's view of an athlete: their self-context (read-only) + the coach's private entry. */
    public record CoachAthleteContext(ContextEntry athleteSelf, ContextEntry coachContext) {}
}
