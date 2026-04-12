package com.koval.trainingplannerbackend.chat;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Batch unread count computation that avoids the N+1 query problem.
 *
 * Uses a single MongoDB aggregation pipeline: {@code $match} on roomId set +
 * createdAt floor, then {@code $group} by roomId. The floor is the earliest
 * lastReadAt across all queried rooms, so the result may over-count for rooms
 * with a later lastReadAt — callers pre-filter rooms whose
 * {@code lastMessageAt <= lastReadAt} to eliminate most of them, keeping the
 * over-count negligible.
 */
@Repository
public class ChatMessageCustomRepository {

    private final MongoTemplate mongoTemplate;

    public ChatMessageCustomRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * @param roomLastReadMap roomId → lastReadAt for each room that might have unreads.
     *        Caller should pre-filter rooms whose lastMessageAt ≤ lastReadAt.
     * @return roomId → unread count (rooms with 0 unread are absent from the map).
     *         Counts are approximate upper-bounds when lastReadAt varies across rooms.
     */
    public Map<String, Long> batchUnreadCounts(Map<String, Instant> roomLastReadMap) {
        if (roomLastReadMap.isEmpty()) return Map.of();

        Instant earliest = roomLastReadMap.values().stream()
                .min(Instant::compareTo)
                .orElse(Instant.EPOCH);

        List<String> roomIds = List.copyOf(roomLastReadMap.keySet());

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(
                        Criteria.where("roomId").in(roomIds)
                                .and("createdAt").gt(earliest)
                ),
                Aggregation.group("roomId").count().as("count")
        );

        AggregationResults<UnreadCountResult> results =
                mongoTemplate.aggregate(agg, "chat_messages", UnreadCountResult.class);

        Map<String, Long> out = new HashMap<>();
        for (UnreadCountResult r : results.getMappedResults()) {
            if (r.count() > 0) out.put(r.id(), r.count());
        }
        return out;
    }

    record UnreadCountResult(String id, long count) {}
}
