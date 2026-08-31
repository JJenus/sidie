package com.jjenus.tracker.notification.api.dto;

public record WebhookPushPayload(
        String deliveryId,
        String status,
        String errorCode,
        String errorMessage
) {
}
