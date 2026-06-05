package com.koval.trainingplannerbackend.club.feed;

import com.koval.trainingplannerbackend.training.history.CompletedSession;

/**
 * Published when a CompletedSession is saved, so the feed and notifications can react
 * asynchronously.
 *
 * @param notifyUser false when the save must not push a notification to the athlete —
 *                   bulk history imports (one event per imported activity would spam)
 *                   and re-publishes for existing sessions (e.g. club-session linking).
 */
public record SessionCompletedEvent(CompletedSession session, boolean notifyUser) {}
