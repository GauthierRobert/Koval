package com.koval.trainingplannerbackend.club;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.club.dto.ClubActivityResponse;
import com.koval.trainingplannerbackend.notification.NotificationService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ClubActivityService {

    private final ClubActivityRepository activityRepository;
    private final ClubMembershipRepository membershipRepository;
    private final UserService userService;
    private final NotificationService notificationService;

    public ClubActivityService(ClubActivityRepository activityRepository,
                               ClubMembershipRepository membershipRepository,
                               UserService userService,
                               NotificationService notificationService) {
        this.activityRepository = activityRepository;
        this.membershipRepository = membershipRepository;
        this.userService = userService;
        this.notificationService = notificationService;
    }

    public void emitActivity(String clubId, ClubActivityType type, String actorId,
                              String targetId, String targetTitle) {
        ClubActivity activity = new ClubActivity();
        activity.setClubId(clubId);
        activity.setType(type);
        activity.setActorId(actorId);
        activity.setTargetId(targetId);
        activity.setTargetTitle(targetTitle);
        activity.setOccurredAt(LocalDateTime.now());
        activityRepository.save(activity);

        if (type == ClubActivityType.SESSION_CREATED) {
            List<String> memberIds = getActiveMemberIds(clubId);
            memberIds.remove(actorId);
            if (!memberIds.isEmpty()) {
                User actor = userService.findById(actorId).orElse(null);
                String actorName = actor != null ? actor.getDisplayName() : "Someone";
                notificationService.sendToUsers(
                        memberIds,
                        "New Group Session",
                        actorName + " created a training session: " + targetTitle,
                        Map.of("type", "SESSION_CREATED",
                               "clubId", clubId,
                               "sessionId", targetId != null ? targetId : ""));
            }
        }

        if (type == ClubActivityType.SESSION_CANCELLED) {
            List<String> memberIds = getActiveMemberIds(clubId);
            memberIds.remove(actorId);
            if (!memberIds.isEmpty()) {
                User actor = userService.findById(actorId).orElse(null);
                String actorName = actor != null ? actor.getDisplayName() : "Someone";
                notificationService.sendToUsers(
                        memberIds,
                        "Session Cancelled",
                        actorName + " cancelled the session: " + targetTitle,
                        Map.of("type", "SESSION_CANCELLED",
                               "clubId", clubId,
                               "sessionId", targetId != null ? targetId : ""));
            }
        }
    }

    public List<ClubActivityResponse> getActivityFeed(String clubId, Pageable pageable) {
        List<ClubActivity> activities = activityRepository.findByClubIdOrderByOccurredAtDesc(clubId, pageable);
        List<String> actorIds = activities.stream().map(ClubActivity::getActorId).distinct().toList();
        Map<String, User> userMap = userService.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return activities.stream().map(a -> {
            User actor = userMap.get(a.getActorId());
            String actorName = actor != null ? actor.getDisplayName() : a.getActorId();
            return new ClubActivityResponse(
                    a.getId(), a.getType(), a.getActorId(), actorName,
                    a.getTargetId(), a.getTargetTitle(), a.getOccurredAt());
        }).collect(Collectors.toList());
    }

    List<String> getActiveMemberIds(String clubId) {
        return membershipRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE)
                .stream().map(ClubMembership::getUserId).collect(Collectors.toList());
    }
}
