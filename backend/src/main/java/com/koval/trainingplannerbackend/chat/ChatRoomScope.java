package com.koval.trainingplannerbackend.chat;

/**
 * Discriminator for the kind of parent entity a chat room is attached to.
 * A single unified room model avoids duplicating six near-identical CRUDs.
 */
public enum ChatRoomScope {
    /** One room per club, scoped by clubId. scopeRefId = null. */
    CLUB,
    /** One room per ClubGroup. scopeRefId = groupId. */
    GROUP,
    /** One room per "club objective" (RaceGoal grouping key). scopeRefId = raceId or derived key. Joinable. */
    OBJECTIVE,
    /** One room per RecurringSessionTemplate. scopeRefId = templateId. */
    RECURRING_SESSION,
    /** One room per ad-hoc ClubTrainingSession (not spawned from a template). scopeRefId = sessionId. */
    SINGLE_SESSION,
    /** 1-on-1 direct message between two users. clubId = null. scopeRefId = sorted "userA|userB" key. */
    DIRECT
}
