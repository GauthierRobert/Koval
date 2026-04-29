package com.koval.trainingplannerbackend.club.gazette;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.club.feed.ClubFeedEvent;
import com.koval.trainingplannerbackend.club.feed.ClubFeedEventRepository;
import com.koval.trainingplannerbackend.club.feed.ClubFeedEventType;
import com.koval.trainingplannerbackend.club.feed.ClubFeedSseBroadcaster;
import com.koval.trainingplannerbackend.club.feed.dto.ClubFeedEventResponse;
import com.koval.trainingplannerbackend.club.gazette.dto.ClubGazetteEditionResponse;
import com.koval.trainingplannerbackend.club.gazette.dto.PublishGazetteRequest;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 4 — turns a curated set of choices + a Claude-generated PDF into a
 * frozen, published gazette edition.
 *
 * <p>The {@link PublishGazetteRequest} carries the curation: which posts to
 * include, which auto-curated sections to compute, and (optionally) override
 * for the period bounds. Posts not in the inclusion list are kept in the
 * collection but flagged {@code excluded=true} so they don't show up in the
 * published view.
 */
@Service
public class ClubGazettePublisher {

    private static final Logger log = LoggerFactory.getLogger(ClubGazettePublisher.class);

    private static final long MAX_PDF_BYTES = 10L * 1024 * 1024;
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};

    private final ClubGazetteEditionRepository editionRepository;
    private final ClubGazettePostRepository postRepository;
    private final ClubAuthorizationService authorizationService;
    private final ClubGazetteSnapshotService snapshotService;
    private final ClubMembershipRepository membershipRepository;
    private final NotificationService notificationService;
    private final ClubFeedEventRepository feedEventRepository;
    private final ClubFeedSseBroadcaster feedBroadcaster;
    private final UserService userService;

    public ClubGazettePublisher(ClubGazetteEditionRepository editionRepository,
                                ClubGazettePostRepository postRepository,
                                ClubAuthorizationService authorizationService,
                                ClubGazetteSnapshotService snapshotService,
                                ClubMembershipRepository membershipRepository,
                                NotificationService notificationService,
                                ClubFeedEventRepository feedEventRepository,
                                ClubFeedSseBroadcaster feedBroadcaster,
                                UserService userService) {
        this.editionRepository = editionRepository;
        this.postRepository = postRepository;
        this.authorizationService = authorizationService;
        this.snapshotService = snapshotService;
        this.membershipRepository = membershipRepository;
        this.notificationService = notificationService;
        this.feedEventRepository = feedEventRepository;
        this.feedBroadcaster = feedBroadcaster;
        this.userService = userService;
    }

    public ClubGazetteEditionResponse publish(String userId, String editionId,
                                              PublishGazetteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("PublishGazetteRequest is required");
        }
        ClubGazetteEdition edition = editionRepository.findById(editionId)
                .orElseThrow(() -> new IllegalArgumentException("Edition not found"));
        authorizationService.requireAdminOrCoach(userId, edition.getClubId());
        if (edition.getStatus() != GazetteStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT editions can be published");
        }

        byte[] pdfBytes = decodeAndValidatePdf(request.pdfBase64());
        applyCurationToPosts(edition, request.includedPostIds());

        // Period overrides — admin may have shifted the bounds in Claude
        if (request.periodStart() != null) edition.setPeriodStart(request.periodStart());
        if (request.periodEnd() != null) edition.setPeriodEnd(request.periodEnd());
        if (!edition.getPeriodEnd().isAfter(edition.getPeriodStart())) {
            throw new IllegalArgumentException("periodEnd must be after periodStart");
        }

        Set<AutoSection> sections = request.includedSections() == null
                ? Set.of() : new HashSet<>(request.includedSections());
        edition.setIncludedSections(sections);
        applySnapshots(edition, sections);

        edition.setPdfData(pdfBytes);
        edition.setPdfFileName(safeFilename(request.pdfFilename(), edition.getEditionNumber()));
        edition.setPdfGeneratedAt(LocalDateTime.now());
        edition.setPdfSizeBytes((long) pdfBytes.length);

        edition.setStatus(GazetteStatus.PUBLISHED);
        edition.setPublishedAt(LocalDateTime.now());
        edition.setPublishedByUserId(userId);

        editionRepository.save(edition);

        emitGazettePublishedFeedEvent(edition);
        notifyMembers(edition);

        return ClubGazetteEditionResponse.from(edition);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private byte[] decodeAndValidatePdf(String pdfBase64) {
        if (pdfBase64 == null || pdfBase64.isBlank()) {
            throw new IllegalArgumentException("pdfBase64 is required");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(pdfBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("pdfBase64 is not valid base64", e);
        }
        if (bytes.length == 0) {
            throw new IllegalArgumentException("PDF payload is empty");
        }
        if (bytes.length > MAX_PDF_BYTES) {
            throw new IllegalArgumentException("PDF exceeds maximum size of " + MAX_PDF_BYTES + " bytes");
        }
        if (bytes.length < PDF_MAGIC.length) {
            throw new IllegalArgumentException("PDF payload too short");
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (bytes[i] != PDF_MAGIC[i]) {
                throw new IllegalArgumentException("Payload does not start with %PDF- magic header");
            }
        }
        return bytes;
    }

    private void applyCurationToPosts(ClubGazetteEdition edition, List<String> includedPostIds) {
        List<ClubGazettePost> all = postRepository.findByEditionIdOrderByCreatedAtAsc(edition.getId());
        if (includedPostIds == null || includedPostIds.isEmpty()) {
            for (ClubGazettePost p : all) {
                p.setExcluded(true);
                p.setDisplayOrder(null);
            }
            postRepository.saveAll(all);
            return;
        }

        // Validate every included id belongs to this edition
        Map<String, ClubGazettePost> byId = new HashMap<>();
        for (ClubGazettePost p : all) {
            byId.put(p.getId(), p);
        }
        for (String pid : includedPostIds) {
            if (!byId.containsKey(pid)) {
                throw new IllegalArgumentException(
                        "Post " + pid + " does not belong to edition " + edition.getId());
            }
        }

        Set<String> includedSet = new HashSet<>(includedPostIds);
        for (ClubGazettePost p : byId.values()) {
            p.setExcluded(!includedSet.contains(p.getId()));
            p.setDisplayOrder(p.isExcluded() ? null : includedPostIds.indexOf(p.getId()));
        }
        postRepository.saveAll(byId.values());
    }

    private void applySnapshots(ClubGazetteEdition edition, Set<AutoSection> sections) {
        String clubId = edition.getClubId();
        LocalDateTime start = edition.getPeriodStart();
        LocalDateTime end = edition.getPeriodEnd();

        edition.setStatsSnapshot(sections.contains(AutoSection.STATS)
                ? snapshotService.computeStats(clubId, start, end) : null);
        edition.setLeaderboardSnapshot(sections.contains(AutoSection.LEADERBOARD)
                ? snapshotService.computeLeaderboard(clubId, start, end) : List.of());
        edition.setTopSessions(sections.contains(AutoSection.TOP_SESSIONS)
                ? snapshotService.computeTopSessions(clubId, start, end) : List.of());
        edition.setMostActiveMembers(sections.contains(AutoSection.MOST_ACTIVE_MEMBERS)
                ? snapshotService.computeMostActiveMembers(clubId, start, end) : List.of());
        edition.setMilestones(sections.contains(AutoSection.MILESTONES)
                ? snapshotService.computeMilestones(clubId, start, end) : List.of());
    }

    private void emitGazettePublishedFeedEvent(ClubGazetteEdition edition) {
        ClubFeedEvent event = new ClubFeedEvent();
        event.setClubId(edition.getClubId());
        event.setType(ClubFeedEventType.GAZETTE_PUBLISHED);
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(event.getCreatedAt());
        event.setGazetteEditionId(edition.getId());
        event.setGazetteEditionNumber(edition.getEditionNumber());
        event.setGazettePeriodStart(edition.getPeriodStart().toLocalDate());
        event.setGazettePeriodEnd(edition.getPeriodEnd().toLocalDate());
        int includedCount = (int) postRepository.findByEditionIdOrderByCreatedAtAsc(edition.getId())
                .stream().filter(p -> !p.isExcluded()).count();
        event.setGazettePostCount(includedCount);
        feedEventRepository.save(event);
        feedBroadcaster.broadcast(edition.getClubId(), "new_feed_event",
                ClubFeedEventResponse.from(event));
    }

    private void notifyMembers(ClubGazetteEdition edition) {
        List<String> memberIds = membershipRepository
                .findByClubIdAndStatus(edition.getClubId(), ClubMemberStatus.ACTIVE)
                .stream().map(ClubMembership::getUserId).toList();
        if (memberIds.isEmpty()) return;

        String authorName = userService.findById(edition.getPublishedByUserId())
                .map(User::getDisplayName).orElse("A coach");
        String title = "Gazette #" + edition.getEditionNumber() + " is out";
        String body = authorName + " just published a new club gazette";
        try {
            notificationService.sendToUsers(memberIds, title, body,
                    Map.of("type", "GAZETTE_PUBLISHED",
                           "clubId", edition.getClubId(),
                           "editionId", edition.getId()));
        } catch (Exception e) {
            log.warn("Failed to send gazette published notifications: {}", e.getMessage());
        }
    }

    private static String safeFilename(String requested, int editionNumber) {
        if (requested == null || requested.isBlank()) {
            return "gazette-" + editionNumber + ".pdf";
        }
        // Strip any path separators
        String name = requested.replaceAll("[\\\\/]+", "_");
        if (!name.toLowerCase().endsWith(".pdf")) name += ".pdf";
        return name;
    }
}
