package com.jjenus.tracker.core.application;

import com.jjenus.tracker.core.domain.Trip;
import com.jjenus.tracker.core.domain.LocationPoint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ITripService {
    Optional<Trip> getActiveTrip(String vehicleId);
    List<Trip> getVehicleTrips(String vehicleId, Instant from, Instant to);
    float calculateTripDistance(String tripId);
    float calculateFuelConsumption(String tripId);
    void detectTripStart(LocationPoint location);
    void detectTripEnd(LocationPoint location);
}
