package com.koval.trainingplannerbackend.ai;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.coach.ScheduleSummary;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutRepository;
import com.koval.trainingplannerbackend.training.Training;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ContextToolService {

    private final UserRepository userRepository;
    private final ScheduledWorkoutRepository scheduledWorkoutRepository;
    private final TrainingRepository trainingRepository;

    public ContextToolService(UserRepository userRepository,
                              ScheduledWorkoutRepository scheduledWorkoutRepository,
                              TrainingRepository trainingRepository) {
        this.userRepository = userRepository;
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
        this.trainingRepository = trainingRepository;
    }

    @Tool(description = "Get today's date, day of the week, and current week boundaries. " +
            "Use this whenever you need to know the current date, schedule workouts relative to today, " +
            "or determine which day of the week it is.")
    public Map<String, String> getCurrentDate() {
        LocalDate today = LocalDate.now();
        DayOfWeek dow = today.getDayOfWeek();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = today.with(DayOfWeek.SUNDAY);

        return Map.of(
                "today", today.toString(),
                "dayOfWeek", dow.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                "weekStart", weekStart.toString(),
                "weekEnd", weekEnd.toString()
        );
    }

    @Tool(description = "Get the scheduled workouts for a user within a date range. " +
            "Returns lean summaries with training titles and status (PENDING, COMPLETED, SKIPPED). " +
            "Useful to see what the user has planned or completed recently before suggesting new workouts.")
    public List<ScheduleSummary> getUserSchedule(
            @ToolParam(description = "The user ID to get the schedule for") String userId,
            @ToolParam(description = "Start date (inclusive) in YYYY-MM-DD format") LocalDate startDate,
            @ToolParam(description = "End date (inclusive) in YYYY-MM-DD format") LocalDate endDate) {
        List<ScheduledWorkout> workouts = scheduledWorkoutRepository
                .findByAthleteIdAndScheduledDateBetween(userId, startDate, endDate);

        if (workouts.isEmpty()) return List.of();

        List<String> trainingIds = workouts.stream()
                .map(ScheduledWorkout::getTrainingId).distinct().toList();
        Map<String, String> titleMap = trainingRepository.findAllById(trainingIds).stream()
                .collect(Collectors.toMap(Training::getId, Training::getTitle, (a, b) -> a));

        return workouts.stream()
                .map(sw -> ScheduleSummary.from(sw, titleMap.getOrDefault(sw.getTrainingId(), "Unknown")))
                .toList();
    }

    @Tool(description = "Get detailed profile information for a user, including FTP, training load metrics " +
            "(CTL, ATL, TSB), role, and display name. Use this to personalize advice and workout recommendations.")
    public Map<String, Object> getUserProfile(
            @ToolParam(description = "The user ID to get the profile for") String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        return Map.of(
                "displayName", user.getDisplayName() != null ? user.getDisplayName() : "",
                "role", user.getRole().name(),
                "ftp", user.getFtp() != null ? user.getFtp() : 250,
                "ctl", user.getCtl() != null ? user.getCtl() : 0,
                "atl", user.getAtl() != null ? user.getAtl() : 0,
                "tsb", user.getTsb() != null ? user.getTsb() : 0
        );
    }
}
