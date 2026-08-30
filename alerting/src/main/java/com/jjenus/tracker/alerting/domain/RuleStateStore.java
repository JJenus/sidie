package com.jjenus.tracker.alerting.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public interface RuleStateStore {

    Optional<Boolean> getGeofenceWasInside(String ruleKey, String vehicleId);

    void setGeofenceWasInside(String ruleKey, String vehicleId, boolean wasInside);

    Optional<Instant> getLastMovementTime(String ruleKey, String vehicleId);

    void setLastMovementTime(String ruleKey, String vehicleId, Instant time);

    Map<String, Instant> getAllLastMovementTimes(String ruleKey);
}
