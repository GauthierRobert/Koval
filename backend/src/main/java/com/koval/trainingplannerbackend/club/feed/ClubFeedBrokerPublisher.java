package com.koval.trainingplannerbackend.club.feed;

public interface ClubFeedBrokerPublisher {
    void publish(ClubFeedBroadcastMessage message);
}
