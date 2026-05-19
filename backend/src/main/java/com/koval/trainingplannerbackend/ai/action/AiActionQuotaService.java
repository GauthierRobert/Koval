package com.koval.trainingplannerbackend.ai.action;

import com.koval.trainingplannerbackend.config.exceptions.RateLimitException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.ZoneOffset;

/**
 * Server-side monthly quota for AI workout generation.
 *
 * The counter is keyed by {@code "userId:YYYY-MM:ACTION_TYPE"} so each
 * calendar month starts fresh without any cron job. Disabled by default —
 * enabled only via {@code app.ai.monthly-quota.enabled=true} in
 * {@code application-prod.yml}.
 *
 * Usage pattern: call {@link #checkQuota} before executing the action
 * (throws HTTP 429 if the limit is already reached), then call
 * {@link #recordUsage} after the action succeeded. We accept a tiny race
 * window (concurrent requests by the same user) which can let at most a
 * handful of extra calls through per month — acceptable for a soft cap.
 */
@Service
public class AiActionQuotaService {

    private final MongoTemplate mongoTemplate;
    private final boolean enabled;
    private final int monthlyLimit;

    public AiActionQuotaService(
            MongoTemplate mongoTemplate,
            @Value("${app.ai.monthly-quota.enabled:false}") boolean enabled,
            @Value("${app.ai.monthly-quota.training-creation-per-month:5}") int monthlyLimit) {
        this.mongoTemplate = mongoTemplate;
        this.enabled = enabled;
        this.monthlyLimit = monthlyLimit;
    }

    public void checkQuota(String userId, AIActionType actionType) {
        if (!shouldEnforce(userId, actionType)) {
            return;
        }
        long current = currentCount(userId, actionType);
        if (current >= monthlyLimit) {
            throw new RateLimitException(
                    "Monthly AI workout limit reached (" + monthlyLimit
                            + "/month). Please try again next month.");
        }
    }

    public void recordUsage(String userId, AIActionType actionType) {
        if (!shouldEnforce(userId, actionType)) {
            return;
        }
        String id = documentId(userId, actionType);
        Update update = new Update()
                .inc("count", 1)
                .setOnInsert("userId", userId)
                .setOnInsert("yearMonth", currentYearMonth())
                .setOnInsert("actionType", actionType);
        mongoTemplate.findAndModify(
                new Query(Criteria.where("_id").is(id)),
                update,
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                AiActionUsage.class);
    }

    private long currentCount(String userId, AIActionType actionType) {
        AiActionUsage usage = mongoTemplate.findById(
                documentId(userId, actionType), AiActionUsage.class);
        return usage != null ? usage.getCount() : 0L;
    }

    private boolean shouldEnforce(String userId, AIActionType actionType) {
        return enabled
                && userId != null && !userId.isBlank()
                && actionType == AIActionType.TRAINING_CREATION;
    }

    private String documentId(String userId, AIActionType actionType) {
        return userId + ":" + currentYearMonth() + ":" + actionType.name();
    }

    private String currentYearMonth() {
        return YearMonth.now(ZoneOffset.UTC).toString();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMonthlyLimit() {
        return monthlyLimit;
    }
}
