package com.jjenus.tracker.notification.domain;

public enum DeliveryStatus {
    PENDING("Pending delivery"),
    SENDING("Currently sending"),
    SENT("Successfully sent"),
    DELIVERED("Confirmed delivery"),
    FAILED("Delivery failed"),
    RETRYING("Retrying delivery"),
    DISCARDED("Discarded after retries");
    
    private final String description;
    
    DeliveryStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isFinal() {
        return this == DELIVERED || this == DISCARDED;
    }
    
    public boolean canRetry() {
        return this == FAILED || this == RETRYING;
    }
}
