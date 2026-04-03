package com.koval.trainingplannerbackend.club.feed.broker;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * GraalVM native-image hints for Google Cloud Pub/Sub.
 * Registered via @ImportRuntimeHints on PubSubFeedConfig (conditional on pubsub broker type),
 * so these hints are only included when the pubsub broker is active at build time.
 */
public class PubSubNativeHints implements RuntimeHintsRegistrar {

    private static final MemberCategory[] ALL = MemberCategory.values();

    // Protobuf-generated and resource-name types used directly in PubSubFeedConfig
    private static final String[] PUBSUB_TYPES = {
        "com.google.pubsub.v1.Subscription",
        "com.google.pubsub.v1.Subscription$Builder",
        "com.google.pubsub.v1.PubsubMessage",
        "com.google.pubsub.v1.PubsubMessage$Builder",
        "com.google.pubsub.v1.ExpirationPolicy",
        "com.google.pubsub.v1.ExpirationPolicy$Builder",
        "com.google.pubsub.v1.TopicName",
        "com.google.pubsub.v1.ProjectSubscriptionName",
        "com.google.protobuf.Duration",
        "com.google.protobuf.Duration$Builder",
        "com.google.protobuf.Timestamp",
        "com.google.protobuf.Timestamp$Builder",
        "com.google.protobuf.Any",
        "com.google.protobuf.Empty",
        "com.google.api.gax.rpc.AlreadyExistsException",
        "com.google.api.gax.rpc.ApiException",
        "com.google.api.gax.rpc.StatusCode$Code",
        "com.google.cloud.pubsub.v1.Publisher",
        "com.google.cloud.pubsub.v1.Publisher$Builder",
        "com.google.cloud.pubsub.v1.SubscriptionAdminClient",
        "com.google.cloud.pubsub.v1.SubscriptionAdminSettings",
    };

    // gRPC transport/resolver providers loaded via ServiceLoader at runtime
    private static final String[] GRPC_TYPES = {
        "io.grpc.internal.DnsNameResolverProvider",
        "io.grpc.internal.PickFirstLoadBalancerProvider",
        "io.grpc.netty.shaded.io.grpc.netty.NettyChannelProvider",
        "io.grpc.netty.shaded.io.grpc.netty.NettyServerProvider",
        "io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel",
        "io.grpc.netty.shaded.io.netty.channel.socket.nio.NioServerSocketChannel",
        "io.grpc.netty.shaded.io.netty.channel.epoll.EpollSocketChannel",
        "io.grpc.netty.shaded.io.netty.channel.epoll.EpollServerSocketChannel",
    };

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        for (String type : PUBSUB_TYPES) {
            hints.reflection().registerType(TypeReference.of(type), ALL);
        }
        for (String type : GRPC_TYPES) {
            hints.reflection().registerType(TypeReference.of(type), ALL);
        }

        // gRPC and Google Cloud ServiceLoader provider descriptor files
        hints.resources().registerPattern("META-INF/services/io.grpc.*");
        hints.resources().registerPattern("META-INF/services/com.google.cloud.*");
        hints.resources().registerPattern("META-INF/services/com.google.api.*");

        // Protobuf descriptor files (required for protobuf reflection)
        hints.resources().registerPattern("google/pubsub/v1/pubsub.proto");
        hints.resources().registerPattern("google/protobuf/*.proto");
    }
}
