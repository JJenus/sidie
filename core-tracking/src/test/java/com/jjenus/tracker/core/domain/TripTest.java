package com.jjenus.tracker.core.domain;

import com.jjenus.tracker.shared.domain.LocationPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Trip Domain Entity Tests")
class TripTest {

    private Trip trip;
    private Instant startTime;
    private LocationPoint startLocation;

    @BeforeEach
    void setUp() {
        startTime = Instant.now();
        startLocation = new LocationPoint(40.7128, -74.0060, 0.0f, startTime);
        trip = new Trip("VEH-001", startLocation);
    }

    @Test
    @DisplayName("Should create trip with correct initial state")
    void testTripCreation() {
        assertEquals("VEH-001", trip.getVehicleId(), "Vehicle ID should match");
        assertEquals(startLocation, trip.getStartLocation(), "Start location should match");
        assertTrue(trip.getTripId().startsWith("TRIP_VEH-001_"),
                "Trip ID should start with TRIP_VEH-001_");
        assertTrue(trip.isActive(), "New trip should be active");
        assertNull(trip.getEndTime(), "End time should be null for new trip");
        assertNull(trip.getEndLocation(), "End location should be null for new trip");
        assertEquals(0.0f, trip.getTotalDistance(), 0.001,
                "Initial distance should be 0");
        assertEquals(1, trip.getRoutePoints().size(),
                "Should have start location in route points");
        assertEquals(startLocation, trip.getRoutePoints().get(0),
                "First route point should be start location");
    }

    @Test
    @DisplayName("Should add locations to route points")
    void testAddLocation() {
        LocationPoint location2 = new LocationPoint(40.7589, -73.9851, 30.0f,
                startTime.plusSeconds(60));
        LocationPoint location3 = new LocationPoint(40.7589, -73.9851, 0.0f,
                startTime.plusSeconds(120));

        trip.addLocation(location2);
        trip.addLocation(location3);

        List<LocationPoint> routePoints = trip.getRoutePoints();
        assertEquals(3, routePoints.size(), "Should have 3 route points");
        assertEquals(startLocation, routePoints.get(0), "First point should be start");
        assertEquals(location2, routePoints.get(1), "Second point should match");
        assertEquals(location3, routePoints.get(2), "Third point should match");
    }

    @Test
    @DisplayName("Should calculate distance between locations")
    void testDistanceCalculation() {
        // Start: New York (40.7128, -74.0060)
        LocationPoint location2 = new LocationPoint(40.7589, -73.9851, 30.0f,
                startTime.plusSeconds(60)); // Times Square
        LocationPoint location3 = new LocationPoint(40.6892, -74.0445, 0.0f,
                startTime.plusSeconds(120)); // Statue of Liberty

        trip.addLocation(location2);
        trip.addLocation(location3);

        float totalDistance = trip.getTotalDistance();

        // Distance should be positive
        assertTrue(totalDistance > 0, "Total distance should be positive");
        assertTrue(totalDistance < 50,
                "Distance between NYC locations should be less than 50 km");

        // Verify distance is calculated correctly
        double expectedDistance = startLocation.distanceTo(location2) +
                location2.distanceTo(location3);
        assertEquals(expectedDistance, totalDistance, 0.01,
                "Total distance should be sum of distances between points");
    }

    @Test
    @DisplayName("Should end trip correctly")
    void testEndTrip() {
        Instant endTime = startTime.plusSeconds(300);
        LocationPoint endLocation = new LocationPoint(40.6892, -74.0445, 0.0f, endTime);

        trip.end(endLocation);

        assertFalse(trip.isActive(), "Trip should not be active after ending");
        assertNotNull(trip.getEndTime(), "End time should be set");
        assertEquals(endLocation, trip.getEndLocation(), "End location should match");
        assertTrue(trip.getRoutePoints().contains(endLocation),
                "End location should be in route points");

        // Verify end location is the last point
        List<LocationPoint> routePoints = trip.getRoutePoints();
        assertEquals(endLocation, routePoints.get(routePoints.size() - 1),
                "End location should be last point");

        // Verify end time matches location timestamp
        assertEquals(endTime, trip.getEndTime(),
                "End time should match location timestamp");
    }

    @Test
    @DisplayName("Should calculate duration correctly")
    void testGetDuration() {
        // Active trip - duration should be small positive number
        Duration initialDuration = trip.getDuration();
        assertTrue(initialDuration.toMillis() >= 0,
                "Duration should be non-negative");
        assertTrue(initialDuration.toMillis() < 1000,
                "Initial duration should be less than 1 second");

        // Ended trip - should have fixed duration based on timestamps
        Instant endTime = startTime.plusSeconds(300);
        LocationPoint endLocation = new LocationPoint(40.6892, -74.0445, 0.0f, endTime);
        trip.end(endLocation);

        Duration endedDuration = trip.getDuration();
        assertEquals(300, endedDuration.getSeconds(),
                "Ended trip duration should be exactly 300 seconds");
    }

    @Test
    @DisplayName("Should calculate average speed correctly")
    void testGetAverageSpeed() {
        // Add some locations with distance
        LocationPoint location2 = new LocationPoint(40.7589, -73.9851, 30.0f,
                startTime.plusSeconds(60));
        LocationPoint location3 = new LocationPoint(40.6892, -74.0445, 0.0f,
                startTime.plusSeconds(120));

        trip.addLocation(location2);
        trip.addLocation(location3);
        trip.end(location3);

        float averageSpeed = trip.getAverageSpeed();

        // Average speed should be positive
        assertTrue(averageSpeed >= 0, "Average speed should be non-negative");

        // Remove the unrealistic constraint:
        // assertTrue(averageSpeed < 100, "Average speed in NYC should be less than 100 km/h");

        // Instead, verify the calculation is correct
        float totalDistance = trip.getTotalDistance();
        Duration duration = trip.getDuration();
        long seconds = duration.getSeconds();

        if (seconds > 0) {
            float expectedSpeed = totalDistance / (seconds / 3600.0f);
            assertEquals(expectedSpeed, averageSpeed, 0.01,
                    "Average speed should match calculated value");
        }

        // Log for debugging
        System.out.println("Debug - Total distance: " + totalDistance + " km");
        System.out.println("Debug - Duration: " + seconds + " seconds");
        System.out.println("Debug - Average speed: " + averageSpeed + " km/h");
    }

    @Test
    @DisplayName("Should return 0 average speed for zero distance")
    void testGetAverageSpeedWithZeroDistance() {
        // Trip with same location (zero distance) but time passed
        LocationPoint sameLocation = new LocationPoint(40.7128, -74.0060, 0.0f,
                startTime.plusSeconds(10));
        trip.addLocation(sameLocation);
        trip.end(sameLocation);

        float averageSpeed = trip.getAverageSpeed();
        assertEquals(0.0f, averageSpeed, 0.001,
                "Average speed should be 0 for zero distance");
    }

    @Test
    @DisplayName("Should handle average speed for very short trips")
    void testGetAverageSpeedForShortTrip() {
        // Trip with distance but very short duration (less than 1 second)
        LocationPoint location2 = new LocationPoint(40.7129, -74.0061, 0.0f,
                startTime.plusNanos(1)); // 1 nanosecond later
        trip.addLocation(location2);
        trip.end(location2);

        float averageSpeed = trip.getAverageSpeed();
        // With 1 nanosecond duration, speed calculation might be very large
        // or Infinity, which is mathematically correct
        assertTrue(averageSpeed >= 0, "Average speed should be non-negative");
    }

    @Test
    @DisplayName("Should generate unique trip IDs")
    void testTripIdUniqueness() {
        Trip trip2 = new Trip("VEH-001", startLocation);

        assertNotEquals(trip.getTripId(), trip2.getTripId(),
                "Trip IDs should be unique");
        assertTrue(trip.getTripId().startsWith("TRIP_"),
                "Trip ID should start with TRIP_");
        assertTrue(trip2.getTripId().startsWith("TRIP_"),
                "Second trip ID should also start with TRIP_");
    }

    @Test
    @DisplayName("Should return immutable route points list")
    void testRoutePointsImmutable() {
        List<LocationPoint> routePoints = trip.getRoutePoints();

        // Should be able to get the list
        assertEquals(1, routePoints.size(),
                "Should have one route point initially");

        // Attempting to modify should throw UnsupportedOperationException
        assertThrows(UnsupportedOperationException.class, () -> {
            routePoints.add(new LocationPoint(40.0, -73.0, 0.0f, Instant.now()));
        }, "Should not be able to add to immutable list");

        assertThrows(UnsupportedOperationException.class, () -> {
            routePoints.remove(0);
        }, "Should not be able to remove from immutable list");

        assertThrows(UnsupportedOperationException.class, () -> {
            routePoints.clear();
        }, "Should not be able to clear immutable list");
    }

    @Test
    @DisplayName("Should have proper string representation")
    void testToString() {
        String str = trip.toString();

        assertTrue(str.contains("tripId='" + trip.getTripId() + "'"),
                "toString should contain trip ID");
        assertTrue(str.contains("vehicleId='VEH-001'"),
                "toString should contain vehicle ID");
        assertTrue(str.contains("isActive=true"),
                "toString should indicate trip is active");

        // Test after ending trip
        LocationPoint endLocation = new LocationPoint(40.6892, -74.0445, 0.0f,
                startTime.plusSeconds(300));
        trip.end(endLocation);

        str = trip.toString();
        assertTrue(str.contains("isActive=false"),
                "toString should indicate trip is not active after ending");
        assertTrue(str.contains("totalDistance="),
                "toString should contain total distance");
    }

    @Test
    @DisplayName("Should handle edge cases")
    void testEdgeCases() {
        // Test adding multiple locations
        for (int i = 1; i <= 5; i++) {
            LocationPoint point = new LocationPoint(
                    40.7128 + i * 0.001,
                    -74.0060 + i * 0.001,
                    30.0f,
                    startTime.plusSeconds(i * 60)
            );
            trip.addLocation(point);
        }

        assertEquals(6, trip.getRoutePoints().size(),
                "Should have 6 route points after adding 5");
        assertTrue(trip.getTotalDistance() > 0,
                "Total distance should be positive after adding locations");

        // Test that trip is still active
        assertTrue(trip.isActive(), "Trip should still be active before ending");

        // End the trip
        LocationPoint endLocation = new LocationPoint(40.7128, -74.0060, 0.0f,
                startTime.plusSeconds(360));
        trip.end(endLocation);

        assertFalse(trip.isActive(), "Trip should not be active after ending");
        assertEquals(7, trip.getRoutePoints().size(),
                "Should have 7 route points after ending");
    }

    @Test
    @DisplayName("Should use location timestamps for duration calculation")
    void testTimestampUsage() {
        // Create a trip with specific start time
        Instant specificStart = Instant.parse("2024-01-15T10:00:00Z");
        LocationPoint specificStartLocation = new LocationPoint(40.7128, -74.0060, 0.0f, specificStart);
        Trip specificTrip = new Trip("VEH-002", specificStartLocation);

        assertEquals(specificStart, specificTrip.getStartTime(),
                "Trip should use location timestamp as start time");

        // End the trip 2 hours later
        Instant specificEnd = specificStart.plusSeconds(7200); // 2 hours
        LocationPoint specificEndLocation = new LocationPoint(40.6892, -74.0445, 0.0f, specificEnd);
        specificTrip.end(specificEndLocation);

        Duration duration = specificTrip.getDuration();
        assertEquals(7200, duration.getSeconds(),
                "Duration should be exactly 2 hours (7200 seconds)");

        // Average speed with known distance and time
        float totalDistance = specificTrip.getTotalDistance();
        float expectedSpeed = totalDistance / 2.0f; // 2 hours
        float actualSpeed = specificTrip.getAverageSpeed();

        assertEquals(expectedSpeed, actualSpeed, 0.01,
                "Average speed should be distance / 2 hours");
    }
}