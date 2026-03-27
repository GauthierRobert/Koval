package com.koval.trainingplannerbackend.integration.garmin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/**
 * Garmin Connect OAuth 1.0a service.
 * Requires a developer partnership at developer.garmin.com.
 */
@Service
public class GarminOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GarminOAuthService.class);

    private static final String REQUEST_TOKEN_URL = "https://connectapi.garmin.com/oauth-service/oauth/request_token";
    private static final String AUTHORIZE_URL = "https://connect.garmin.com/oauthConfirm";
    private static final String ACCESS_TOKEN_URL = "https://connectapi.garmin.com/oauth-service/oauth/access_token";

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${garmin.consumer-key:}")
    private String consumerKey;

    @Value("${garmin.consumer-secret:}")
    private String consumerSecret;

    @Value("${garmin.redirect-uri:}")
    private String redirectUri;

    public boolean isConfigured() {
        return consumerKey != null && !consumerKey.isBlank()
                && consumerSecret != null && !consumerSecret.isBlank();
    }

    /**
     * Step 1: Get a request token from Garmin.
     * Returns a map with oauth_token and oauth_token_secret.
     */
    public Map<String, String> getRequestToken() {
        String nonce = generateNonce();
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

        Map<String, String> oauthParams = new LinkedHashMap<>();
        oauthParams.put("oauth_callback", encode(redirectUri));
        oauthParams.put("oauth_consumer_key", consumerKey);
        oauthParams.put("oauth_nonce", nonce);
        oauthParams.put("oauth_signature_method", "HMAC-SHA1");
        oauthParams.put("oauth_timestamp", timestamp);
        oauthParams.put("oauth_version", "1.0");

        String signature = generateSignature("POST", REQUEST_TOKEN_URL, oauthParams, "");
        oauthParams.put("oauth_signature", encode(signature));

        String authHeader = buildAuthorizationHeader(oauthParams);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);

        ResponseEntity<String> response = restTemplate.exchange(
                REQUEST_TOKEN_URL, HttpMethod.POST, new HttpEntity<>(headers), String.class);

        return parseOAuthResponse(response.getBody());
    }

    /**
     * Step 2: Build the authorization URL for the user to visit.
     */
    public String getAuthorizationUrl(String oauthToken) {
        return AUTHORIZE_URL + "?oauth_token=" + encode(oauthToken);
    }

    /**
     * Step 3: Exchange the request token + verifier for an access token.
     */
    public GarminAccessToken exchangeForAccessToken(String oauthToken, String oauthTokenSecret, String verifier) {
        String nonce = generateNonce();
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

        Map<String, String> oauthParams = new LinkedHashMap<>();
        oauthParams.put("oauth_consumer_key", consumerKey);
        oauthParams.put("oauth_nonce", nonce);
        oauthParams.put("oauth_signature_method", "HMAC-SHA1");
        oauthParams.put("oauth_timestamp", timestamp);
        oauthParams.put("oauth_token", oauthToken);
        oauthParams.put("oauth_verifier", verifier);
        oauthParams.put("oauth_version", "1.0");

        String signature = generateSignature("POST", ACCESS_TOKEN_URL, oauthParams, oauthTokenSecret);
        oauthParams.put("oauth_signature", encode(signature));

        String authHeader = buildAuthorizationHeader(oauthParams);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);

        ResponseEntity<String> response = restTemplate.exchange(
                ACCESS_TOKEN_URL, HttpMethod.POST, new HttpEntity<>(headers), String.class);

        Map<String, String> parsed = parseOAuthResponse(response.getBody());
        return new GarminAccessToken(
                parsed.get("oauth_token"),
                parsed.get("oauth_token_secret"),
                parsed.getOrDefault("userId", parsed.getOrDefault("user_id", ""))
        );
    }

    /**
     * Sign an API request with OAuth 1.0a.
     */
    public String signRequest(String method, String url, String accessToken, String accessTokenSecret) {
        String nonce = generateNonce();
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

        Map<String, String> oauthParams = new LinkedHashMap<>();
        oauthParams.put("oauth_consumer_key", consumerKey);
        oauthParams.put("oauth_nonce", nonce);
        oauthParams.put("oauth_signature_method", "HMAC-SHA1");
        oauthParams.put("oauth_timestamp", timestamp);
        oauthParams.put("oauth_token", accessToken);
        oauthParams.put("oauth_version", "1.0");

        String signature = generateSignature(method, url, oauthParams, accessTokenSecret);
        oauthParams.put("oauth_signature", encode(signature));

        return buildAuthorizationHeader(oauthParams);
    }

    private String generateSignature(String method, String url, Map<String, String> params, String tokenSecret) {
        try {
            TreeMap<String, String> sorted = new TreeMap<>(params);
            StringBuilder paramString = new StringBuilder();
            sorted.forEach((k, v) -> {
                if (!paramString.isEmpty()) paramString.append("&");
                paramString.append(k).append("=").append(v);
            });

            String baseString = method.toUpperCase() + "&" + encode(url) + "&" + encode(paramString.toString());
            String signingKey = encode(consumerSecret) + "&" + encode(tokenSecret);

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(baseString.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate OAuth signature", e);
        }
    }

    private String buildAuthorizationHeader(Map<String, String> params) {
        StringBuilder sb = new StringBuilder("OAuth ");
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(entry.getKey()).append("=\"").append(entry.getValue()).append("\"");
            first = false;
        }
        return sb.toString();
    }

    private Map<String, String> parseOAuthResponse(String body) {
        Map<String, String> result = new HashMap<>();
        if (body != null) {
            for (String param : body.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2) result.put(kv[0], kv[1]);
            }
        }
        return result;
    }

    private String generateNonce() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record GarminAccessToken(String accessToken, String accessTokenSecret, String userId) {}
}
