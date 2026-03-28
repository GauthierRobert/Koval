package com.koval.trainingplannerbackend.club.feed.broker;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.koval.trainingplannerbackend.club.feed.ClubFeedBroadcastMessage;
import com.koval.trainingplannerbackend.club.feed.ClubFeedBrokerPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "club-feed.broker.type", havingValue = "pubsub")
public class PubSubFeedPublisher implements ClubFeedBrokerPublisher {

    private static final Logger log = LoggerFactory.getLogger(PubSubFeedPublisher.class);

    private final Publisher publisher;
    private final ObjectMapper objectMapper;

    public PubSubFeedPublisher(Publisher clubFeedPublisher, ObjectMapper objectMapper) {
        this.publisher = clubFeedPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(ClubFeedBroadcastMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                    .setData(ByteString.copyFromUtf8(json))
                    .putAttributes("clubId", message.clubId())
                    .putAttributes("eventName", message.eventName())
                    .build();

            publisher.publish(pubsubMessage);
            log.debug("Published feed event to Pub/Sub: clubId={}, event={}",
                    message.clubId(), message.eventName());
        } catch (Exception e) {
            log.error("Failed to publish feed event to Pub/Sub: {}", e.getMessage(), e);
        }
    }
}
