package com.koval.trainingplannerbackend.context;

/**
 * Identifies who authored an {@link AthleteContext} entry. An athlete writes their own
 * self-context ({@code ATHLETE}); a coach writes private context about an athlete they manage
 * ({@code COACH}). Coach-authored entries are never surfaced to the athlete.
 */
public enum ContextAuthorRole {
    ATHLETE,
    COACH
}
