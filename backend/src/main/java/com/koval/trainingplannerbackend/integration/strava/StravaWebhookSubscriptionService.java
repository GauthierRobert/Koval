package com.koval.trainingplannerbackend.integration.strava;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class StravaWebhookSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(StravaWebhookSubscriptionService.class);
    private static final String STRAVA_SUBSCRIPTIONS_URL = "https://www.strava.com/api/v3/push_subscriptions";

    private final RestTemplate restTemplate = new RestTemplate();
    private final String clientId;
    private final String clientSecret;
    private final String callbackUrl;
    private final String verifyToken;

    public StravaWebhookSubscriptionService(
            @Value("${strava.client-id}") String clientId,
            @Value("${strava.client-secret}") String clientSecret,
            @Value("${strava.webhook-callback-url}") String callbackUrl,
            @Value("${strava.webhook-verify-token:strava-webhook-verify}") String verifyToken) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.callbackUrl = callbackUrl;
        this.verifyToken = verifyToken;
    }

    public SubscriptionResult createSubscription() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("callback_url", callbackUrl);
        body.add("verify_token", verifyToken);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    STRAVA_SUBSCRIPTIONS_URL,
                    new HttpEntity<>(body, headers),
                    Map.class);

            Number id = (Number) response.getBody().get("id");
            log.info("Strava webhook subscription created with id={}", id);
            return new SubscriptionResult(id.intValue(), callbackUrl);
        } catch (HttpClientErrorException e) {
            log.error("Failed to create Strava webhook subscription: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new StravaSubscriptionException("Strava returned " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> viewSubscription() {
        String url = STRAVA_SUBSCRIPTIONS_URL + "?client_id={clientId}&client_secret={clientSecret}";
        try {
            ResponseEntity<Map[]> response = restTemplate.getForEntity(url, Map[].class, clientId, clientSecret);
            Map[] subs = response.getBody();
            if (subs == null || subs.length == 0) {
                return Map.of("active", false);
            }
            return Map.of("active", true, "subscription", subs[0]);
        } catch (HttpClientErrorException e) {
            log.error("Failed to view Strava webhook subscription: {}", e.getResponseBodyAsString());
            throw new StravaSubscriptionException("Strava returned " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        }
    }

    public record SubscriptionResult(int id, String callbackUrl) {}

    public static class StravaSubscriptionException extends RuntimeException {
        public StravaSubscriptionException(String message) {
            super(message);
        }
    }
}
