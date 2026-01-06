package com.jjenus.tracker.alerting.domain;

import com.jjenus.tracker.core.domain.Vehicle;
import com.jjenus.tracker.shared.domain.LocationPoint;

public interface IAlertRule {
    AlertEvent evaluate(Vehicle vehicle, LocationPoint newLocation);
    String getRuleKey();
    String getRuleName();
    boolean isEnabled();
    void setEnabled(boolean enabled);
    int getPriority();
}

