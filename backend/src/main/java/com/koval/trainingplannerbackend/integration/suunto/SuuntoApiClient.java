package com.koval.trainingplannerbackend.integration.suunto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Thin client over the Suunto Cloud API. Only the bits needed for OAuth bootstrap are
 * implemented here — activity sync and workout push live in their own services and will
 * be added incrementally, mirroring the Polar integration.
 */
@Component
public class SuuntoApiClient {

    private static final Logger log = LoggerFactory.getLogger(SuuntoApiClient.class);
    private static final String BASE_URL = "https://cloudapi.suunto.com/v2";

    private final RestTemplate restTemplate;
    private final String subscriptionKey;

    public SuuntoApiClient(@Value("${suunto.client-id:}") String subscriptionKey) {
        this.subscriptionKey = subscriptionKey;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.restTemplate = new RestTemplate(factory);
    }

    /** Fetches the user profile (first/last name, etc.) for the authenticated Suunto user. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchUser(String accessToken) {
        String url = BASE_URL + "/user";
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(bearer(accessToken)), Map.class);
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (RestClientException e) {
            log.warn("Suunto fetchUser failed: {}", e.getMessage());
            return Map.of();
        }
    }

    private HttpHeaders bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (subscriptionKey != null && !subscriptionKey.isBlank()) {
            headers.set("Ocp-Apim-Subscription-Key", subscriptionKey);
        }
        return headers;
    }
}
