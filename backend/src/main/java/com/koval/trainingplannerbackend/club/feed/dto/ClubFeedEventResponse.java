package com.koval.trainingplannerbackend.club.feed.dto;

import com.koval.trainingplannerbackend.club.feed.ClubFeedEvent;
import com.koval.trainingplannerbackend.club.feed.ClubFeedEventType;
import com.koval.trainingplannerbackend.club.feed.SpotlightBadge;
import com.koval.trainingplannerbackend.media.dto.MediaResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public record ClubFeedEventResponse(
        String id,
        ClubFeedEventType type,
        boolean pinned,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        // SESSION_COMPLETION
        String clubSessionId,
        String sessionTitle,
        String sessionSport,
        LocalDateTime sessionScheduledAt,
        List<ClubFeedEvent.CompletionEntry> completions,
        Set<String> kudosGivenBy,
        // RACE_COMPLETION
        String raceGoalId,
        String raceTitle,
        LocalDate raceDate,
        List<ClubFeedEvent.RaceCompletionEntry> raceCompletions,
        // COACH_ANNOUNCEMENT
        String authorId,
        String authorName,
        String authorProfilePicture,
        String announcementContent,
        List<AnnouncementAttachmentResponse> announcementAttachments,
        // NEXT_GOAL
        String goalTitle,
        String goalSport,
        LocalDate goalDate,
        String goalLocation,
        List<ClubFeedEvent.EngagedAthlete> engagedAthletes,
        // GAZETTE_PUBLISHED
        String gazetteEditionId,
        Integer gazetteEditionNumber,
        LocalDate gazettePeriodStart,
        LocalDate gazettePeriodEnd,
        int gazettePostCount,
        // COMMENTS
        List<ClubFeedEvent.CommentEntry> comments,
        // PHOTO ENRICHMENTS
        List<PhotoEnrichmentResponse> photoEnrichments,
        // REACTIONS (emoji code -> set of userIds)
        Map<String, Set<String>> reactions,
        // MENTIONS (denormalized refs to mentioned users)
        List<ClubFeedEvent.MentionRef> mentionRefs,
        // MEMBER_SPOTLIGHT
        String spotlightedUserId,
        String spotlightedDisplayName,
        String spotlightedProfilePicture,
        String spotlightTitle,
        String spotlightMessage,
        SpotlightBadge spotlightBadge,
        LocalDateTime spotlightExpiresAt) {

    /** Plain mapping with no media resolution — photoEnrichments will be empty. */
    public static ClubFeedEventResponse from(ClubFeedEvent e) {
        return from(e, null);
    }

    /** Mapping with optional media resolver to populate photo enrichments. */
    public static ClubFeedEventResponse from(ClubFeedEvent e,
                                             Function<String, MediaResponse> mediaResolver) {
        List<PhotoEnrichmentResponse> photos = e.getPhotoEnrichments() == null
                ? List.of()
                : e.getPhotoEnrichments().stream()
                        .map(en -> PhotoEnrichmentResponse.from(en, mediaResolver))
                        .toList();
        List<AnnouncementAttachmentResponse> attachments = e.getAnnouncementAttachments() == null
                ? List.of()
                : e.getAnnouncementAttachments().stream()
                        .map(a -> AnnouncementAttachmentResponse.from(a, mediaResolver))
                        .toList();
        Map<String, Set<String>> reactions = e.getReactions() == null
                ? new HashMap<>()
                : e.getReactions();
        List<ClubFeedEvent.MentionRef> mentions = e.getMentionRefs() == null
                ? List.of()
                : e.getMentionRefs();
        return new ClubFeedEventResponse(
                e.getId(), e.getType(), Boolean.TRUE.equals(e.getPinned()), e.getCreatedAt(), e.getUpdatedAt(),
                e.getClubSessionId(), e.getSessionTitle(), e.getSessionSport(), e.getSessionScheduledAt(),
                e.getCompletions(), e.getKudosGivenBy(),
                e.getRaceGoalId(), e.getRaceTitle(), e.getRaceDate(), e.getRaceCompletions(),
                e.getAuthorId(), e.getAuthorName(), e.getAuthorProfilePicture(), e.getAnnouncementContent(),
                attachments,
                e.getGoalTitle(), e.getGoalSport(), e.getGoalDate(), e.getGoalLocation(), e.getEngagedAthletes(),
                e.getGazetteEditionId(), e.getGazetteEditionNumber(),
                e.getGazettePeriodStart(), e.getGazettePeriodEnd(), e.getGazettePostCount(),
                e.getComments(),
                photos,
                reactions, mentions,
                e.getSpotlightedUserId(), e.getSpotlightedDisplayName(), e.getSpotlightedProfilePicture(),
                e.getSpotlightTitle(), e.getSpotlightMessage(), e.getSpotlightBadge(), e.getSpotlightExpiresAt());
    }
}
