package com.koval.trainingplannerbackend.ai;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutRepository;
import com.koval.trainingplannerbackend.coach.tools.ScheduleSummary;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.ai.chat.model.ToolContext;
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
    private final CoachService coachService;

    public ContextToolService(UserRepository userRepository,
                              ScheduledWorkoutRepository scheduledWorkoutRepository,
                              TrainingRepository trainingRepository,
                              CoachService coachService) {
        this.userRepository = userRepository;
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
        this.trainingRepository = trainingRepository;
        this.coachService = coachService;
    }

    @Tool(description = "Today's date, day of week, and current week boundaries (Mon–Sun).")
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

    @Tool(description = "Get user profile: FTP, CTL, ATL, TSB, role, displayName.")
    public Map<String, Object> getUserProfile(ToolContext context) {
        String userId = SecurityUtils.getUserId(context);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        return Map.of(
                "displayName", user.getDisplayName() != null ? user.getDisplayName() : "",
                "role", user.getRole().name(),
                "funtionalThresholdPower", user.getFtp() != null ? user.getFtp() : 250,
                "criticalSwimSpeed", user.getCriticalSwimSpeed() != null ? user.getCriticalSwimSpeed() : 120,
                "funtionalThresholdPace", user.getFunctionalThresholdPace() != null ? user.getFunctionalThresholdPace() : 240,
                "ctl", user.getCtl() != null ? user.getCtl() : 0,
                "atl", user.getAtl() != null ? user.getAtl() : 0,
                "tsb", user.getTsb() != null ? user.getTsb() : 0
        );
    }

    @Tool(description = "Schedule a training for yourself on a date.")
    public ScheduleSummary selfAssignTraining(
            @ToolParam(description = "Training ID") String trainingId,
            @ToolParam(description = "Date (YYYY-MM-DD)") LocalDate scheduledDate,
            @ToolParam(description = "Notes (optional)") String notes,
            ToolContext context) {
        String userId = SecurityUtils.getUserId(context);
        ScheduledWorkout sw = coachService.selfAssignTraining(userId, trainingId, scheduledDate, notes);
        String title = trainingRepository.findById(trainingId)
                .map(Training::getTitle).orElse("Unknown");
        return ScheduleSummary.from(sw, title);
    }
}
