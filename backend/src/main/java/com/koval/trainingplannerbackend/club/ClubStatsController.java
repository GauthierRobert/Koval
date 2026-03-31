package com.koval.trainingplannerbackend.club;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.club.dto.ClubExtendedStatsResponse;
import com.koval.trainingplannerbackend.club.dto.ClubRaceGoalResponse;
import com.koval.trainingplannerbackend.club.dto.ClubWeeklyStatsResponse;
import com.koval.trainingplannerbackend.club.dto.LeaderboardEntry;
import com.koval.trainingplannerbackend.club.stats.ClubStatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clubs")
public class ClubStatsController {

    private final ClubStatsService clubStatsService;

    public ClubStatsController(ClubStatsService clubStatsService) {
        this.clubStatsService = clubStatsService;
    }

    @GetMapping("/{id}/stats/weekly")
    public ResponseEntity<ClubWeeklyStatsResponse> getWeeklyStats(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubStatsService.getWeeklyStats(userId, id));
    }

    @GetMapping("/{id}/stats/extended")
    public ResponseEntity<ClubExtendedStatsResponse> getExtendedStats(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubStatsService.getExtendedStats(userId, id));
    }

    @GetMapping("/{id}/leaderboard")
    public ResponseEntity<List<LeaderboardEntry>> getLeaderboard(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubStatsService.getLeaderboard(userId, id));
    }

    @GetMapping("/{id}/race-goals")
    public ResponseEntity<List<ClubRaceGoalResponse>> getRaceGoals(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubStatsService.getRaceGoals(userId, id));
    }
}
