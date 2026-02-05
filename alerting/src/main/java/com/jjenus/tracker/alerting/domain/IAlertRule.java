package com.jjenus.tracker.alerting.domain;

import com.jjenus.tracker.shared.domain.LocationPoint;

public interface IAlertRule {
    AlertDetectedEvent evaluate(String vehicleId, LocationPoint newLocation);
    String getRuleKey();
    String getRuleName();
    boolean isEnabled();
    void setEnabled(boolean enabled);
    int getPriority();
}

