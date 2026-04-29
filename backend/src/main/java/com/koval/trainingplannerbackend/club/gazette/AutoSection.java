package com.koval.trainingplannerbackend.club.gazette;

/**
 * Auto-curated sections an admin can choose to include or exclude when
 * publishing a gazette via Claude. Each enabled section produces a snapshot
 * frozen on the published edition.
 */
public enum AutoSection {
    STATS,
    LEADERBOARD,
    TOP_SESSIONS,
    MILESTONES,
    MOST_ACTIVE_MEMBERS
}
