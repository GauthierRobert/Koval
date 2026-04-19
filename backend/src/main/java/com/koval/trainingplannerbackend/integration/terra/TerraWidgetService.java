package com.koval.trainingplannerbackend.integration.terra;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates widget session creation for Terra-backed providers.
 */
@Service
public class TerraWidgetService {

    public static final String PROVIDER_NOLIO = "NOLIO";

    private final TerraApiClient terraApiClient;
    private final String successUrl;
    private final String failureUrl;

    public TerraWidgetService(TerraApiClient terraApiClient,
                              @Value("${terra.widget-success-redirect-url}") String successUrl,
                              @Value("${terra.widget-failure-redirect-url}") String failureUrl) {
        this.terraApiClient = terraApiClient;
        this.successUrl = successUrl;
        this.failureUrl = failureUrl;
    }

    public TerraApiClient.WidgetSession generateNolioSession(String userId) {
        return terraApiClient.generateWidgetSession(userId, List.of(PROVIDER_NOLIO), successUrl, failureUrl);
    }
}
