package com.koval.trainingplannerbackend.integration.polar;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutRepository;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.model.Training;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Pushes Koval planned workouts to the athlete's Polar account as training targets. */
@Service
public class PolarTrainingTargetService {

    private static final Logger log = LoggerFactory.getLogger(PolarTrainingTargetService.class);

    private final PolarOAuthService oauthService;
    private final PolarApiClient apiClient;
    private final PolarTrainingTargetMapper mapper = new PolarTrainingTargetMapper();
    private final UserRepository userRepository;
    private final TrainingService trainingService;
    private final ScheduledWorkoutRepository scheduledWorkoutRepository;

    public PolarTrainingTargetService(PolarOAuthService oauthService,
                                       PolarApiClient apiClient,
                                       UserRepository userRepository,
                                       TrainingService trainingService,
                                       ScheduledWorkoutRepository scheduledWorkoutRepository) {
        this.oauthService = oauthService;
        this.apiClient = apiClient;
        this.userRepository = userRepository;
        this.trainingService = trainingService;
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
    }

    /**
     * Pushes the training behind a scheduled workout to the athlete's Polar Flow.
     * @return the Polar-assigned training target id, or null when Polar didn't echo one.
     */
    public String pushScheduledWorkout(String scheduledWorkoutId) {
        ScheduledWorkout scheduled = scheduledWorkoutRepository.findById(scheduledWorkoutId)
                .orElseThrow(() -> new ResourceNotFoundException("ScheduledWorkout not found"));

        User athlete = userRepository.findById(scheduled.getAthleteId())
                .orElseThrow(() -> new ResourceNotFoundException("Athlete not found"));

        if (athlete.getPolarUserId() == null || athlete.getPolarAccessToken() == null) {
            throw new IllegalStateException("Athlete has not connected Polar");
        }

        Training training = trainingService.getTrainingById(scheduled.getTrainingId());
        Map<String, Object> payload = mapper.map(training, scheduled.getScheduledDate());

        String accessToken = oauthService.ensureValidToken(athlete);
        return apiClient.createTrainingTarget(accessToken, athlete.getPolarUserId(), payload)
                .orElse(null);
    }
}
