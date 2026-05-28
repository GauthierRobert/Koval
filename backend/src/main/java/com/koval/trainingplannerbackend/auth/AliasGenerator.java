package com.koval.trainingplannerbackend.auth;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;

/**
 * Generates memorable, anonymous handles for users (e.g. {@code SwiftOtter-42}).
 *
 * <p>These aliases replace real names in every MCP tool response so external AI clients
 * (Claude Desktop, ChatGPT) never see personally identifiable information. Coaches see the
 * same alias in their UI so what they read on screen matches what they can type to the AI.
 */
@Service
public class AliasGenerator {

    private static final List<String> ADJECTIVES = List.of(
            "Swift", "Silent", "Brave", "Quick", "Mighty", "Steady", "Fierce", "Calm",
            "Bold", "Cunning", "Eager", "Gentle", "Lively", "Noble", "Rapid", "Sharp",
            "Stout", "Wild", "Wise", "Daring", "Sturdy", "Nimble", "Fearless", "Loyal",
            "Crimson", "Golden", "Silver", "Bronze", "Iron", "Storm", "Frost", "Solar",
            "Lunar", "Cosmic", "Forest", "River", "Mountain", "Ocean", "Thunder", "Blazing");

    private static final List<String> ANIMALS = List.of(
            "Otter", "Falcon", "Wolf", "Bear", "Hawk", "Lynx", "Fox", "Stag",
            "Eagle", "Tiger", "Lion", "Panther", "Cheetah", "Heron", "Raven", "Owl",
            "Badger", "Hare", "Boar", "Elk", "Bison", "Cobra", "Dolphin", "Orca",
            "Shark", "Mantis", "Hornet", "Drake", "Phoenix", "Griffin", "Stallion", "Ibis",
            "Marlin", "Salmon", "Puma", "Jaguar", "Mustang", "Viper", "Condor", "Kestrel");

    private final SecureRandom random = new SecureRandom();
    private final UserRepository userRepository;

    public AliasGenerator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Generate a unique alias. Retries on collision; falls back to a numeric suffix
     * widening if the namespace is somehow saturated.
     */
    public String generate() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = pick(ADJECTIVES) + pick(ANIMALS) + "-" + (10 + random.nextInt(90));
            if (!userRepository.existsByAlias(candidate)) {
                return candidate;
            }
        }
        // Extremely unlikely fallback: 4-digit suffix to widen the space.
        while (true) {
            String candidate = pick(ADJECTIVES) + pick(ANIMALS) + "-" + (1000 + random.nextInt(9000));
            if (!userRepository.existsByAlias(candidate)) {
                return candidate;
            }
        }
    }

    private String pick(List<String> from) {
        return from.get(random.nextInt(from.size()));
    }
}
