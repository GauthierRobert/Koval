package com.koval.trainingplannerbackend.ai.anonymization;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-conversation mapping between real user identifiers and AI-safe pseudonyms.
 * Ensures no personal data (names, MongoDB IDs) reaches the AI provider.
 *
 * Thread-safe: all public methods synchronize on the context instance.
 */
public class AnonymizationContext {

    private final Map<String, String> realIdToAlias = new HashMap<>();
    private final Map<String, String> aliasToRealId = new HashMap<>();
    private final AtomicInteger athleteCounter = new AtomicInteger(1);
    private final AtomicInteger groupCounter = new AtomicInteger(1);
    private final AtomicInteger clubCounter = new AtomicInteger(1);

    /**
     * Returns a stable pseudonym for the given real user ID (e.g. "Athlete-1").
     * Repeated calls with the same realId return the same alias.
     */
    public synchronized String anonymizeAthlete(String realId) {
        return realIdToAlias.computeIfAbsent(realId, k -> {
            String alias = "Athlete-" + athleteCounter.getAndIncrement();
            aliasToRealId.put(alias, realId);
            return alias;
        });
    }

    /**
     * Returns a stable pseudonym for a group ID (e.g. "Group-1").
     */
    public synchronized String anonymizeGroup(String realId) {
        return realIdToAlias.computeIfAbsent(realId, k -> {
            String alias = "Group-" + groupCounter.getAndIncrement();
            aliasToRealId.put(alias, realId);
            return alias;
        });
    }

    /**
     * Returns a stable pseudonym for a club ID (e.g. "Club-1").
     */
    public synchronized String anonymizeClub(String realId) {
        return realIdToAlias.computeIfAbsent(realId, k -> {
            String alias = "Club-" + clubCounter.getAndIncrement();
            aliasToRealId.put(alias, realId);
            return alias;
        });
    }

    /**
     * Resolves an alias back to the real ID.
     * Returns the input unchanged if it is not a known alias (passthrough for real IDs).
     */
    public synchronized String deAnonymize(String alias) {
        return aliasToRealId.getOrDefault(alias, alias);
    }

    /**
     * De-anonymizes a list of IDs (e.g. athleteIds from a tool call).
     */
    public synchronized List<String> deAnonymizeAll(List<String> aliases) {
        return aliases.stream().map(this::deAnonymize).toList();
    }

    /**
     * Returns the alias for a real ID, or null if not yet mapped.
     */
    public synchronized String getAlias(String realId) {
        return realIdToAlias.get(realId);
    }
}
