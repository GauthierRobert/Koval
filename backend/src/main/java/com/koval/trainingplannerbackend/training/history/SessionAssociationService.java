package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutRepository;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        List<ScheduledWorkout> candidates = scheduledWorkoutRepository
                .findByAthleteIdAndScheduledDate(userId, day);

        List<ScheduledWorkout> pending = candidates.stream()
                .filter(sw -> "PENDING".equals(sw.getStatus() != null ? sw.getStatus().name() : null))
                .toList();
        if (pending.isEmpty()) return;

        List<String> trainingIds = pending.stream().map(ScheduledWorkout::getTrainingId).toList();
        Map<String, Training> trainingsById = trainingRepository.findAllById(trainingIds).stream()
                .collect(Collectors.toMap(Training::getId, Function.identity()));

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

        if (bestScore >= ASSOCIATION_THRESHOLD && best != null) {
            session.setScheduledWorkoutId(best.getId());
        }
    }

    /**
     * If the given scheduled workout has a synthetic (planned) session linked, delete it
     * so the real session can take its place.
     */
    public void deleteSyntheticSessionForSchedule(String scheduledWorkoutId) {
        scheduledWorkoutRepository.findById(scheduledWorkoutId).ifPresent(sw -> {
            if (sw.getSessionId() != null) {
                sessionRepository.findById(sw.getSessionId()).ifPresent(existing -> {
                    if (existing.isSyntheticCompletion()) {
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

        if (training.getEstimatedDurationSeconds() != null && training.getEstimatedDurationSeconds() > 0) {
            int planned = training.getEstimatedDurationSeconds();
            int actual = session.getTotalDurationSeconds();
            double ratio = Math.abs((double) (actual - planned)) / planned;
            if (ratio <= DURATION_CLOSE_RATIO) {
                score += DURATION_CLOSE_SCORE;
            } else if (ratio <= DURATION_MODERATE_RATIO) {
                score += DURATION_MODERATE_SCORE;
            }
        }

        if (training.getTitle() != null && session.getTitle() != null) {
            Set<String> planWords = wordSet(training.getTitle());
            planWords.retainAll(wordSet(session.getTitle()));
            score += Math.min(planWords.size() * TITLE_WORD_SCORE, TITLE_WORD_CAP);
        }

        return score;
    }

    static Set<String> wordSet(String text) {
        Set<String> words = new HashSet<>();
        for (String w : text.toLowerCase().split("\\W+")) {
            if (w.length() > 2) words.add(w);
        }
        return words;
    }
}
