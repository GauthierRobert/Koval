package com.koval.trainingplannerbackend.club.feed.broker;

import com.koval.trainingplannerbackend.club.feed.ClubFeedBroadcastMessage;
import com.koval.trainingplannerbackend.club.feed.ClubFeedSseBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "club-feed.broker.type", havingValue = "rabbitmq")
public class RabbitMqFeedConsumer {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqFeedConsumer.class);

    private final ClubFeedSseBroadcaster broadcaster;

    public RabbitMqFeedConsumer(ClubFeedSseBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @RabbitListener(queues = "#{clubFeedInstanceQueue.name}")
    public void onMessage(ClubFeedBroadcastMessage message) {
        log.debug("Received feed event from RabbitMQ: clubId={}, event={}",
                message.clubId(), message.eventName());
        broadcaster.broadcastLocal(message.clubId(), message.eventName(), message.payloadJson());
    }
}
