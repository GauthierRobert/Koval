package com.koval.trainingplannerbackend.club.feed.broker;

import com.google.api.gax.rpc.AlreadyExistsException;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.protobuf.Duration;
import com.google.pubsub.v1.ExpirationPolicy;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.TopicName;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.UUID;

@Configuration
@ConditionalOnProperty(name = "club-feed.broker.type", havingValue = "pubsub")
@ImportRuntimeHints(PubSubNativeHints.class)
public class PubSubFeedConfig {

    private static final Logger log = LoggerFactory.getLogger(PubSubFeedConfig.class);

    private final String projectId;
    private final String topicId;
    private final String subscriptionId;
    private SubscriptionAdminClient subscriptionAdminClient;

    public PubSubFeedConfig(@Value("${club-feed.broker.pubsub.project-id}") String projectId,
                            @Value("${club-feed.broker.pubsub.topic:club-feed-events}") String topicId) {
        this.projectId = projectId;
        this.topicId = topicId;
        this.subscriptionId = "club-feed-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Bean
    TopicName clubFeedTopicName() {
        return TopicName.of(projectId, topicId);
    }

    @Bean
    Publisher clubFeedPublisher(TopicName clubFeedTopicName) throws IOException {
        return Publisher.newBuilder(clubFeedTopicName).build();
    }

    /**
     * Each instance creates its own ephemeral subscription for fan-out.
     * Auto-deletes after 1 day of inactivity if shutdown cleanup is missed.
     */
    @Bean
    ProjectSubscriptionName clubFeedSubscriptionName(TopicName clubFeedTopicName) throws IOException {
        ProjectSubscriptionName subName = ProjectSubscriptionName.of(projectId, subscriptionId);
        subscriptionAdminClient = SubscriptionAdminClient.create();

        Subscription subscription = Subscription.newBuilder()
                .setName(subName.toString())
                .setTopic(clubFeedTopicName.toString())
                .setAckDeadlineSeconds(10)
                .setExpirationPolicy(ExpirationPolicy.newBuilder()
                        .setTtl(Duration.newBuilder().setSeconds(86400).build())
                        .build())
                .build();

        try {
            subscriptionAdminClient.createSubscription(subscription);
            log.info("Created ephemeral Pub/Sub subscription: {}", subscriptionId);
        } catch (AlreadyExistsException e) {
            log.debug("Pub/Sub subscription already exists: {}", subscriptionId);
        }

        return subName;
    }

    @PreDestroy
    public void cleanup() {
        if (subscriptionAdminClient != null) {
            try {
                ProjectSubscriptionName subName = ProjectSubscriptionName.of(projectId, subscriptionId);
                subscriptionAdminClient.deleteSubscription(subName);
                log.info("Deleted Pub/Sub subscription: {}", subscriptionId);
            } catch (Exception e) {
                log.warn("Failed to delete Pub/Sub subscription {}: {}", subscriptionId, e.getMessage());
            } finally {
                subscriptionAdminClient.close();
            }
        }
    }
}
