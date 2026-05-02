package com.koval.trainingplannerbackend.club.gazette;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.club.Club;
import com.koval.trainingplannerbackend.club.ClubRepository;
import com.koval.trainingplannerbackend.club.gazette.dto.ClubGazetteEditionResponse;
import com.koval.trainingplannerbackend.club.gazette.dto.ClubGazetteEditionSummary;
import com.koval.trainingplannerbackend.club.gazette.dto.ClubGazettePayloadResponse;
import com.koval.trainingplannerbackend.club.gazette.dto.ClubGazettePostResponse;
import com.koval.trainingplannerbackend.club.gazette.dto.ClubGazettePostsResponse;
import com.koval.trainingplannerbackend.club.gazette.dto.CreateGazettePostRequest;
import com.koval.trainingplannerbackend.club.gazette.dto.UpdateGazettePostRequest;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSessionRepository;
import com.koval.trainingplannerbackend.goal.RaceGoal;
import com.koval.trainingplannerbackend.goal.RaceGoalRepository;
import com.koval.trainingplannerbackend.media.MediaPurpose;
import com.koval.trainingplannerbackend.media.MediaService;
import com.koval.trainingplannerbackend.media.dto.MediaResponse;
import com.koval.trainingplannerbackend.race.Race;
import com.koval.trainingplannerbackend.race.RaceService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Core service for the Club Gazette feature.
 *
 * <p>Phase 2 — edition lifecycle (lazy creation, listing, fetching, discarding,
 * mark-as-read, comment).
 *
 * <p>Phase 3 — member posts (CRUD with type/link validation, visibility rules).
 *
 * <p>Phases 4 / 5 are still stubs.
 */
@Service
public class ClubGazetteService {

    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final int MAX_MEDIA_PER_POST = 4;

    private final ClubGazetteEditionRepository editionRepository;
    private final ClubGazettePostRepository postRepository;
    private final ClubAuthorizationService authorizationService;
    private final UserService userService;
    private final ClubTrainingSessionRepository clubSessionRepository;
    private final RaceGoalRepository raceGoalRepository;
    private final RaceService raceService;
    private final MediaService mediaService;
    private final ClubGazetteSnapshotService snapshotService;
    private final ClubRepository clubRepository;

    public ClubGazetteService(ClubGazetteEditionRepository editionRepository,
                              ClubGazettePostRepository postRepository,
                              ClubAuthorizationService authorizationService,
                              UserService userService,
                              ClubTrainingSessionRepository clubSessionRepository,
                              RaceGoalRepository raceGoalRepository,
                              RaceService raceService,
                              MediaService mediaService,
                              ClubGazetteSnapshotService snapshotService,
                              ClubRepository clubRepository) {
        this.editionRepository = editionRepository;
        this.postRepository = postRepository;
        this.authorizationService = authorizationService;
        this.userService = userService;
        this.clubSessionRepository = clubSessionRepository;
        this.raceGoalRepository = raceGoalRepository;
        this.raceService = raceService;
        this.mediaService = mediaService;
        this.snapshotService = snapshotService;
        this.clubRepository = clubRepository;
    }

    // ── Editions ─────────────────────────────────────────────────────────────

    public List<ClubGazetteEditionSummary> listEditions(String userId, String clubId,
                                                        int page, int size) {
        authorizationService.requireActiveMember(userId, clubId);
        boolean canSeeDrafts = authorizationService.isAdminOrCoach(userId, clubId);
        return editionRepository
                .findByClubIdOrderByPeriodStartDesc(clubId, PageRequest.of(page, size))
                .stream()
                .filter(e -> canSeeDrafts || e.getStatus() != GazetteStatus.DRAFT)
                .map(ClubGazetteEditionSummary::from)
                .toList();
    }

    public ClubGazetteEditionResponse getEdition(String userId, String clubId, String editionId) {
        authorizationService.requireActiveMember(userId, clubId);
        ClubGazetteEdition edition = requireEditionInClub(clubId, editionId);
        return ClubGazetteEditionResponse.from(edition);
    }

    public ClubGazetteEditionResponse getCurrentDraft(String userId, String clubId) {
        authorizationService.requireActiveMember(userId, clubId);
        ClubGazetteEdition draft = findOrCreateDraftForPeriodContaining(clubId, LocalDateTime.now());
        return ClubGazetteEditionResponse.from(draft);
    }

    public List<ClubGazetteEditionResponse> getOpenDrafts(String userId, String clubId) {
        authorizationService.requireActiveMember(userId, clubId);
        findOrCreateDraftForPeriodContaining(clubId, LocalDateTime.now());
        return editionRepository
                .findByClubIdAndStatusOrderByPeriodStartDesc(clubId, GazetteStatus.DRAFT)
                .stream()
                .map(ClubGazetteEditionResponse::from)
                .toList();
    }

    public void discardDraft(String userId, String editionId) {
        ClubGazetteEdition edition = editionRepository.findById(editionId)
                .orElseThrow(() -> new IllegalArgumentException("Edition not found"));
        authorizationService.requireAdminOrCoach(userId, edition.getClubId());
        if (edition.getStatus() != GazetteStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT editions can be discarded");
        }
        postRepository.deleteByEditionId(editionId);
        editionRepository.delete(edition);
    }

    public byte[] getPdfBytes(String userId, String clubId, String editionId) {
        authorizationService.requireActiveMember(userId, clubId);
        ClubGazetteEdition edition = requireEditionInClub(clubId, editionId);
        if (edition.getStatus() != GazetteStatus.PUBLISHED) {
            return null;
        }
        return edition.getPdfData();
    }

    // ── Engagement on published editions ─────────────────────────────────────

    public void markAsRead(String userId, String editionId) {
        ClubGazetteEdition edition = editionRepository.findById(editionId)
                .orElseThrow(() -> new IllegalArgumentException("Edition not found"));
        authorizationService.requireActiveMember(userId, edition.getClubId());
        if (edition.getStatus() != GazetteStatus.PUBLISHED) {
            throw new IllegalStateException("Only published editions can be marked as read");
        }
        if (edition.getReadBy().add(userId)) {
            edition.setViewCount(edition.getViewCount() + 1);
            editionRepository.save(edition);
        }
    }

    public ClubGazetteEdition.CommentEntry addComment(String userId, String editionId, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Comment content is required");
        }
        ClubGazetteEdition edition = editionRepository.findById(editionId)
                .orElseThrow(() -> new IllegalArgumentException("Edition not found"));
        authorizationService.requireActiveMember(userId, edition.getClubId());
        if (edition.getStatus() != GazetteStatus.PUBLISHED) {
            throw new IllegalStateException("Comments only allowed on published editions");
        }
        User author = userService.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        ClubGazetteEdition.CommentEntry entry = new ClubGazetteEdition.CommentEntry(
                UUID.randomUUID().toString(),
                userId,
                author.getDisplayName(),
                author.getProfilePicture(),
                content.trim(),
                LocalDateTime.now());
        edition.getComments().add(entry);
        editionRepository.save(edition);
        return entry;
    }

    // ── Posts ────────────────────────────────────────────────────────────────

    public ClubGazettePostsResponse listPosts(String userId, String editionId) {
        ClubGazetteEdition edition = editionRepository.findById(editionId)
                .orElseThrow(() -> new IllegalArgumentException("Edition not found"));
        authorizationService.requireActiveMember(userId, edition.getClubId());
        boolean isAdmin = authorizationService.isAdminOrCoach(userId, edition.getClubId());

        List<ClubGazettePost> posts;
        long othersDraftCount = 0;

        if (edition.getStatus() == GazetteStatus.PUBLISHED) {
            posts = postRepository.findByEditionIdOrderByCreatedAtAsc(editionId).stream()
                    .filter(p -> !p.isExcluded())
                    .sorted(Comparator.comparing(
                            ClubGazettePost::getDisplayOrder,
                            Comparator.nullsLast(Integer::compareTo)))
                    .toList();
        } else if (isAdmin) {
            posts = postRepository.findByEditionIdOrderByCreatedAtAsc(editionId);
        } else {
            posts = postRepository.findByEditionIdAndAuthorIdOrderByCreatedAtAsc(editionId, userId);
            othersDraftCount = postRepository.countByEditionIdAndAuthorIdNot(editionId, userId);
        }

        List<ClubGazettePostResponse> dtos = posts.stream().map(this::toPostResponse).toList();
        return new ClubGazettePostsResponse(dtos, othersDraftCount);
    }

    public ClubGazettePostResponse createPost(String userId, String editionId,
                                              CreateGazettePostRequest req) {
        ClubGazetteEdition edition = editionRepository.findById(editionId)
                .orElseThrow(() -> new IllegalArgumentException("Edition not found"));
        if (edition.getStatus() != GazetteStatus.DRAFT) {
            throw new IllegalStateException("Posts can only be added to DRAFT editions");
        }
        authorizationService.requireActiveMember(userId, edition.getClubId());

        if (req == null || req.type() == null) {
            throw new IllegalArgumentException("Post type is required");
        }
        validateContent(req.title(), req.content());
        validateLinksForType(req.type(), req.linkedSessionId(), req.linkedRaceGoalId());
        validateMediaIds(userId, req.mediaIds());

        ClubGazettePost post = new ClubGazettePost();
        post.setId(UUID.randomUUID().toString());
        post.setEditionId(editionId);
        post.setClubId(edition.getClubId());
        post.setAuthorId(userId);

        User author = userService.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        post.setAuthorDisplayName(author.getDisplayName());
        post.setAuthorProfilePicture(author.getProfilePicture());

        post.setType(req.type());
        post.setTitle(trimToNull(req.title()));
        post.setContent(req.content().trim());

        applyLink(post, edition, userId, req.linkedSessionId(), req.linkedRaceGoalId());

        post.setMediaIds(Optional.ofNullable(req.mediaIds())
                .<List<String>>map(ArrayList::new)
                .orElseGet(ArrayList::new));
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(post.getCreatedAt());

        postRepository.save(post);
        return toPostResponse(post);
    }

    public ClubGazettePostResponse updatePost(String userId, String postId,
                                              UpdateGazettePostRequest req) {
        ClubGazettePost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
        if (!post.getAuthorId().equals(userId)) {
            throw new IllegalStateException("Only the author can edit this post");
        }
        ClubGazetteEdition edition = editionRepository.findById(post.getEditionId())
                .orElseThrow(() -> new IllegalStateException("Edition not found"));
        if (edition.getStatus() != GazetteStatus.DRAFT) {
            throw new IllegalStateException("Cannot edit a post on a published edition");
        }

        if (req.title() != null || req.content() != null) {
            String newTitle = Optional.ofNullable(req.title()).orElse(post.getTitle());
            String newContent = Optional.ofNullable(req.content()).orElse(post.getContent());
            validateContent(newTitle, newContent);
            post.setTitle(trimToNull(newTitle));
            post.setContent(newContent.trim());
        }

        // Link can be modified — re-validate against the post's existing type
        if (req.linkedSessionId() != null || req.linkedRaceGoalId() != null) {
            validateLinksForType(post.getType(), req.linkedSessionId(), req.linkedRaceGoalId());
            applyLink(post, edition, userId, req.linkedSessionId(), req.linkedRaceGoalId());
        }

        if (req.mediaIds() != null) {
            validateMediaIds(userId, req.mediaIds());
            post.setMediaIds(new ArrayList<>(req.mediaIds()));
        }

        post.setUpdatedAt(LocalDateTime.now());
        postRepository.save(post);
        return toPostResponse(post);
    }

    public void deletePost(String userId, String postId) {
        ClubGazettePost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
        ClubGazetteEdition edition = editionRepository.findById(post.getEditionId())
                .orElseThrow(() -> new IllegalStateException("Edition not found"));
        if (edition.getStatus() != GazetteStatus.DRAFT) {
            throw new IllegalStateException("Cannot delete a post on a published edition");
        }
        boolean isAuthor = post.getAuthorId().equals(userId);
        boolean isAdmin = authorizationService.isAdminOrCoach(userId, post.getClubId());
        if (!isAuthor && !isAdmin) {
            throw new IllegalStateException("Only the author or an admin can delete this post");
        }
        postRepository.delete(post);
    }

    public List<ClubGazettePostResponse> getMyPostsInCurrentDraft(String userId, String clubId) {
        authorizationService.requireActiveMember(userId, clubId);
        ClubGazetteEdition draft = findOrCreateDraftForPeriodContaining(clubId, LocalDateTime.now());
        return postRepository
                .findByEditionIdAndAuthorIdOrderByCreatedAtAsc(draft.getId(), userId)
                .stream()
                .map(this::toPostResponse)
                .toList();
    }

    // ── Admin / MCP ──────────────────────────────────────────────────────────

    /**
     * Rich payload for Claude: edition meta, all draft posts (with photos
     * resolved to signed URLs), and live previews of every auto-curated
     * section so the admin can decide what to include before publishing.
     */
    public ClubGazettePayloadResponse getPayload(String userId, String editionId) {
        ClubGazetteEdition edition = editionRepository.findById(editionId)
                .orElseThrow(() -> new IllegalArgumentException("Edition not found"));
        authorizationService.requireAdminOrCoach(userId, edition.getClubId());

        String clubName = clubRepository.findById(edition.getClubId())
                .map(Club::getName).orElse(null);

        List<ClubGazettePostResponse> posts = postRepository
                .findByEditionIdOrderByCreatedAtAsc(edition.getId())
                .stream()
                .map(this::toPostResponse)
                .toList();

        return new ClubGazettePayloadResponse(
                edition.getId(),
                edition.getClubId(),
                clubName,
                edition.getEditionNumber(),
                edition.getPeriodStart(),
                edition.getPeriodEnd(),
                edition.getStatus(),
                posts,
                snapshotService.computeStats(edition.getClubId(), edition.getPeriodStart(), edition.getPeriodEnd()),
                snapshotService.computeLeaderboard(edition.getClubId(), edition.getPeriodStart(), edition.getPeriodEnd()),
                snapshotService.computeTopSessions(edition.getClubId(), edition.getPeriodStart(), edition.getPeriodEnd()),
                snapshotService.computeMostActiveMembers(edition.getClubId(), edition.getPeriodStart(), edition.getPeriodEnd()),
                snapshotService.computeMilestones(edition.getClubId(), edition.getPeriodStart(), edition.getPeriodEnd()));
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private ClubGazettePostResponse toPostResponse(ClubGazettePost post) {
        List<MediaResponse> photos = new ArrayList<>();
        if (post.getMediaIds() != null) {
            for (String mediaId : post.getMediaIds()) {
                mediaService.findById(mediaId)
                        .map(mediaService::buildMediaResponse)
                        .ifPresent(photos::add);
            }
        }
        return ClubGazettePostResponse.from(post, photos);
    }

    private void validateContent(String title, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Post content is required");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException(
                    "Content exceeds maximum length of " + MAX_CONTENT_LENGTH);
        }
        if (title != null && title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException(
                    "Title exceeds maximum length of " + MAX_TITLE_LENGTH);
        }
    }

    private void validateLinksForType(GazettePostType type, String sessionId, String raceGoalId) {
        switch (type) {
            case SESSION_RECAP -> {
                if (sessionId == null || sessionId.isBlank()) {
                    throw new IllegalArgumentException("SESSION_RECAP requires linkedSessionId");
                }
                if (raceGoalId != null && !raceGoalId.isBlank()) {
                    throw new IllegalArgumentException("SESSION_RECAP cannot also link a race goal");
                }
            }
            case RACE_RESULT -> {
                if (raceGoalId == null || raceGoalId.isBlank()) {
                    throw new IllegalArgumentException("RACE_RESULT requires linkedRaceGoalId");
                }
                if (sessionId != null && !sessionId.isBlank()) {
                    throw new IllegalArgumentException("RACE_RESULT cannot also link a session");
                }
            }
            case PERSONAL_WIN, SHOUTOUT, REFLECTION -> {
                if ((sessionId != null && !sessionId.isBlank())
                        || (raceGoalId != null && !raceGoalId.isBlank())) {
                    throw new IllegalArgumentException(type + " posts cannot have a link");
                }
            }
        }
    }

    private void applyLink(ClubGazettePost post, ClubGazetteEdition edition, String userId,
                           String sessionId, String raceGoalId) {
        post.setLinkedSessionId(null);
        post.setLinkedRaceGoalId(null);
        post.setLinkedSessionSnapshot(null);
        post.setLinkedRaceGoalSnapshot(null);

        if (post.getType() == GazettePostType.SESSION_RECAP) {
            ClubTrainingSession session = clubSessionRepository.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Linked session not found"));
            if (!session.getClubId().equals(post.getClubId())) {
                throw new IllegalArgumentException("Linked session does not belong to this club");
            }
            if (!session.getParticipantIds().contains(userId)) {
                throw new IllegalArgumentException("You must be a participant of the linked session");
            }
            if (session.getScheduledAt() == null
                    || session.getScheduledAt().isBefore(edition.getPeriodStart())
                    || !session.getScheduledAt().isBefore(edition.getPeriodEnd())) {
                throw new IllegalArgumentException(
                        "Linked session is not within the gazette period");
            }
            post.setLinkedSessionId(sessionId);
            post.setLinkedSessionSnapshot(new ClubGazettePost.LinkedSessionSnapshot(
                    session.getId(), session.getTitle(), session.getSport(),
                    session.getScheduledAt(), session.getLocation()));
        } else if (post.getType() == GazettePostType.RACE_RESULT) {
            RaceGoal goal = raceGoalRepository.findById(raceGoalId)
                    .orElseThrow(() -> new IllegalArgumentException("Linked race goal not found"));
            if (!goal.getAthleteId().equals(userId)) {
                throw new IllegalArgumentException("Race goal must belong to you");
            }
            LocalDate raceDate = resolveRaceDate(goal);
            if (raceDate == null) {
                throw new IllegalArgumentException("Linked race has no date set");
            }
            LocalDateTime raceAt = raceDate.atStartOfDay();
            if (raceAt.isBefore(edition.getPeriodStart())
                    || !raceAt.isBefore(edition.getPeriodEnd())) {
                throw new IllegalArgumentException(
                        "Linked race is not within the gazette period");
            }
            post.setLinkedRaceGoalId(raceGoalId);
            post.setLinkedRaceGoalSnapshot(new ClubGazettePost.LinkedRaceGoalSnapshot(
                    goal.getId(), goal.getTitle(), goal.getSport(),
                    raceDate, goal.getDistance(), goal.getTargetTime(), null));
        }
    }

    private LocalDate resolveRaceDate(RaceGoal goal) {
        if (goal.getRaceId() == null) return null;
        try {
            Race race = raceService.getRaceById(goal.getRaceId());
            return Optional.ofNullable(race.getScheduledDate()).map(LocalDate::parse).orElse(null);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    private void validateMediaIds(String userId, List<String> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) return;
        if (mediaIds.size() > MAX_MEDIA_PER_POST) {
            throw new IllegalArgumentException(
                    "A post cannot have more than " + MAX_MEDIA_PER_POST + " photos");
        }
        mediaService.requireOwnedAndConfirmed(userId, mediaIds, MediaPurpose.GAZETTE_POST);
    }

    private ClubGazetteEdition requireEditionInClub(String clubId, String editionId) {
        ClubGazetteEdition edition = editionRepository.findById(editionId)
                .orElseThrow(() -> new IllegalArgumentException("Edition not found"));
        if (!edition.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Edition does not belong to this club");
        }
        return edition;
    }

    private ClubGazetteEdition findOrCreateDraftForPeriodContaining(String clubId, LocalDateTime moment) {
        return editionRepository
                .findFirstByClubIdAndStatusAndPeriodStartLessThanEqualAndPeriodEndGreaterThan(
                        clubId, GazetteStatus.DRAFT, moment, moment)
                .orElseGet(() -> {
                    LocalDateTime periodStart = defaultPeriodStart(moment);
                    return createDraft(clubId, periodStart, periodStart.plusDays(7));
                });
    }

    private ClubGazetteEdition createDraft(String clubId, LocalDateTime periodStart,
                                           LocalDateTime periodEnd) {
        ClubGazetteEdition edition = new ClubGazetteEdition();
        edition.setClubId(clubId);
        edition.setEditionNumber(editionRepository.countByClubId(clubId) + 1);
        edition.setPeriodStart(periodStart);
        edition.setPeriodEnd(periodEnd);
        edition.setStatus(GazetteStatus.DRAFT);
        edition.setCreatedAt(LocalDateTime.now());
        return editionRepository.save(edition);
    }

    static LocalDateTime defaultPeriodStart(LocalDateTime moment) {
        LocalDate monday = moment.toLocalDate().with(DayOfWeek.MONDAY);
        return monday.minusDays(1).atTime(23, 59, 59);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
