package com.jjenus.tracker.core.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class Trip {
    private final String tripId;
    private final String vehicleId;
    private final Instant startTime;
    private Instant endTime;
    private LocationPoint startLocation;
    private LocationPoint endLocation;
    private final List<LocationPoint> routePoints;
    private float totalDistance;

    public Trip(String vehicleId, LocationPoint startLocation) {
        this.tripId = "TRIP_" + vehicleId + "_" + UUID.randomUUID().toString().substring(0, 8);
        this.vehicleId = vehicleId;
        this.startTime = startLocation.timestamp();
        this.startLocation = startLocation;
        this.routePoints = new ArrayList<>();
        this.routePoints.add(startLocation);
        this.totalDistance = 0.0f;
    }

    public void addLocation(LocationPoint location) {
        if (!routePoints.isEmpty()) {
            LocationPoint lastPoint = routePoints.get(routePoints.size() - 1);
            totalDistance += lastPoint.distanceTo(location);
        }
        routePoints.add(location);
    }

    public void end(LocationPoint endLocation) {
        this.endTime = endLocation.timestamp(); // Use the location's timestamp!
        this.endLocation = endLocation;
        addLocation(endLocation);
    }

    public Duration getDuration() {
        if (endTime == null) {
            // Active trip - duration from start to now
            return Duration.between(startTime, Instant.now());
        } else {
            // Ended trip - fixed duration between start and end timestamps
            return Duration.between(startTime, endTime);
        }
    }

    public float getAverageSpeed() {
        Duration duration = getDuration();
        long seconds = duration.getSeconds();
        if (seconds == 0) return 0.0f;

        // Speed = distance / time (convert seconds to hours)
        return totalDistance / (seconds / 3600.0f);
    }

    // Getters
    public String getTripId() { return tripId; }
    public String getVehicleId() { return vehicleId; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public LocationPoint getStartLocation() { return startLocation; }
    public LocationPoint getEndLocation() { return endLocation; }
    public List<LocationPoint> getRoutePoints() {
        return Collections.unmodifiableList(routePoints);
    }
    public float getTotalDistance() { return totalDistance; }
    public boolean isActive() { return endTime == null; }

    @Override
    public String toString() {
        return "Trip{" +
                "tripId='" + tripId + '\'' +
                ", vehicleId='" + vehicleId + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", totalDistance=" + totalDistance +
                ", isActive=" + isActive() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Trip trip = (Trip) o;
        return Objects.equals(tripId, trip.tripId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tripId);
    }
}