package com.koval.trainingplannerbackend.training.history;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * How a completed session aligned with the workout that was scheduled for it, expressed as a
 * percentage: 100 = on plan, above 100 = exceeded the scheduled effort, below 100 = under it.
 *
 * <p>Two independent, optional assessments are kept side by side:
 * <ul>
 *   <li>the <b>athlete</b>'s own self-rating ({@link #athleteScore} + {@link #athleteNote}), and</li>
 *   <li>the <b>coach</b>'s rating ({@link #coachScore} + {@link #coachNote}), which may be set by a
 *       human coach or by an AI client (see {@link #coachSource}).</li>
 * </ul>
 *
 * <p>The authoritative value shown on the badge and evolution chart is the coach score when present,
 * otherwise the athlete score (see {@link #effectiveScore()}). The deterministic estimate produced
 * when neither is set is never persisted here — it is only a suggestion offered in the UI.
 *
 * <p>Embedded inside {@link CompletedSession}; only meaningful when the session is linked to a
 * scheduled workout.
 */
@Getter
@Setter
public class AlignmentScore {

    /** Source tags for {@link #coachSource}. */
    public static final String SOURCE_COACH = "coach";
    public static final String SOURCE_AI = "ai";

    private Integer athleteScore;        // percent; null until the athlete rates
    private String athleteNote;
    private LocalDateTime athleteSetAt;

    private Integer coachScore;          // percent; null until the coach/AI rates
    private String coachNote;
    private String coachSource;          // "coach" | "ai"
    private LocalDateTime coachSetAt;

    /** Coach rating wins when present; otherwise the athlete's. Null when neither has rated. */
    public Integer effectiveScore() {
        return coachScore != null ? coachScore : athleteScore;
    }
}
