package com.koval.trainingplannerbackend.ai.action;

import com.koval.trainingplannerbackend.club.session.ClubSessionService;
import com.koval.trainingplannerbackend.club.dto.CreateSessionRequest;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.tools.TrainingMapper;
import com.koval.trainingplannerbackend.training.tools.TrainingRequest;
import com.koval.trainingplannerbackend.training.tools.WorkoutElementRequest;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI tool service for one-shot actions: creates a Training + optional ClubTrainingSession atomically.
 */
@Service
public class AIActionToolService {

    private final TrainingMapper trainingMapper;
    private final TrainingService trainingService;
    private final ClubSessionService clubSessionService;

    public AIActionToolService(TrainingMapper trainingMapper,
                               TrainingService trainingService,
                               ClubSessionService clubSessionService) {
        this.trainingMapper = trainingMapper;
        this.trainingService = trainingService;
        this.clubSessionService = clubSessionService;
    }

    public record ActionResult(String trainingId, String trainingTitle, String sessionId, String message) {
    }

    @Tool(description = """
            Create a new training plan and optionally a linked club training session in a single atomic action.
            Call this exactly ONCE. Pass userId, clubId, clubGroupId, and coachGroupId exactly as provided in the system context.""")
    public ActionResult createTrainingWithClubSession(
            @ToolParam(description = "User ID — pass exactly from system context") String userId,
            @ToolParam(description = "Sport type: CYCLING|RUNNING|SWIMMING|BRICK") String sportType,
            @ToolParam(description = "Training title") String title,
            @ToolParam(description = "Training description") String description,
            @ToolParam(description = "Training type: VO2MAX|THRESHOLD|SWEET_SPOT|ENDURANCE|SPRINT|RECOVERY|MIXED|TEST") String trainingType,
            @ToolParam(description = "Workout blocks (WARMUP, STEADY, INTERVAL, RAMP, COOLDOWN, FREE, PAUSE)") List<WorkoutElementRequest> blocks,
            @ToolParam(description = "Estimated TSS") Integer estimatedTss,
            @ToolParam(description = "Title for the club session") String sessionTitle,
            @ToolParam(description = "Description for the club session") String sessionDescription,
            @ToolParam(description = "Location for the club session") String location,
            @ToolParam(description = "Max participants") Integer maxParticipants,
            @ToolParam(description = "Scheduled datetime ISO-8601 (e.g. 2025-06-10T19:00:00), null if unscheduled") String scheduledAt,
            @ToolParam(description = "Club ID from system context — pass null if not in club context") String clubId,
            @ToolParam(description = "Club group ID from system context — pass null if not applicable") String clubGroupId,
            @ToolParam(description = "Coach group ID from system context — pass null if not applicable") String coachGroupId) {

        ActionToolTracker.markCalled();

        // 1. Build and save the Training
        List<String> groupIds = new ArrayList<>();
        if (coachGroupId != null && !coachGroupId.equals("null")) {
            groupIds.add(coachGroupId);
        }
        List<String> clubGroupIds = new ArrayList<>();
        if (clubGroupId != null && !clubGroupId.equals("null")) {
            clubGroupIds.add(clubGroupId);
        }
        String resolvedClubId = (clubId != null && !clubId.equals("null")) ? clubId : null;

        TrainingRequest request = new TrainingRequest(
                sportType, title, description, trainingType,
                estimatedTss, null, blocks, groupIds);

        Training training = trainingMapper.mapToEntity(request);
        training.addClubId(resolvedClubId);
        training.setClubGroupIds(clubGroupIds);

        Training saved = trainingService.createTraining(training, userId);

        // 2. Optionally create a club session
        String sessionId = null;
        if (resolvedClubId != null) {
            LocalDateTime scheduledDateTime = null;
            if (scheduledAt != null && !scheduledAt.isBlank() && !scheduledAt.equals("null")) {
                try {
                    scheduledDateTime = LocalDateTime.parse(scheduledAt);
                } catch (Exception ignored) {
                    // leave as null if unparseable
                }
            }
            CreateSessionRequest sessionReq = new CreateSessionRequest(
                    sessionTitle != null ? sessionTitle : title,
                    sportType,
                    scheduledDateTime,
                    location,
                    sessionDescription,
                    saved.getId(),
                    maxParticipants,
                    null,
                    null,
                    null,
                    false,
                    null,
                    null);

            var session = clubSessionService.createSession(userId, resolvedClubId, sessionReq);
            sessionId = session.getId();
        }

        String msg = sessionId != null
                ? "Created training '" + saved.getTitle() + "' and club session."
                : "Created training '" + saved.getTitle() + "'.";

        return new ActionResult(saved.getId(), saved.getTitle(), sessionId, msg);
    }
}
