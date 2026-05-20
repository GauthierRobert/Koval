package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.coach.ScheduleStatus;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutRepository;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Automatically associates completed sessions with pending scheduled workouts
 * based on sport, date, duration, and title similarity, and exposes ranked
 * candidates for the manual link picker.
 */
@Service
public class SessionAssociationService {

    private static final int SPORT_MATCH_SCORE = 30;
    private static final int DATE_MATCH_SCORE = 20;
    private static final int DURATION_CLOSE_SCORE = 25;
    private static final int DURATION_MODERATE_SCORE = 10;
    private static final int TITLE_WORD_SCORE = 5;
    private static final int TITLE_WORD_CAP = 25;

    /** Score ≥ this auto-links the session to the matching scheduled workout. */
    static final int AUTO_LINK_THRESHOLD = 60;
    /** Score in [SUGGESTION_FLOOR, AUTO_LINK_THRESHOLD) is stored as a suggestion for user confirmation. */
    static final int SUGGESTION_FLOOR = 30;

    /** Window (days) used when listing candidates for the manual picker. */
    static final int PICKER_WINDOW_DAYS = 3;

    private static final double DURATION_CLOSE_RATIO = 0.20;
    private static final double DURATION_MODERATE_RATIO = 0.40;

    private final ScheduledWorkoutRepository scheduledWorkoutRepository;
    private final TrainingRepository trainingRepository;
    private final CompletedSessionRepository sessionRepository;

    public SessionAssociationService(ScheduledWorkoutRepository scheduledWorkoutRepository,
                                     TrainingRepository trainingRepository,
                                     CompletedSessionRepository sessionRepository) {
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
        this.trainingRepository = trainingRepository;
        this.sessionRepository = sessionRepository;
    }

    /**
     * Score the same-day pending scheduled workouts and choose an outcome:
     * <ul>
     *   <li>≥ {@link #AUTO_LINK_THRESHOLD}: firmly link via {@code scheduledWorkoutId}.</li>
     *   <li>[{@link #SUGGESTION_FLOOR}, {@link #AUTO_LINK_THRESHOLD}): record as a suggestion only.</li>
     *   <li>otherwise: leave both unset.</li>
     * </ul>
     * Skipped entirely when the user has marked the session as unplanned.
     */
    public void tryAutoAssociate(CompletedSession session, String userId) {
        if (Boolean.TRUE.equals(session.getUnplanned())) return;

        LocalDate day = session.getCompletedAt().toLocalDate();
        List<ScheduledWorkout> pending = findPendingCandidates(userId, day);
        if (pending.isEmpty()) return;

        Map<String, Training> trainingsById = loadTrainingsByIds(pending);
        Map.Entry<ScheduledWorkout, Integer> best = findBestMatch(session, pending, trainingsById);

        if (best == null) return;
        int score = best.getValue();
        String scheduledWorkoutId = best.getKey().getId();

        if (score >= AUTO_LINK_THRESHOLD) {
            session.setScheduledWorkoutId(scheduledWorkoutId);
            session.setSuggestedScheduledWorkoutId(null);
            session.setSuggestionScore(null);
        } else if (score >= SUGGESTION_FLOOR) {
            session.setSuggestedScheduledWorkoutId(scheduledWorkoutId);
            session.setSuggestionScore(score);
        }
    }

    /**
     * Returns ranked link candidates within ±{@link #PICKER_WINDOW_DAYS} days of the session,
     * same sport, status {@code PENDING}, not yet linked to any session. Each entry exposes the
     * score breakdown so the UI can render confidence cues.
     */
    public List<LinkCandidate> listCandidates(CompletedSession session) {
        LocalDate day = session.getCompletedAt().toLocalDate();
        List<ScheduledWorkout> nearby = scheduledWorkoutRepository
                .findByAthleteIdAndScheduledDateBetween(
                        session.getUserId(),
                        day.minusDays(PICKER_WINDOW_DAYS),
                        day.plusDays(PICKER_WINDOW_DAYS))
                .stream()
                .filter(sw -> sw.getStatus() == ScheduleStatus.PENDING)
                .filter(sw -> sw.getSessionId() == null)
                .toList();

        if (nearby.isEmpty()) return List.of();

        Map<String, Training> trainingsById = loadTrainingsByIds(nearby);

        List<LinkCandidate> candidates = new ArrayList<>();
        for (ScheduledWorkout sw : nearby) {
            Training training = trainingsById.get(sw.getTrainingId());
            if (training == null) continue;
            ScoreBreakdown breakdown = scoreBreakdown(session, sw, training);
            if (breakdown.total() == 0) continue;
            candidates.add(new LinkCandidate(
                    sw.getId(),
                    training.getTitle(),
                    training.getSportType() != null ? training.getSportType().name() : null,
                    sw.getScheduledDate(),
                    training.getEstimatedDurationSeconds(),
                    breakdown.total(),
                    breakdown.sport(),
                    breakdown.date(),
                    breakdown.duration(),
                    breakdown.title()
            ));
        }
        candidates.sort(Comparator.comparingInt(LinkCandidate::totalScore).reversed());
        return candidates;
    }

    private List<ScheduledWorkout> findPendingCandidates(String userId, LocalDate day) {
        List<ScheduledWorkout> candidates = scheduledWorkoutRepository
                .findByAthleteIdAndScheduledDate(userId, day);

        return candidates.stream()
                .filter(sw -> sw.getStatus() == ScheduleStatus.PENDING)
                .filter(sw -> sw.getSessionId() == null)
                .toList();
    }

    private Map<String, Training> loadTrainingsByIds(List<ScheduledWorkout> pending) {
        List<String> trainingIds = pending.stream().map(ScheduledWorkout::getTrainingId).toList();
        return trainingRepository.findAllById(trainingIds).stream()
                .collect(Collectors.toMap(Training::getId, Function.identity()));
    }

    private Map.Entry<ScheduledWorkout, Integer> findBestMatch(CompletedSession session,
                                           List<ScheduledWorkout> pending,
                                           Map<String, Training> trainingsById) {
        return pending.stream()
                .filter(sw -> trainingsById.get(sw.getTrainingId()) != null)
                .map(sw -> Map.<ScheduledWorkout, Integer>entry(sw,
                        scoreCandidate(session, sw, trainingsById.get(sw.getTrainingId()))))
                .filter(e -> e.getValue() > 0)
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .orElse(null);
    }

    /**
     * If the given scheduled workout has a synthetic (planned) session linked, delete it
     * so the real session can take its place.
     */
    public void deleteSyntheticSessionForSchedule(String scheduledWorkoutId) {
        scheduledWorkoutRepository.findById(scheduledWorkoutId).ifPresent(sw -> {
            if (sw.getSessionId() != null) {
                sessionRepository.findById(sw.getSessionId()).ifPresent(existing -> {
                    if (Boolean.TRUE.equals(existing.getSyntheticCompletion())) {
                        sessionRepository.delete(existing);
                        sw.setSessionId(null);
                        scheduledWorkoutRepository.save(sw);
                    }
                });
            }
        });
    }

    /**
     * Clears the sessionId on a scheduled workout, unlinking it from any completed session.
     */
    public void clearScheduledWorkoutLink(String scheduledWorkoutId) {
        scheduledWorkoutRepository.findById(scheduledWorkoutId).ifPresent(sw -> {
            sw.setSessionId(null);
            scheduledWorkoutRepository.save(sw);
        });
    }

    static int scoreCandidate(CompletedSession session, ScheduledWorkout sw, Training training) {
        return scoreBreakdown(session, sw, training).total();
    }

    static ScoreBreakdown scoreBreakdown(CompletedSession session, ScheduledWorkout sw, Training training) {
        String sessionSport = session.getSportType();
        if (training.getSportType() == null ||
                !training.getSportType().name().equalsIgnoreCase(sessionSport)) {
            return ScoreBreakdown.ZERO;
        }

        int sport = SPORT_MATCH_SCORE;
        int date = (sw.getScheduledDate() != null
                && sw.getScheduledDate().equals(session.getCompletedAt().toLocalDate()))
                ? DATE_MATCH_SCORE : 0;
        int duration = scoreDurationProximity(training, session);
        int title = scoreTitleOverlap(training.getTitle(), session.getTitle());

        return new ScoreBreakdown(sport, date, duration, title);
    }

    private static int scoreDurationProximity(Training training, CompletedSession session) {
        if (training.getEstimatedDurationSeconds() == null || training.getEstimatedDurationSeconds() <= 0) {
            return 0;
        }
        int planned = training.getEstimatedDurationSeconds();
        int actual = session.getTotalDurationSeconds();
        double ratio = Math.abs((double) (actual - planned)) / planned;
        if (ratio <= DURATION_CLOSE_RATIO) {
            return DURATION_CLOSE_SCORE;
        } else if (ratio <= DURATION_MODERATE_RATIO) {
            return DURATION_MODERATE_SCORE;
        }
        return 0;
    }

    private static int scoreTitleOverlap(String plannedTitle, String sessionTitle) {
        if (plannedTitle == null || sessionTitle == null) {
            return 0;
        }
        Set<String> planWords = wordSet(plannedTitle);
        planWords.retainAll(wordSet(sessionTitle));
        return Math.min(planWords.size() * TITLE_WORD_SCORE, TITLE_WORD_CAP);
    }

    static Set<String> wordSet(String text) {
        return Arrays.stream(text.toLowerCase().split("\\W+"))
                .filter(w -> w.length() > 2)
                .collect(Collectors.toSet());
    }

    /** Sub-score breakdown for a single (session, scheduledWorkout) pair. */
    public record ScoreBreakdown(int sport, int date, int duration, int title) {
        static final ScoreBreakdown ZERO = new ScoreBreakdown(0, 0, 0, 0);

        public int total() {
            return sport + date + duration + title;
        }
    }

    /**
     * Candidate row returned to the picker UI. Score sub-components let the UI render a
     * confidence breakdown ("✓ Sport · ✓ Same day · ~ Duration").
     */
    public record LinkCandidate(
            String scheduledWorkoutId,
            String title,
            String sport,
            LocalDate scheduledDate,
            Integer plannedDurationSeconds,
            int totalScore,
            int sportScore,
            int dateScore,
            int durationScore,
            int titleScore
    ) {}
}
