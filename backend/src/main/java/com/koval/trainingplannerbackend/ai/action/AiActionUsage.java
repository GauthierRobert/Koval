package com.koval.trainingplannerbackend.ai.action;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Per-user monthly usage counter for one-shot AI actions (e.g. AI workout
 * creation). The {@code id} is {@code "userId:YYYY-MM"} so a new bucket
 * starts automatically each calendar month — no scheduled cleanup needed
 * (old documents are stable and can be pruned later if desired).
 */
@Document(collection = "ai_action_usage")
public class AiActionUsage {

    @Id
    private String id;
    private String userId;
    private String yearMonth;
    private AIActionType actionType;
    private long count;

    public AiActionUsage() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getYearMonth() { return yearMonth; }
    public void setYearMonth(String yearMonth) { this.yearMonth = yearMonth; }
    public AIActionType getActionType() { return actionType; }
    public void setActionType(AIActionType actionType) { this.actionType = actionType; }
    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
}
