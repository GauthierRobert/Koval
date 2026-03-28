package com.koval.trainingplannerbackend.club.feed.broker;

import com.koval.trainingplannerbackend.club.feed.ClubFeedBroadcastMessage;
import com.koval.trainingplannerbackend.club.feed.ClubFeedBrokerPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "club-feed.broker.type", havingValue = "rabbitmq")
public class RabbitMqFeedPublisher implements ClubFeedBrokerPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqFeedPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitMqFeedPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(ClubFeedBroadcastMessage message) {
        log.debug("Publishing feed event to RabbitMQ: clubId={}, event={}",
                message.clubId(), message.eventName());
        rabbitTemplate.convertAndSend(RabbitMqFeedConfig.FANOUT_EXCHANGE, "", message);
    }
}
