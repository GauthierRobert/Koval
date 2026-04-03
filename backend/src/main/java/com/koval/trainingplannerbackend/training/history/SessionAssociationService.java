package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutRepository;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Automatically associates completed sessions with pending scheduled workouts
 * based on sport, date, duration, and title similarity.
 */
@Service
public class SessionAssociationService {

    private static final int SPORT_MATCH_SCORE = 30;
    private static final int DATE_MATCH_SCORE = 20;
    private static final int DURATION_CLOSE_SCORE = 25;
    private static final int DURATION_MODERATE_SCORE = 10;
    private static final int TITLE_WORD_SCORE = 5;
    private static final int TITLE_WORD_CAP = 25;
    private static final int ASSOCIATION_THRESHOLD = 60;

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
     * Attempts to automatically associate a completed session with a pending scheduled workout
     * based on sport type, date, duration proximity, and title similarity.
     */
    public void tryAutoAssociate(CompletedSession session, String userId) {
        LocalDate day = session.getCompletedAt().toLocalDate();
        List<ScheduledWorkout> pending = findPendingCandidates(userId, day);
        if (pending.isEmpty()) return;

        Map<String, Training> trainingsById = loadTrainingsByIds(pending);
        ScheduledWorkout best = findBestMatch(session, pending, trainingsById);

        if (best != null) {
            session.setScheduledWorkoutId(best.getId());
        }
    }

    private List<ScheduledWorkout> findPendingCandidates(String userId, LocalDate day) {
        List<ScheduledWorkout> candidates = scheduledWorkoutRepository
                .findByAthleteIdAndScheduledDate(userId, day);

        return candidates.stream()
                .filter(sw -> "PENDING".equals(sw.getStatus() != null ? sw.getStatus().name() : null))
                .toList();
    }

    private Map<String, Training> loadTrainingsByIds(List<ScheduledWorkout> pending) {
        List<String> trainingIds = pending.stream().map(ScheduledWorkout::getTrainingId).toList();
        return trainingRepository.findAllById(trainingIds).stream()
                .collect(Collectors.toMap(Training::getId, Function.identity()));
    }

    private ScheduledWorkout findBestMatch(CompletedSession session,
                                           List<ScheduledWorkout> pending,
                                           Map<String, Training> trainingsById) {
        ScheduledWorkout best = null;
        int bestScore = 0;

        for (ScheduledWorkout sw : pending) {
            Training training = trainingsById.get(sw.getTrainingId());
            if (training == null) continue;
            int s = scoreCandidate(session, sw, training);
            if (s > bestScore) {
                bestScore = s;
                best = sw;
            }
        }

        return bestScore >= ASSOCIATION_THRESHOLD ? best : null;
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
        String sessionSport = session.getSportType();
        if (training.getSportType() == null ||
                !training.getSportType().name().equalsIgnoreCase(sessionSport)) {
            return 0;
        }

        int score = SPORT_MATCH_SCORE;

        if (sw.getScheduledDate() != null &&
                sw.getScheduledDate().equals(session.getCompletedAt().toLocalDate())) {
            score += DATE_MATCH_SCORE;
        }

        score += scoreDurationProximity(training, session);
        score += scoreTitleOverlap(training.getTitle(), session.getTitle());

        return score;
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
}
