package com.koval.trainingplannerbackend.club.session;

import com.koval.trainingplannerbackend.club.Club;
import com.koval.trainingplannerbackend.club.ClubProperties;
import com.koval.trainingplannerbackend.club.ClubRepository;
import com.koval.trainingplannerbackend.club.activity.ClubActivityService;
import com.koval.trainingplannerbackend.club.activity.ClubActivityType;
import com.koval.trainingplannerbackend.club.dto.CalendarClubSessionResponse;
import com.koval.trainingplannerbackend.club.dto.CreateSessionRequest;
import com.koval.trainingplannerbackend.club.group.ClubGroup;
import com.koval.trainingplannerbackend.club.group.ClubGroupRepository;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.club.recurring.RecurringSessionMaterializer;
import com.koval.trainingplannerbackend.club.recurring.RecurringSessionTemplate;
import com.koval.trainingplannerbackend.club.recurring.RecurringSessionTemplateRepository;
import com.koval.trainingplannerbackend.notification.NotificationService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ClubSessionService {

    private final ClubTrainingSessionRepository sessionRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubRepository clubRepository;
    private final ClubGroupRepository clubGroupRepository;
    private final ClubAuthorizationService authorizationService;
    private final SessionTrainingLinkService trainingLinkService;
    private final NotificationService notificationService;
    private final ClubActivityService activityService;
    private final RecurringSessionMaterializer materializer;
    private final RecurringSessionTemplateRepository templateRepository;
    private final ClubProperties clubProperties;

    public ClubSessionService(ClubTrainingSessionRepository sessionRepository,
                              ClubMembershipRepository membershipRepository,
                              ClubRepository clubRepository,
                              ClubGroupRepository clubGroupRepository,
                              ClubAuthorizationService authorizationService,
                              SessionTrainingLinkService trainingLinkService,
                              NotificationService notificationService,
                              ClubActivityService activityService,
                              RecurringSessionMaterializer materializer,
                              RecurringSessionTemplateRepository templateRepository,
                              ClubProperties clubProperties) {
        this.sessionRepository = sessionRepository;
        this.membershipRepository = membershipRepository;
        this.clubRepository = clubRepository;
        this.clubGroupRepository = clubGroupRepository;
        this.authorizationService = authorizationService;
        this.trainingLinkService = trainingLinkService;
        this.notificationService = notificationService;
        this.activityService = activityService;
        this.materializer = materializer;
        this.templateRepository = templateRepository;
        this.clubProperties = clubProperties;
    }

    public ClubTrainingSession createSession(String userId, String clubId, CreateSessionRequest req) {
        SessionCategory cat = Optional.ofNullable(req.category()).orElse(SessionCategory.SCHEDULED);
        if (cat == SessionCategory.OPEN) {
            authorizationService.requireActiveMember(userId, clubId);
        } else {
            authorizationService.requireAdminOrCoach(userId, clubId);
        }

        ClubTrainingSession session = new ClubTrainingSession();
        session.setClubId(clubId);
        session.setCreatedBy(userId);
        session.setCreatedAt(LocalDateTime.now());
        SessionPropertyMapper.applyRequest(req, session);
        trainingLinkService.enrichFromLinkedTraining(session);
        session = sessionRepository.save(session);

        activityService.emitActivity(clubId, ClubActivityType.SESSION_CREATED, userId, session.getId(), session.getTitle());

        String prefType = (cat == SessionCategory.OPEN) ? "openSessionCreated" : "clubSessionCreated";
        String notifPrefix = (cat == SessionCategory.OPEN) ? "Open Session" : "New Session";
        notifyClubMembers(clubId, session,
                getClubName(clubId) + " — " + notifPrefix,
                "\"" + session.getTitle() + "\" — " + formatSessionDate(session),
                "SESSION_CREATED", prefType);

        return session;
    }

    /**
     * Duplicate an existing session within the same club. The copy keeps all session
     * properties (linked trainings, location, group, capacity) but resets participants,
     * waiting list, and cancellation. The new {@code scheduledAt} defaults to the
     * original time + 7 days when {@code newScheduledAt} is null.
     */
    public ClubTrainingSession duplicateSession(String userId, String clubId, String sessionId,
                                                LocalDateTime newScheduledAt) {
        ClubTrainingSession source = materializer.resolveOrMaterialize(sessionId);
        if (!source.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Session does not belong to this club");
        }
        SessionCategory cat = Optional.ofNullable(source.getCategory()).orElse(SessionCategory.SCHEDULED);
        if (cat == SessionCategory.OPEN) {
            authorizationService.requireActiveMember(userId, clubId);
        } else {
            authorizationService.requireAdminOrCoach(userId, clubId);
        }

        ClubTrainingSession copy = new ClubTrainingSession();
        copy.setClubId(clubId);
        copy.setCreatedBy(userId);
        copy.setCreatedAt(LocalDateTime.now());
        copy.setTitle(source.getTitle());
        copy.setSport(source.getSport());
        copy.setScheduledAt(Optional.ofNullable(newScheduledAt)
                .or(() -> Optional.ofNullable(source.getScheduledAt()).map(d -> d.plusDays(7)))
                .orElseGet(() -> LocalDateTime.now().plusDays(7)));
        copy.setLocation(source.getLocation());
        copy.setMeetingPointLat(source.getMeetingPointLat());
        copy.setMeetingPointLon(source.getMeetingPointLon());
        copy.setDescription(source.getDescription());
        copy.setLinkedTrainingId(source.getLinkedTrainingId());
        copy.setLinkedTrainingTitle(source.getLinkedTrainingTitle());
        copy.setLinkedTrainingDescription(source.getLinkedTrainingDescription());
        copy.setLinkedTrainings(Optional.ofNullable(source.getLinkedTrainings())
                .<List<GroupLinkedTraining>>map(ArrayList::new)
                .orElseGet(ArrayList::new));
        copy.setClubGroupId(source.getClubGroupId());
        copy.setOpenToAll(source.getOpenToAll());
        copy.setOpenToAllDelayValue(source.getOpenToAllDelayValue());
        copy.setOpenToAllDelayUnit(source.getOpenToAllDelayUnit());
        copy.setResponsibleCoachId(source.getResponsibleCoachId());
        copy.setMaxParticipants(source.getMaxParticipants());
        copy.setDurationMinutes(source.getDurationMinutes());
        copy.setCategory(cat);
        copy.setGpxFileName(source.getGpxFileName());
        copy.setRouteCoordinates(source.getRouteCoordinates());
        copy.setGpxData(source.getGpxData());
        copy.setParticipantIds(new java.util.ArrayList<>());
        copy.setWaitingList(new java.util.ArrayList<>());
        copy.setCancelled(false);
        copy.setCancellationReason(null);
        copy.setCancelledAt(null);
        copy.setRecurringTemplateId(null);

        ClubTrainingSession saved = sessionRepository.save(copy);
        activityService.emitActivity(clubId, ClubActivityType.SESSION_CREATED, userId, saved.getId(), saved.getTitle());
        return saved;
    }

    public List<ClubTrainingSession> listSessions(String userId, String clubId) {
        return listSessions(userId, clubId, (SessionCategory) null);
    }

    public List<ClubTrainingSession> listSessions(String userId, String clubId, SessionCategory category) {
        List<ClubTrainingSession> all = category != null
                ? sessionRepository.findByClubIdAndCategoryOrderByScheduledAtDesc(clubId, category)
                : sessionRepository.findByClubIdOrderByScheduledAtDesc(clubId);
        LocalDate today = LocalDate.now();
        LocalDateTime defaultFrom = today.minusWeeks(clubProperties.getSession().getDefaultPastWeeks()).atStartOfDay();
        LocalDateTime defaultTo = today.plusWeeks(clubProperties.getSession().getDefaultFutureWeeks()).atStartOfDay();
        List<ClubTrainingSession> merged = mergeWithVirtuals(clubId, defaultFrom, defaultTo, all, category);
        return filterByGroupVisibility(userId, clubId, merged);
    }

    public List<ClubTrainingSession> listSessions(String userId, String clubId, LocalDateTime from, LocalDateTime to) {
        return listSessions(userId, clubId, null, from, to);
    }

    public List<ClubTrainingSession> listSessions(String userId, String clubId, SessionCategory category,
                                                    LocalDateTime from, LocalDateTime to) {
        List<ClubTrainingSession> materialized = category != null
                ? sessionRepository.findByClubIdAndCategoryAndScheduledAtBetween(clubId, category, from, to)
                : sessionRepository.findByClubIdAndScheduledAtBetween(clubId, from, to);
        List<ClubTrainingSession> merged = mergeWithVirtuals(clubId, from, to, materialized, category);
        return filterByGroupVisibility(userId, clubId, merged);
    }

    public ClubTrainingSession cancelEntireSession(String userId, String clubId, String sessionId, String reason) {
        ClubTrainingSession session = materializer.resolveOrMaterialize(sessionId);
        if (!session.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Session does not belong to this club");
        }
        authorizeSessionModification(userId, clubId, session);
        if (Boolean.TRUE.equals(session.getCancelled())) {
            throw new IllegalStateException("Session is already cancelled");
        }
        session.setCancelled(true);
        session.setCancellationReason(reason);
        session.setCancelledAt(LocalDateTime.now());
        ClubTrainingSession saved = sessionRepository.save(session);
        activityService.emitActivity(clubId, ClubActivityType.SESSION_CANCELLED, userId, saved.getId(), saved.getTitle());

        if (!saved.getParticipantIds().isEmpty()) {
            String body = getClubName(clubId) + " — \"" + saved.getTitle() + "\" (" + formatSessionDate(saved) + ") has been cancelled"
                    + (reason != null && !reason.isBlank() ? ": " + reason : "");
            notificationService.sendToUsers(
                    saved.getParticipantIds(),
                    "Session Cancelled",
                    body,
                    Map.of("type", "SESSION_CANCELLED",
                           "clubId", clubId,
                           "sessionId", saved.getId()),
                    "clubSessionCancelled");
        }

        return saved;
    }

    public ClubTrainingSession updateSession(String userId, String clubId, String sessionId,
                                              CreateSessionRequest req) {
        ClubTrainingSession session = materializer.resolveOrMaterialize(sessionId);
        if (!session.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Session does not belong to this club");
        }
        authorizeSessionModification(userId, clubId, session);
        if (Boolean.TRUE.equals(session.getCancelled())) {
            throw new IllegalStateException("Cannot update a cancelled session");
        }
        String previousLinkedTrainingId = session.getLinkedTrainingId();
        SessionPropertyMapper.applyRequest(req, session);
        if (req.linkedTrainingId() != null && !req.linkedTrainingId().equals(previousLinkedTrainingId)) {
            trainingLinkService.enrichFromLinkedTraining(session);
        }
        return sessionRepository.save(session);
    }

    public List<CalendarClubSessionResponse> getMyClubSessionsForCalendar(String userId, LocalDate start, LocalDate end) {
        List<ClubMembership> memberships = membershipRepository.findByUserId(userId).stream()
                .filter(m -> m.getStatus() == ClubMemberStatus.ACTIVE)
                .toList();
        if (memberships.isEmpty()) return List.of();

        List<String> clubIds = memberships.stream().map(ClubMembership::getClubId).toList();
        Map<String, Club> clubMap = clubRepository.findAllById(clubIds).stream()
                .collect(Collectors.toMap(Club::getId, c -> c));

        LocalDateTime fromDt = start.atStartOfDay();
        LocalDateTime toDt = end.plusDays(1).atStartOfDay();
        List<ClubTrainingSession> materialized = sessionRepository.findByClubIdInAndScheduledAtBetween(
                clubIds, fromDt, toDt);

        Set<String> coveredKeys = buildCoveredKeys(materialized);
        List<RecurringSessionTemplate> templates = templateRepository
                .findActiveAndNotExpiredForClubs(clubIds, LocalDate.now());
        List<ClubTrainingSession> virtuals = materializer.synthesizeVirtuals(templates, start, end, coveredKeys);

        List<ClubTrainingSession> sessions = new ArrayList<>(materialized);
        sessions.addAll(virtuals);
        sessions.sort(Comparator.comparing(ClubTrainingSession::getScheduledAt));

        // Single group lookup powers both the user's group set and the id→name map.
        List<ClubGroup> allGroups = clubGroupRepository.findByClubIdIn(clubIds);
        Set<String> userGroupIds = allGroups.stream()
                .filter(g -> g.getMemberIds() != null && g.getMemberIds().contains(userId))
                .map(ClubGroup::getId)
                .collect(Collectors.toSet());
        Map<String, String> groupNameMap = allGroups.stream()
                .collect(Collectors.toMap(ClubGroup::getId, ClubGroup::getName));

        LocalDateTime now = LocalDateTime.now();
        List<CalendarClubSessionResponse> result = new ArrayList<>();
        for (ClubTrainingSession s : sessions) {
            if (!isVisibleToUser(s, userGroupIds, now)) continue;

            Club club = clubMap.get(s.getClubId());
            if (club == null) continue;

            result.add(toCalendarResponse(s, userId, club, groupNameMap, userGroupIds));
        }
        return result;
    }

    private boolean isVisibleToUser(ClubTrainingSession s, Set<String> userGroupIds, LocalDateTime now) {
        if (s.getClubGroupId() == null || s.getClubGroupId().isBlank()) return true;
        if (userGroupIds.contains(s.getClubGroupId())) return true;
        LocalDateTime openFrom = s.computeOpenToAllFrom();
        return openFrom != null && !now.isBefore(openFrom);
    }

    private List<ClubTrainingSession> mergeWithVirtuals(String clubId,
                                                        LocalDateTime from, LocalDateTime to,
                                                        List<ClubTrainingSession> materialized,
                                                        SessionCategory categoryFilter) {
        LocalDate fromDate = from.toLocalDate();
        LocalDate toDate = to.toLocalDate();
        Set<String> coveredKeys = buildCoveredKeys(materialized);
        List<RecurringSessionTemplate> templates = templateRepository
                .findActiveAndNotExpiredForClub(clubId, LocalDate.now());
        if (categoryFilter != null) {
            templates = templates.stream()
                    .filter(t -> categoryFilter.equals(t.getCategory()))
                    .toList();
        }
        List<ClubTrainingSession> virtuals = materializer.synthesizeVirtuals(templates, fromDate, toDate, coveredKeys);
        if (virtuals.isEmpty()) return materialized;
        List<ClubTrainingSession> merged = new ArrayList<>(materialized);
        merged.addAll(virtuals);
        merged.sort(Comparator.comparing(ClubTrainingSession::getScheduledAt).reversed());
        return merged;
    }

    private Set<String> buildCoveredKeys(List<ClubTrainingSession> materialized) {
        Set<String> keys = new HashSet<>();
        for (ClubTrainingSession s : materialized) {
            if (s.getRecurringTemplateId() != null && s.getScheduledAt() != null) {
                keys.add(s.getRecurringTemplateId() + ":" + s.getScheduledAt().toLocalDate());
            }
        }
        return keys;
    }

    private CalendarClubSessionResponse toCalendarResponse(ClubTrainingSession s, String userId,
                                                            Club club, Map<String, String> groupNameMap,
                                                            Set<String> userGroupIds) {
        boolean joined = s.getParticipantIds().contains(userId);
        boolean onWaitingList = s.isOnWaitingList(userId);
        int waitingListPosition = SessionParticipationService.computeWaitingListPosition(s, userId);

        GroupLinkedTraining resolved = trainingLinkService.resolveUserLinkedTraining(s, userGroupIds);

        List<GroupLinkedTraining> effective = s.getEffectiveLinkedTrainings();
        List<CalendarClubSessionResponse.CalendarLinkedTraining> linkedTrainings = effective.stream()
                .map(glt -> new CalendarClubSessionResponse.CalendarLinkedTraining(
                        glt.getTrainingId(),
                        glt.getTrainingTitle(),
                        glt.getClubGroupId(),
                        Optional.ofNullable(glt.getClubGroupId())
                                .map(id -> groupNameMap.getOrDefault(id, glt.getClubGroupName()))
                                .orElse(null),
                        glt.getClubGroupId() == null || userGroupIds.contains(glt.getClubGroupId())
                )).toList();

        return new CalendarClubSessionResponse(
                s.getId(), s.getClubId(), club.getName(), s.getTitle(), s.getSport(),
                s.getScheduledAt(), s.getLocation(), s.getDescription(),
                s.getDurationMinutes(), s.getParticipantIds(),
                s.getMaxParticipants(), s.getClubGroupId(),
                groupNameMap.get(s.getClubGroupId()),
                joined, onWaitingList, waitingListPosition,
                s.computeOpenToAllFrom(),
                Boolean.TRUE.equals(s.getCancelled()), s.getCancellationReason(),
                Optional.ofNullable(resolved).map(GroupLinkedTraining::getTrainingId).orElse(null),
                Optional.ofNullable(resolved).map(GroupLinkedTraining::getTrainingTitle).orElse(null),
                Optional.ofNullable(resolved).map(GroupLinkedTraining::getTrainingDescription).orElse(null),
                linkedTrainings);
    }

    private List<ClubTrainingSession> filterByGroupVisibility(String userId, String clubId,
                                                               List<ClubTrainingSession> sessions) {
        if (authorizationService.isAdminOrCoach(userId, clubId)) {
            return sessions;
        }
        Set<String> userGroupIds = clubGroupRepository.findByClubIdAndMemberIdsContaining(clubId, userId)
                .stream().map(ClubGroup::getId).collect(Collectors.toSet());
        return sessions.stream().filter(s -> {
            if (s.getClubGroupId() == null || s.getClubGroupId().isBlank()) return true;
            if (userGroupIds.contains(s.getClubGroupId())) return true;
            LocalDateTime openFrom = s.computeOpenToAllFrom();
            return openFrom != null && !LocalDateTime.now().isBefore(openFrom);
        }).toList();
    }

    private void notifyClubMembers(String clubId, ClubTrainingSession session,
                                     String title, String body, String type, String preferenceType) {
        List<String> memberIds = membershipRepository.findByClubId(clubId).stream()
                .filter(m -> m.getStatus() == ClubMemberStatus.ACTIVE)
                .map(ClubMembership::getUserId)
                .filter(id -> !id.equals(session.getCreatedBy()))
                .toList();
        if (!memberIds.isEmpty()) {
            notificationService.sendToUsers(memberIds, title, body,
                    Map.of("type", type,
                           "clubId", clubId,
                           "sessionId", session.getId()),
                    preferenceType);
        }
    }

    private String getClubName(String clubId) {
        return clubRepository.findById(clubId).map(Club::getName).orElse("Club");
    }

    private String formatSessionDate(ClubTrainingSession session) {
        return Optional.ofNullable(session.getScheduledAt())
                .map(SessionPropertyMapper.SESSION_DATE_FMT::format)
                .orElse("");
    }

    private void authorizeSessionModification(String userId, String clubId, ClubTrainingSession session) {
        if (session.getCategory() == SessionCategory.OPEN && session.getCreatedBy().equals(userId)) {
            authorizationService.requireActiveMember(userId, clubId);
        } else {
            authorizationService.requireAdminOrCoach(userId, clubId);
        }
    }
}
