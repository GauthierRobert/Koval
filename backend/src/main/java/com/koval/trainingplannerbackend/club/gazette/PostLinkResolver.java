package com.koval.trainingplannerbackend.club.gazette;

import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSessionRepository;
import com.koval.trainingplannerbackend.goal.RaceGoal;
import com.koval.trainingplannerbackend.goal.RaceGoalRepository;
import com.koval.trainingplannerbackend.race.Race;
import com.koval.trainingplannerbackend.race.RaceService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Resolves and validates the optional session / race-goal link attached to a
 * gazette post. Centralizing this logic keeps {@link ClubGazetteService} small
 * and the link rules consistent across post create / update.
 */
@Component
public class PostLinkResolver {

    private final ClubTrainingSessionRepository clubSessionRepository;
    private final RaceGoalRepository raceGoalRepository;
    private final RaceService raceService;

    public PostLinkResolver(ClubTrainingSessionRepository clubSessionRepository,
                            RaceGoalRepository raceGoalRepository,
                            RaceService raceService) {
        this.clubSessionRepository = clubSessionRepository;
        this.raceGoalRepository = raceGoalRepository;
        this.raceService = raceService;
    }

    /** Validates that the supplied link IDs match the post type's contract. */
    public void validateLinksForType(GazettePostType type, String sessionId, String raceGoalId) {
        switch (type) {
            case SESSION_RECAP -> {
                requireNonBlank(sessionId, "SESSION_RECAP requires linkedSessionId");
                requireBlank(raceGoalId, "SESSION_RECAP cannot also link a race goal");
            }
            case RACE_RESULT -> {
                requireNonBlank(raceGoalId, "RACE_RESULT requires linkedRaceGoalId");
                requireBlank(sessionId, "RACE_RESULT cannot also link a session");
            }
            case PERSONAL_WIN, SHOUTOUT, REFLECTION -> {
                if (!isBlank(sessionId) || !isBlank(raceGoalId)) {
                    throw new IllegalArgumentException(type + " posts cannot have a link");
                }
            }
        }
    }

    /**
     * Applies (or clears and re-applies) the appropriate link snapshot on the
     * post. Caller is responsible for persisting the post.
     */
    public void applyLink(ClubGazettePost post, ClubGazetteEdition edition, String userId,
                          String sessionId, String raceGoalId) {
        post.setLinkedSessionId(null);
        post.setLinkedRaceGoalId(null);
        post.setLinkedSessionSnapshot(null);
        post.setLinkedRaceGoalSnapshot(null);

        if (post.getType() == GazettePostType.SESSION_RECAP) {
            applySessionLink(post, edition, userId, sessionId);
        } else if (post.getType() == GazettePostType.RACE_RESULT) {
            applyRaceGoalLink(post, edition, userId, raceGoalId);
        }
    }

    private void applySessionLink(ClubGazettePost post, ClubGazetteEdition edition,
                                  String userId, String sessionId) {
        ClubTrainingSession session = clubSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Linked session not found"));
        if (!session.getClubId().equals(post.getClubId())) {
            throw new IllegalArgumentException("Linked session does not belong to this club");
        }
        if (!session.getParticipantIds().contains(userId)) {
            throw new IllegalArgumentException("You must be a participant of the linked session");
        }
        if (session.getScheduledAt() == null
                || session.getScheduledAt().isBefore(edition.getPeriodStart())
                || !session.getScheduledAt().isBefore(edition.getPeriodEnd())) {
            throw new IllegalArgumentException("Linked session is not within the gazette period");
        }
        post.setLinkedSessionId(sessionId);
        post.setLinkedSessionSnapshot(new ClubGazettePost.LinkedSessionSnapshot(
                session.getId(), session.getTitle(), session.getSport(),
                session.getScheduledAt(), session.getLocation()));
    }

    private void applyRaceGoalLink(ClubGazettePost post, ClubGazetteEdition edition,
                                   String userId, String raceGoalId) {
        RaceGoal goal = raceGoalRepository.findById(raceGoalId)
                .orElseThrow(() -> new IllegalArgumentException("Linked race goal not found"));
        if (!goal.getAthleteId().equals(userId)) {
            throw new IllegalArgumentException("Race goal must belong to you");
        }
        LocalDate raceDate = resolveRaceDate(goal);
        if (raceDate == null) {
            throw new IllegalArgumentException("Linked race has no date set");
        }
        LocalDateTime raceAt = raceDate.atStartOfDay();
        if (raceAt.isBefore(edition.getPeriodStart()) || !raceAt.isBefore(edition.getPeriodEnd())) {
            throw new IllegalArgumentException("Linked race is not within the gazette period");
        }
        post.setLinkedRaceGoalId(raceGoalId);
        post.setLinkedRaceGoalSnapshot(new ClubGazettePost.LinkedRaceGoalSnapshot(
                goal.getId(), goal.getTitle(), goal.getSport(),
                raceDate, goal.getDistance(), goal.getTargetTime(), null));
    }

    private LocalDate resolveRaceDate(RaceGoal goal) {
        if (goal.getRaceId() == null) return null;
        try {
            Race race = raceService.getRaceById(goal.getRaceId());
            return Optional.ofNullable(race.getScheduledDate()).map(LocalDate::parse).orElse(null);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static void requireNonBlank(String value, String message) {
        if (isBlank(value)) throw new IllegalArgumentException(message);
    }

    private static void requireBlank(String value, String message) {
        if (!isBlank(value)) throw new IllegalArgumentException(message);
    }
}
