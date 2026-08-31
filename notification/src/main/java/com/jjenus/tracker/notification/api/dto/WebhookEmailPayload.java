package com.jjenus.tracker.notification.api.dto;

public record WebhookEmailPayload(
        String deliveryId,
        String status,
        String errorMessage
) {
}
