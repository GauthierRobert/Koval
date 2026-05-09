package com.koval.trainingplannerbackend.club.feed;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves client-supplied mention user IDs against active club membership and
 * builds {@link ClubFeedEvent.MentionRef} instances. Forged or non-member IDs
 * are silently dropped (no notification, no persisted ref).
 */
@Component
public class MentionResolver {

    public static final int MAX_MENTIONS_PER_POST = 10;

    public static final String CONTEXT_ANNOUNCEMENT = "ANNOUNCEMENT";
    public static final String CONTEXT_COMMENT = "COMMENT";
    public static final String CONTEXT_REPLY = "REPLY";
    public static final String CONTEXT_SPOTLIGHT = "SPOTLIGHT";

    private final ClubMembershipRepository membershipRepository;
    private final UserService userService;

    public MentionResolver(ClubMembershipRepository membershipRepository, UserService userService) {
        this.membershipRepository = membershipRepository;
        this.userService = userService;
    }

    public List<ClubFeedEvent.MentionRef> resolve(String clubId,
                                                  List<String> userIds,
                                                  String contextType,
                                                  String contextId) {
        if (userIds == null || userIds.isEmpty()) return List.of();

        // Dedupe + cap.
        List<String> unique = userIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .limit(MAX_MENTIONS_PER_POST)
                .toList();
        if (unique.isEmpty()) return List.of();

        // Validate active membership in this club.
        Set<String> activeMembers = membershipRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE)
                .stream()
                .map(ClubMembership::getUserId)
                .collect(Collectors.toSet());

        List<String> validIds = unique.stream().filter(activeMembers::contains).toList();
        if (validIds.isEmpty()) return List.of();

        Map<String, String> nameById = userService.findAllById(validIds).stream()
                .collect(Collectors.toMap(User::getId, u ->
                        u.getDisplayName() != null ? u.getDisplayName() : ""));

        List<ClubFeedEvent.MentionRef> refs = new ArrayList<>();
        for (String id : validIds) {
            refs.add(new ClubFeedEvent.MentionRef(id, nameById.getOrDefault(id, ""), contextType, contextId));
        }
        return refs;
    }

    /** Extract just the unique mentioned userIds from a list of refs (handy for notification dispatch). */
    public Set<String> idsOf(List<ClubFeedEvent.MentionRef> refs) {
        if (refs == null) return Set.of();
        return refs.stream().map(ClubFeedEvent.MentionRef::userId).collect(Collectors.toCollection(HashSet::new));
    }

    public Function<String, String> nameLookup(List<ClubFeedEvent.MentionRef> refs) {
        if (refs == null) return id -> id;
        Map<String, String> names = refs.stream()
                .collect(Collectors.toMap(ClubFeedEvent.MentionRef::userId,
                        ClubFeedEvent.MentionRef::displayName, (a, b) -> a));
        return id -> names.getOrDefault(id, id);
    }
}
