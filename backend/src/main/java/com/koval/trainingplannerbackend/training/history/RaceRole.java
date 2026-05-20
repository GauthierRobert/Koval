package com.koval.trainingplannerbackend.training.history;

/**
 * How a completed session relates to a race day.
 *
 * <ul>
 *   <li>{@link #RACE} — counts toward the race result; sessions sharing a raceId form a chain
 *       (e.g. swim → T1 → bike → T2 → run for a triathlon).</li>
 *   <li>{@link #WARMUP} — recorded on race day but separate from the race effort (e.g. a 15-min
 *       activation spin before the start). Persisted so the suggestion prompt doesn't re-fire,
 *       but the session is NOT bundled with the race chain.</li>
 *   <li>{@link #NONE} — explicitly not race-related; suppresses the race-day prompt.</li>
 * </ul>
 */
public enum RaceRole {
    RACE,
    WARMUP,
    NONE
}
