package com.koval.trainingplannerbackend.club.activity;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.club.dto.ClubActivityResponse;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.notification.NotificationService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ClubActivityService {

    private final ClubActivityRepository activityRepository;
    private final ClubMembershipRepository membershipRepository;
    private final UserService userService;
    private final NotificationService notificationService;
    private final ClubAuthorizationService authorizationService;

    public ClubActivityService(ClubActivityRepository activityRepository,
                               ClubMembershipRepository membershipRepository,
                               UserService userService,
                               NotificationService notificationService,
                               ClubAuthorizationService authorizationService) {
        this.activityRepository = activityRepository;
        this.membershipRepository = membershipRepository;
        this.userService = userService;
        this.notificationService = notificationService;
        this.authorizationService = authorizationService;
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
            notifyMembersExceptActor(clubId, actorId,
                    "New Group Session",
                    "created a training session: " + nonNull(targetTitle),
                    Map.of("type", "SESSION_CREATED",
                           "clubId", clubId,
                           "sessionId", nonNull(targetId)));
        } else if (type == ClubActivityType.SESSION_CANCELLED) {
            notifyMembersExceptActor(clubId, actorId,
                    "Session Cancelled",
                    "cancelled the session: " + nonNull(targetTitle),
                    Map.of("type", "SESSION_CANCELLED",
                           "clubId", clubId,
                           "sessionId", nonNull(targetId)));
        } else if (type == ClubActivityType.RECURRING_SERIES_CANCELLED) {
            notifyMembersExceptActor(clubId, actorId,
                    "Recurring Series Cancelled",
                    "cancelled all future sessions for: " + nonNull(targetTitle),
                    Map.of("type", "RECURRING_SERIES_CANCELLED",
                           "clubId", clubId,
                           "templateId", nonNull(targetId)));
        }
    }

    public List<ClubActivityResponse> getActivityFeed(String userId, String clubId, Pageable pageable) {
        authorizationService.requireActiveMember(userId, clubId);
        List<ClubActivity> activities = activityRepository.findByClubIdOrderByOccurredAtDesc(clubId, pageable);
        List<String> actorIds = activities.stream().map(ClubActivity::getActorId).distinct().toList();
        Map<String, User> userMap = userService.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return activities.stream().map(a -> {
            String actorName = Optional.ofNullable(userMap.get(a.getActorId()))
                    .map(User::getDisplayName)
                    .orElse(a.getActorId());
            return new ClubActivityResponse(
                    a.getId(), a.getType(), a.getActorId(), actorName,
                    a.getTargetId(), a.getTargetTitle(), a.getOccurredAt());
        }).toList();
    }

    private void notifyMembersExceptActor(String clubId, String actorId,
                                           String title, String message,
                                           Map<String, String> data) {
        List<String> memberIds = getActiveMemberIds(clubId);
        memberIds.remove(actorId);
        if (memberIds.isEmpty()) return;
        String actorName = userService.findById(actorId)
                .map(User::getDisplayName)
                .orElse("Someone");
        notificationService.sendToUsers(memberIds, title, actorName + " " + message, data);
    }

    private static String nonNull(String s) {
        return Optional.ofNullable(s).orElse("");
    }

    List<String> getActiveMemberIds(String clubId) {
        return membershipRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE)
                .stream().map(ClubMembership::getUserId).collect(Collectors.toList());
    }
}
