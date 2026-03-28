package com.koval.trainingplannerbackend.club.feed;

import java.io.Serializable;

public record ClubFeedBroadcastMessage(
        String clubId,
        String eventName,
        String payloadJson
) implements Serializable {}
