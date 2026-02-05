package com.jjenus.tracker.core.application;

import com.jjenus.tracker.core.domain.Vehicle;
import com.jjenus.tracker.shared.domain.LocationPoint;
import com.jjenus.tracker.core.infrastructure.IVehicleRepository;
import com.jjenus.tracker.shared.events.VehicleUpdatedEvent;
import com.jjenus.tracker.shared.pubsub.EventPublisher;
import com.jjenus.tracker.core.exception.VehicleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class VehicleCommandServiceTest {

    @Mock
    private IVehicleRepository vehicleRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<Vehicle> vehicleCaptor;

    @Captor
    private ArgumentCaptor<com.jjenus.tracker.shared.pubsub.DomainEvent> eventCaptor;

    private VehicleCommandService commandService;
    private Vehicle testVehicle;
    private Instant testTime;

    @BeforeEach
    void setUp() {
        commandService = new VehicleCommandService(vehicleRepository, eventPublisher);
        testTime = Instant.now();
        testVehicle = new Vehicle("VEH-001");
    }

    @Test
    void testHandleFuelCutRequestSuccess() {
        when(vehicleRepository.findById("VEH-001")).thenReturn(Optional.of(testVehicle));

        commandService.handleFuelCutRequest("VEH-001");

        verify(vehicleRepository).save(vehicleCaptor.capture());
        verify(eventPublisher).publish(eventCaptor.capture());

        Vehicle savedVehicle = vehicleCaptor.getValue();
        assertTrue(savedVehicle.isFuelCutActive());

        com.jjenus.tracker.shared.pubsub.DomainEvent publishedEvent = eventCaptor.getValue();
        assertNotNull(publishedEvent);
    }

    @Test
    void testHandleFuelCutRequestVehicleNotFound() {
        when(vehicleRepository.findById("VEH-999")).thenReturn(Optional.empty());

        VehicleException exception = assertThrows(VehicleException.class,
            () -> commandService.handleFuelCutRequest("VEH-999"));

        assertEquals("VEHICLE_NOT_FOUND", exception.getErrorCode());
        verify(vehicleRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void testHandleFuelCutRequestWhenMovingTooFast() {
        // Setup vehicle that's moving
        Vehicle movingVehicle = new Vehicle("VEH-001");
        LocationPoint movingLocation = new LocationPoint(40.7128, -74.0060, 50.0f, testTime);
        movingVehicle.processNewTelemetry(movingLocation);

        when(vehicleRepository.findById("VEH-001")).thenReturn(Optional.of(movingVehicle));

        VehicleException exception = assertThrows(VehicleException.class,
            () -> commandService.handleFuelCutRequest("VEH-001"));

        assertEquals("VEHICLE_FUEL_CUT_MOVING", exception.getErrorCode());
        verify(vehicleRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void testHandleFuelRestoreRequestSuccess() {
        // Setup vehicle with fuel cut active
        testVehicle.processNewTelemetry(new LocationPoint(40.7128, -74.0060, 0.0f, testTime));
        testVehicle.issueFuelCutOffCommand();

        when(vehicleRepository.findById("VEH-001")).thenReturn(Optional.of(testVehicle));

        commandService.handleFuelRestoreRequest("VEH-001");

        verify(vehicleRepository).save(vehicleCaptor.capture());

        Vehicle savedVehicle = vehicleCaptor.getValue();
        assertFalse(savedVehicle.isFuelCutActive());
    }

}
