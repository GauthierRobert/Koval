package com.koval.trainingplannerbackend.club.feed.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import com.koval.trainingplannerbackend.club.feed.ClubFeedBroadcastMessage;
import com.koval.trainingplannerbackend.club.feed.ClubFeedSseBroadcaster;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "club-feed.broker.type", havingValue = "pubsub")
public class PubSubFeedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PubSubFeedConsumer.class);

    private final ProjectSubscriptionName subscriptionName;
    private final ClubFeedSseBroadcaster broadcaster;
    private final ObjectMapper objectMapper;
    private Subscriber subscriber;

    public PubSubFeedConsumer(ProjectSubscriptionName clubFeedSubscriptionName,
                              ClubFeedSseBroadcaster broadcaster,
                              ObjectMapper objectMapper) {
        this.subscriptionName = clubFeedSubscriptionName;
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() {
        MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
            try {
                String json = message.getData().toStringUtf8();
                ClubFeedBroadcastMessage feedMessage = objectMapper.readValue(json, ClubFeedBroadcastMessage.class);

                log.debug("Received feed event from Pub/Sub: clubId={}, event={}",
                        feedMessage.clubId(), feedMessage.eventName());

                broadcaster.broadcastLocal(
                        feedMessage.clubId(),
                        feedMessage.eventName(),
                        feedMessage.payloadJson());

                consumer.ack();
            } catch (Exception e) {
                log.error("Failed to process Pub/Sub message: {}", e.getMessage(), e);
                consumer.nack();
            }
        };

        subscriber = Subscriber.newBuilder(subscriptionName, receiver).build();
        subscriber.startAsync();
        log.info("Pub/Sub subscriber starting async on: {}", subscriptionName);
    }

    @PreDestroy
    public void stop() {
        if (subscriber != null) {
            subscriber.stopAsync().awaitTerminated();
            log.info("Pub/Sub subscriber stopped");
        }
    }
}
