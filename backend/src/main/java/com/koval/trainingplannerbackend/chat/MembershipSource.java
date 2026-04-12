package com.koval.trainingplannerbackend.chat;

/**
 * Why a user is in a chat room. Used by the sync logic to avoid stomping on
 * SELF_JOINED memberships when reconciling AUTO members from the parent entity.
 */
public enum MembershipSource {
    /** Added automatically from the parent entity (club, group, session roster). */
    AUTO,
    /** User self-joined a joinable room (only OBJECTIVE today). */
    SELF_JOINED,
    /** Reserved for future explicit invites. */
    INVITED
}
