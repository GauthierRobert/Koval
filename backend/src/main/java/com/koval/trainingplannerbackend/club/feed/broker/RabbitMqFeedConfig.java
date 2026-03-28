package com.koval.trainingplannerbackend.club.feed.broker;

import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "club-feed.broker.type", havingValue = "rabbitmq")
public class RabbitMqFeedConfig {

    public static final String FANOUT_EXCHANGE = "club-feed-fanout";
    public static final String DLX_EXCHANGE = "club-feed-dlx";
    public static final String DLQ_QUEUE = "club-feed-dlq";

    @Bean
    FanoutExchange clubFeedFanoutExchange() {
        return new FanoutExchange(FANOUT_EXCHANGE);
    }

    @Bean
    Queue clubFeedInstanceQueue() {
        return QueueBuilder.nonDurable()
                .autoDelete()
                .exclusive()
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .build();
    }

    @Bean
    Binding clubFeedBinding(Queue clubFeedInstanceQueue, FanoutExchange clubFeedFanoutExchange) {
        return BindingBuilder.bind(clubFeedInstanceQueue).to(clubFeedFanoutExchange);
    }

    // Dead-letter exchange and queue
    @Bean
    FanoutExchange clubFeedDlx() {
        return new FanoutExchange(DLX_EXCHANGE);
    }

    @Bean
    Queue clubFeedDlq() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    @Bean
    Binding clubFeedDlqBinding(Queue clubFeedDlq, FanoutExchange clubFeedDlx) {
        return BindingBuilder.bind(clubFeedDlq).to(clubFeedDlx);
    }

    @Bean
    MessageConverter clubFeedMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
