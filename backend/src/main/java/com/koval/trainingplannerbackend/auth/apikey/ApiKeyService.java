package com.koval.trainingplannerbackend.auth.apikey;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class ApiKeyService {

    private static final String KEY_PREFIX = "koval_";
    private static final int RAW_KEY_LENGTH = 40;
    private static final int MAX_KEYS_PER_USER = 5;

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(ApiKeyRepository apiKeyRepository, UserRepository userRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.userRepository = userRepository;
    }

    public ApiKeyResponse generateKey(String userId, String name) {
        long activeCount = apiKeyRepository.countByUserIdAndActiveTrue(userId);
        if (activeCount >= MAX_KEYS_PER_USER) {
            throw new ValidationException("Maximum of " + MAX_KEYS_PER_USER + " active API keys allowed.", "API_KEY_LIMIT");
        }

        String rawKey = KEY_PREFIX + generateRandomHex(RAW_KEY_LENGTH);
        String hash = sha256(rawKey);

        ApiKey apiKey = new ApiKey();
        apiKey.setUserId(userId);
        apiKey.setKeyHash(hash);
        apiKey.setPrefix(rawKey.substring(0, KEY_PREFIX.length() + 8));
        apiKey.setName(name != null && !name.isBlank() ? name.trim() : "API Key");
        apiKey.setCreatedAt(Instant.now());
        apiKey.setActive(true);

        ApiKey saved = apiKeyRepository.save(apiKey);
        return new ApiKeyResponse(saved.getId(), rawKey, saved.getPrefix(), saved.getName());
    }

    public Optional<ApiKey> validateKey(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(KEY_PREFIX)) {
            return Optional.empty();
        }
        String hash = sha256(rawKey);
        Optional<ApiKey> found = apiKeyRepository.findByKeyHashAndActiveTrue(hash);
        found.ifPresent(key -> {
            key.setLastUsedAt(Instant.now());
            apiKeyRepository.save(key);
        });
        return found;
    }

    public Optional<User> resolveUser(ApiKey apiKey) {
        return userRepository.findById(apiKey.getUserId());
    }

    public List<ApiKeyListItem> listKeys(String userId) {
        return apiKeyRepository.findByUserIdAndActiveTrueOrderByCreatedAtDesc(userId)
                .stream()
                .map(ApiKeyListItem::from)
                .toList();
    }

    public void revokeKey(String keyId, String userId) {
        ApiKey key = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new ResourceNotFoundException("ApiKey", keyId));
        if (!key.getUserId().equals(userId)) {
            throw new ValidationException("Cannot revoke another user's API key.", "API_KEY_NOT_OWNED");
        }
        key.setActive(false);
        apiKeyRepository.save(key);
    }

    public static boolean isApiKey(String token) {
        return token != null && token.startsWith(KEY_PREFIX);
    }

    private String generateRandomHex(int length) {
        byte[] bytes = new byte[length / 2];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
