package com.koval.trainingplannerbackend.ai.agents;

import com.koval.trainingplannerbackend.ai.AIConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Classifies user messages into agent types.
 * Uses a fast heuristic for follow-ups and keyword matches to avoid
 * unnecessary Haiku API calls (~60-70% of messages skip the LLM).
 * Falls back to a lightweight Haiku classification call when heuristics are inconclusive.
 */
@Service
public class RouterService {

    private static final Logger log = LoggerFactory.getLogger(RouterService.class);

    private static final String ROUTER_SYSTEM = AIConfig.loadPrompt("router");

    // Follow-up indicators in English and French
    private static final Set<String> FOLLOW_UP_INDICATORS = Set.of(
            // English
            "it", "that", "this", "them", "those", "the same",
            "also", "now", "then", "next", "again", "too",
            "yes", "no", "ok", "sure", "exactly", "perfect",
            "but", "instead", "actually", "rather",
            "modify", "update", "delete", "remove", "add more",
            // French
            "le", "la", "les", "ça", "cela", "celui", "celle",
            "aussi", "maintenant", "ensuite", "encore", "puis",
            "oui", "non", "d'accord", "exactement", "parfait",
            "mais", "plutôt", "en fait", "finalement",
            "change", "modifie", "supprime", "ajoute", "pareil", "le même", "la même"
    );

    // Keyword patterns supporting English and French
    private static final Map<Pattern, AgentType> KEYWORD_PATTERNS = Map.of(
            // Training creation — EN + FR
            Pattern.compile("(?i)\\b(create|design|build|make|generate|créer?|crée|construi[rs]|génère|fabrique|fais)\\b.*(workout|training|session|plan|entraînement|séance|exercice)"),
            AgentType.TRAINING_CREATION,
            Pattern.compile("(?i)\\b(créer?|crée|fais|génère)\\b.*(entraînement|séance|workout|exercice|plan)"),
            AgentType.TRAINING_CREATION,
            // Scheduling — EN + FR (includes training plans / periodization)
            Pattern.compile("(?i)\\b(schedule|assign|calendar|plan for|set for|planifie|programme|calendrier|assigne|prévois)\\b"),
            AgentType.SCHEDULING,
            Pattern.compile("(?i)\\b(race goal|add goal|my goals|objectif|course|mes objectifs|ajoute.*objectif)\\b"),
            AgentType.SCHEDULING,
            Pattern.compile("(?i)\\b(training plan|periodiz|week plan|multi.?week|activate.*plan|plan d'entraînement|périodisation|programme.*semaines)\\b"),
            AgentType.SCHEDULING,
            // Analysis — EN + FR
            Pattern.compile("(?i)\\b(analy[sz]e|review|performance|CTL|ATL|TSB|PMC|fitness|fatigue|form|bilan|progression|forme)\\b"),
            AgentType.ANALYSIS,
            // Coach management — EN + FR
            Pattern.compile("(?i)\\b(athletes?|athlètes?|zone system|système.*zones?|manage.*group|gestion.*groupe|coaching)\\b"),
            AgentType.COACH_MANAGEMENT,
            // Club management — EN + FR
            Pattern.compile("(?i)\\b(club.*(session|séance|training|entraînement|member|membre|group|groupe)|recurring.*(session|séance)|séance.*club|link.*training.*session|lier.*entraînement)\\b"),
            AgentType.CLUB_MANAGEMENT
    );

    private final ChatClient routerClient;

    public RouterService(@Qualifier("routerClient") ChatClient routerClient) {
        this.routerClient = routerClient;
    }

    public AgentType classify(String userMessage, String userRole, String lastAgentType) {
        Optional<AgentType> quick = quickClassify(userMessage, lastAgentType);
        if (quick.isPresent()) {
            log.debug("Quick-classified message as {} (skipped Haiku)", quick.get());
            return quick.get();
        }
        return llmClassify(userMessage, lastAgentType);
    }

    /**
     * Attempts fast classification without an LLM call.
     * Keyword patterns are checked first so explicit intent ("assign", "create")
     * overrides follow-up heuristics ("this", "it") that would otherwise
     * stick to the previous agent.
     * Returns empty if the heuristic is inconclusive.
     */
    private Optional<AgentType> quickClassify(String userMessage, String lastAgentType) {
        // 1. Keyword patterns first — explicit intent always wins
        for (var entry : KEYWORD_PATTERNS.entrySet()) {
            if (entry.getKey().matcher(userMessage).find()) {
                return Optional.of(entry.getValue());
            }
        }

        // 2. Follow-up detection — only if no keyword matched
        if (lastAgentType != null && looksLikeFollowUp(userMessage)) {
            try {
                return Optional.of(AgentType.valueOf(lastAgentType));
            } catch (IllegalArgumentException ignored) {
            }
        }

        return Optional.empty();
    }

    private boolean looksLikeFollowUp(String message) {
        if (message.length() > 150) return false;
        String lower = message.toLowerCase();
        return FOLLOW_UP_INDICATORS.stream().anyMatch(indicator ->
                lower.contains(indicator) || lower.startsWith(indicator));
    }

    private AgentType llmClassify(String userMessage, String lastAgentType) {
        try {
            String lastAgent = lastAgentType != null ? lastAgentType : "NONE";
            String systemPrompt = ROUTER_SYSTEM.replace("{lastAgent}", lastAgent);

            String result = routerClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .content();

            return AgentType.valueOf(result.trim().toUpperCase());
        } catch (Exception e) {
            log.warn("Router classification failed, falling back to GENERAL: {}", e.getMessage());
            return AgentType.GENERAL;
        }
    }
}
