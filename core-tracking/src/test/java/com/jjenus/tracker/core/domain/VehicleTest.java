package com.jjenus.tracker.core.domain;

import com.jjenus.tracker.core.exception.TripException;
import com.jjenus.tracker.core.exception.VehicleException;
import com.jjenus.tracker.shared.domain.LocationPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class VehicleTest {

    private Vehicle vehicle;
    private Instant testTime;

    @BeforeEach
    void setUp() {
        vehicle = new Vehicle("VEH-001");
        testTime = Instant.now();
    }

    @Test
    void testVehicleCreation() {
        assertEquals("VEH-001", vehicle.getVehicleId());
        assertEquals(EngineState.OFF, vehicle.getEngineState());
        assertFalse(vehicle.isFuelCutActive());
        assertNotNull(vehicle.getCurrentLocation());
        assertNotNull(vehicle.getFuelStatus());
        assertTrue(vehicle.getRecentAlerts().isEmpty());
        assertNull(vehicle.getActiveTrip());
    }

    @Test
    void testProcessNewTelemetryWithValidLocation() {
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 60.5f, testTime);

        vehicle.processNewTelemetry(location);

        assertEquals(location, vehicle.getCurrentLocation());
        assertEquals(EngineState.MOVING, vehicle.getEngineState());
        assertNotNull(vehicle.getActiveTrip());
    }

    @Test
    void testProcessNewTelemetryWithInvalidLocation() {
        LocationPoint invalidLocation = new LocationPoint(100.0, -74.0060, 60.5f, testTime);

        VehicleException exception = assertThrows(VehicleException.class,
            () -> vehicle.processNewTelemetry(invalidLocation));

        assertEquals("VEHICLE_INVALID_LOCATION", exception.getErrorCode());
    }

    @Test
    void testProcessNewTelemetryStartsTripWhenMoving() {
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 10.0f, testTime);

        vehicle.processNewTelemetry(location);

        assertNotNull(vehicle.getActiveTrip());
        assertEquals("VEH-001", vehicle.getActiveTrip().getVehicleId());
        assertTrue(vehicle.getActiveTrip().isActive());
    }

    @Test
    void testProcessNewTelemetryDoesNotStartTripWhenStationary() {
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 0.0f, testTime);

        vehicle.processNewTelemetry(location);

        assertNull(vehicle.getActiveTrip());
        assertEquals(EngineState.OFF, vehicle.getEngineState());
    }

    @Test
    void testIssueFuelCutOffCommandWhenStationary() {
        // First make the vehicle stationary
        LocationPoint stationary = new LocationPoint(40.7128, -74.0060, 0.0f, testTime);
        vehicle.processNewTelemetry(stationary);

        vehicle.issueFuelCutOffCommand();

        assertTrue(vehicle.isFuelCutActive());
        assertEquals(EngineState.FUEL_CUT_ACTIVE, vehicle.getEngineState());
    }

    @Test
    void testIssueFuelCutOffCommandWhenMovingTooFast() {
        LocationPoint moving = new LocationPoint(40.7128, -74.0060, 50.0f, testTime);
        vehicle.processNewTelemetry(moving);

        VehicleException exception = assertThrows(VehicleException.class,
            () -> vehicle.issueFuelCutOffCommand());

        assertEquals("VEHICLE_FUEL_CUT_MOVING", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("50.0"));
        assertFalse(vehicle.isFuelCutActive());
    }

    @Test
    void testIssueFuelCutOffCommandWhenAlreadyCut() {
        LocationPoint stationary = new LocationPoint(40.7128, -74.0060, 0.0f, testTime);
        vehicle.processNewTelemetry(stationary);

        vehicle.issueFuelCutOffCommand();

        VehicleException exception = assertThrows(VehicleException.class,
            () -> vehicle.issueFuelCutOffCommand());

        assertEquals("VEHICLE_FUEL_CUT_ACTIVE", exception.getErrorCode());
        assertTrue(vehicle.isFuelCutActive());
    }

    @Test
    void testIssueFuelRestoreCommand() {
        LocationPoint stationary = new LocationPoint(40.7128, -74.0060, 0.0f, testTime);
        vehicle.processNewTelemetry(stationary);

        vehicle.issueFuelCutOffCommand();
        assertTrue(vehicle.isFuelCutActive());

        vehicle.issueFuelRestoreCommand();
        assertFalse(vehicle.isFuelCutActive());
        assertEquals(EngineState.ON, vehicle.getEngineState());
    }

    @Test
    void testIssueFuelRestoreCommandWhenMoving() {
        LocationPoint moving = new LocationPoint(40.7128, -74.0060, 30.0f, testTime);
        vehicle.processNewTelemetry(moving);

        // Can't cut fuel while moving, but let's test restore after cutting when stationary
        LocationPoint stationary = new LocationPoint(40.7128, -74.0060, 0.0f, testTime.plusSeconds(10));
        vehicle.processNewTelemetry(stationary);

        vehicle.issueFuelCutOffCommand();
        vehicle.issueFuelRestoreCommand();

        // Should be ON (not MOVING) because speed is 0
        assertEquals(EngineState.ON, vehicle.getEngineState());
    }

    @Test
    void testEndTrip() {
        LocationPoint start = new LocationPoint(40.7128, -74.0060, 10.0f, testTime);
        vehicle.processNewTelemetry(start);

        assertNotNull(vehicle.getActiveTrip());

        LocationPoint end = new LocationPoint(40.7589, -73.9851, 0.0f, testTime.plusSeconds(300));
        vehicle.endTrip(end);

        assertNull(vehicle.getActiveTrip());
    }

    @Test
    void testEndTripWhenNoActiveTrip() {
        TripException exception = assertThrows(TripException.class,
            () -> vehicle.endTrip(new LocationPoint(40.7128, -74.0060, 0.0f, testTime)));

        assertEquals("TRIP_NOT_ACTIVE", exception.getErrorCode());
    }

    @Test
    void testAddAlert() {
        assertEquals(0, vehicle.getRecentAlerts().size());

        vehicle.addAlert("Test alert 1");
        vehicle.addAlert("Test alert 2");

        assertEquals(2, vehicle.getRecentAlerts().size());
        assertTrue(vehicle.getRecentAlerts().get(0).contains("Test alert 1"));
        assertTrue(vehicle.getRecentAlerts().get(1).contains("Test alert 2"));
    }

    @Test
    void testAlertLimit() {
        for (int i = 0; i < 150; i++) {
            vehicle.addAlert("Alert " + i);
        }

        assertEquals(100, vehicle.getRecentAlerts().size());
        // Oldest alerts should be removed
        assertFalse(vehicle.getRecentAlerts().get(0).contains("Alert 0"));
        assertTrue(vehicle.getRecentAlerts().get(vehicle.getRecentAlerts().size() - 1).contains("Alert 149"));
    }

    @Test
    void testGetIdleDurationWhenNotIdle() {
        assertEquals(Duration.ZERO, vehicle.getIdleDuration());

        LocationPoint moving = new LocationPoint(40.7128, -74.0060, 30.0f, testTime);
        vehicle.processNewTelemetry(moving);

        assertEquals(Duration.ZERO, vehicle.getIdleDuration());
    }

    @Test
    void testEngineStateTransitions() {
        // Start OFF
        assertEquals(EngineState.OFF, vehicle.getEngineState());

        // Start moving -> MOVING
        LocationPoint moving = new LocationPoint(40.7128, -74.0060, 30.0f, testTime);
        vehicle.processNewTelemetry(moving);
        assertEquals(EngineState.MOVING, vehicle.getEngineState());

        // Stop -> IDLE
        LocationPoint stopped = new LocationPoint(40.7128, -74.0060, 0.0f, testTime.plusSeconds(10));
        vehicle.processNewTelemetry(stopped);
        assertEquals(EngineState.IDLE, vehicle.getEngineState());

        // Fuel cut -> FUEL_CUT_ACTIVE
        vehicle.issueFuelCutOffCommand();
        assertEquals(EngineState.FUEL_CUT_ACTIVE, vehicle.getEngineState());

        // Restore fuel -> ON (because speed is 0)
        vehicle.issueFuelRestoreCommand();
        assertEquals(EngineState.ON, vehicle.getEngineState());
    }

    @Test
    void testSettersForReconstruction() {
        FuelStatus newFuelStatus = new FuelStatus(50.0f, 5.0f, testTime);

        // These are protected methods, typically used by repository for reconstruction
        // We're testing them directly for completeness
        vehicle.setDeviceId("DEV-001");
        vehicle.setFuelStatus(newFuelStatus);

        assertEquals("DEV-001", vehicle.getDeviceId());
        assertEquals(newFuelStatus, vehicle.getFuelStatus());
    }
}
