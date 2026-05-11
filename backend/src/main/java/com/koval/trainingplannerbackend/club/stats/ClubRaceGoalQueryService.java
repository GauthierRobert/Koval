package com.koval.trainingplannerbackend.club.stats;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.club.dto.ClubRaceGoalResponse;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipService;
import com.koval.trainingplannerbackend.goal.RaceGoal;
import com.koval.trainingplannerbackend.goal.RaceGoalRepository;
import com.koval.trainingplannerbackend.race.Race;
import com.koval.trainingplannerbackend.race.RaceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Aggregates a club's upcoming race goals, grouping athletes targeting the same race.
 *
 * <p>Split out of {@code ClubStatsService} because race-goal aggregation is a goals
 * concern, not an athletic-statistics one.
 */
@Service
public class ClubRaceGoalQueryService {

    private final RaceGoalRepository raceGoalRepository;
    private final RaceRepository raceRepository;
    private final UserService userService;
    private final ClubMembershipService clubMembershipService;
    private final ClubAuthorizationService authorizationService;

    public ClubRaceGoalQueryService(RaceGoalRepository raceGoalRepository,
                                    RaceRepository raceRepository,
                                    UserService userService,
                                    ClubMembershipService clubMembershipService,
                                    ClubAuthorizationService authorizationService) {
        this.raceGoalRepository = raceGoalRepository;
        this.raceRepository = raceRepository;
        this.userService = userService;
        this.clubMembershipService = clubMembershipService;
        this.authorizationService = authorizationService;
    }

    public List<ClubRaceGoalResponse> getRaceGoals(String userId, String clubId) {
        authorizationService.requireActiveMember(userId, clubId);
        List<String> memberIds = clubMembershipService.getActiveMemberIds(clubId);
        if (memberIds.isEmpty()) return List.of();

        String todayIso = LocalDate.now().toString();
        List<RaceGoal> allGoals = raceGoalRepository.findByAthleteIdIn(memberIds);

        // Batch-fetch every referenced race in a single Mongo round-trip. Building a
        // plain map (vs. computeIfAbsent + per-call lookups) means missing/deleted
        // raceIds resolve to a null map entry once, instead of re-hitting the DB on
        // every stream pass — the previous pattern caused the /race-goals 504s.
        Set<String> referencedRaceIds = allGoals.stream()
                .map(RaceGoal::getRaceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, Race> raceMap = referencedRaceIds.isEmpty()
                ? Map.of()
                : raceRepository.findAllById(referencedRaceIds).stream()
                        .collect(Collectors.toMap(Race::getId, r -> r));
        Function<String, Race> resolveRace = raceId -> raceId == null ? null : raceMap.get(raceId);

        List<RaceGoal> goals = allGoals.stream()
                .filter(g -> {
                    String date = Optional.ofNullable(resolveRace.apply(g.getRaceId()))
                            .map(Race::getScheduledDate).orElse(null);
                    return date == null || date.compareTo(todayIso) >= 0;
                })
                .sorted(Comparator.comparing(g -> Optional.ofNullable(resolveRace.apply(g.getRaceId()))
                        .map(Race::getScheduledDate).orElse("9999-99-99")))
                .toList();

        Map<String, List<RaceGoal>> goalsByRace = goals.stream()
                .collect(Collectors.groupingBy(
                        g -> Optional.ofNullable(g.getRaceId()).orElseGet(() -> g.getTitle().toLowerCase().trim()),
                        LinkedHashMap::new, Collectors.toList()));

        List<String> athleteIds = goals.stream().map(RaceGoal::getAthleteId).distinct().toList();
        Map<String, User> userMap = userService.findAllById(athleteIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return goalsByRace.values().stream().map(raceGoals -> {
            RaceGoal representative = raceGoals.getFirst();
            Race race = resolveRace.apply(representative.getRaceId());
            List<ClubRaceGoalResponse.RaceParticipant> participants = raceGoals.stream()
                    .map(g -> {
                        Optional<User> uOpt = Optional.ofNullable(userMap.get(g.getAthleteId()));
                        return new ClubRaceGoalResponse.RaceParticipant(
                                g.getAthleteId(),
                                uOpt.map(User::getDisplayName).orElse(g.getAthleteId()),
                                uOpt.map(User::getProfilePicture).orElse(null),
                                g.getPriority(),
                                g.getTargetTime());
                    })
                    .toList();

            Optional<Race> raceOpt = Optional.ofNullable(race);
            return new ClubRaceGoalResponse(
                    representative.getRaceId(),
                    raceOpt.map(Race::getTitle).orElseGet(representative::getTitle),
                    raceOpt.map(Race::getSport).filter(Objects::nonNull).orElseGet(representative::getSport),
                    raceOpt.map(Race::getScheduledDate).orElse(null),
                    raceOpt.map(Race::getDistance).filter(Objects::nonNull).orElseGet(representative::getDistance),
                    raceOpt.map(Race::getDistanceCategory).orElse(null),
                    raceOpt.map(Race::getLocation).filter(Objects::nonNull).orElseGet(representative::getLocation),
                    participants);
        }).toList();
    }
}
