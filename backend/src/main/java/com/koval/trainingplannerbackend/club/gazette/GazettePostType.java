package com.koval.trainingplannerbackend.club.gazette;

/**
 * The kind of contribution a member makes to a gazette draft. Drives the
 * link-validation rules and the rendering hint for Claude / the frontend.
 */
public enum GazettePostType {
    /** Recap of a club session. Requires linkedSessionId; session must be in the period. */
    SESSION_RECAP,

    /** Race result. Requires linkedRaceGoalId; race date must be in the period. */
    RACE_RESULT,

    /** Personal milestone (PR, first century, etc.). No link. */
    PERSONAL_WIN,

    /** Recognition for a teammate. No link. */
    SHOUTOUT,

    /** Free-form reflection / week summary. No link. */
    REFLECTION
}
