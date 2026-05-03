package com.koval.trainingplannerbackend.chat;

/**
 * Result of a lazy-init {@code ensure*RoomForMember} call: both the resolved room and
 * the caller's membership, so downstream response-building avoids re-fetching either.
 */
public record EnsureRoomResult(ChatRoom room, ChatRoomMembership membership) {}
