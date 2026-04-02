package com.koval.trainingplannerbackend.integration.garmin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.List;
import java.util.Map;

/**
 * Garmin Connect API client for fetching activities and FIT files.
 */
@Component
public class GarminApiClient {

    private static final Logger log = LoggerFactory.getLogger(GarminApiClient.class);
    private static final String BASE_URL = "https://apis.garmin.com";

    private final GarminOAuthService oauthService;
    private final RestTemplate restTemplate;

    {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(5));
        factory.setReadTimeout(java.time.Duration.ofSeconds(10));
        restTemplate = new RestTemplate(factory);
    }

    public GarminApiClient(GarminOAuthService oauthService) {
        this.oauthService = oauthService;
    }

    /**
     * Fetch activities between start and end epoch seconds.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchActivities(String accessToken, String tokenSecret,
                                                      long startEpoch, long endEpoch) {
        String url = BASE_URL + "/wellness-api/rest/activityDetails"
                + "?uploadStartTimeInSeconds=" + startEpoch
                + "&uploadEndTimeInSeconds=" + endEpoch;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", oauthService.signRequest("GET", url, accessToken, tokenSecret));

        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), List.class);
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (RestClientException e) {
            log.warn("Failed to fetch Garmin activities: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetch a single activity by ID.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchActivity(String accessToken, String tokenSecret, String activityId) {
        String url = BASE_URL + "/wellness-api/rest/activityDetails/" + activityId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", oauthService.signRequest("GET", url, accessToken, tokenSecret));

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (RestClientException e) {
            log.warn("Failed to fetch Garmin activity {}: {}", activityId, e.getMessage());
            return Map.of();
        }
    }
}
