package com.koval.trainingplannerbackend.notification;

import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.coach.ScheduleStatus;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Daily job that sends "You have workouts today" reminders
 * to athletes with pending scheduled workouts.
 */
@Component
public class ScheduledNotificationJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledNotificationJob.class);

    private final ScheduledWorkoutRepository scheduledWorkoutRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public ScheduledNotificationJob(ScheduledWorkoutRepository scheduledWorkoutRepository,
                                    UserRepository userRepository,
                                    NotificationService notificationService) {
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /**
     * Runs daily at 7:00 AM. Finds all PENDING workouts for today and sends
     * a reminder to each athlete.
     */
    @Scheduled(cron = "0 0 7 * * *")
    public void sendDailyWorkoutReminders() {
        LocalDate today = LocalDate.now();
        List<ScheduledWorkout> todaysWorkouts = scheduledWorkoutRepository
                .findByScheduledDateAndStatus(today, ScheduleStatus.PENDING);

        if (todaysWorkouts.isEmpty()) {
            log.debug("No pending workouts for today — no reminders sent");
            return;
        }

        // Group by athlete
        Map<String, List<ScheduledWorkout>> byAthlete = todaysWorkouts.stream()
                .collect(Collectors.groupingBy(ScheduledWorkout::getAthleteId));

        byAthlete.forEach((athleteId, workouts) -> {
            int count = workouts.size();
            String body = count == 1
                    ? "You have 1 workout scheduled for today. Let's go!"
                    : "You have " + count + " workouts scheduled for today. Let's go!";

            notificationService.sendToUsers(
                    List.of(athleteId),
                    "Workout Reminder",
                    body,
                    Map.of("type", "WORKOUT_REMINDER"),
                    "workoutReminder");
        });

        log.info("Sent daily workout reminders to {} athletes", byAthlete.size());
    }
}
