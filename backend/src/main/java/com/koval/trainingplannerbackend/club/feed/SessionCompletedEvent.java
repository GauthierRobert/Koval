package com.koval.trainingplannerbackend.club.feed;

import com.koval.trainingplannerbackend.training.history.CompletedSession;

/**
 * Published when a CompletedSession is saved, so the feed can react asynchronously.
 */
public record SessionCompletedEvent(CompletedSession session) {}
