package com.jjenus.tracker.notification.application.service;

import com.jjenus.tracker.notification.domain.entity.Delivery;

public interface NotificationService {

    DeliveryResult send(Delivery delivery);

    boolean isAvailable();

    String getChannel();
}