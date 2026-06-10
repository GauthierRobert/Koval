package com.koval.trainingplannerbackend.integration.nolio.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Nolio webhook notification (https://github.com/NolioApp/NolioAPI-Documentation/wiki/Webhook-mechanism).
 * It is a notification, not a delivery: the object itself must be re-fetched.
 * {@code dateObject} is omitted on delete events; {@code livemode=false} marks
 * portal test deliveries (with the {@code object_id: 0} sentinel).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NolioWebhookPayload(
        @JsonProperty("notif_type") String notifType,
        @JsonProperty("object_type") String objectType,
        @JsonProperty("object_id") Long objectId,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("date_object") String dateObject,
        @JsonProperty("livemode") Boolean livemode
) {
    boolean isTestDelivery() {
        return Boolean.FALSE.equals(livemode) || objectId == null || objectId == 0;
    }
}
