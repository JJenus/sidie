package com.jjenus.tracker.core.application;

import com.jjenus.tracker.core.domain.Vehicle;
import com.jjenus.tracker.shared.domain.LocationPoint;
import com.jjenus.tracker.core.infrastructure.IVehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class VehicleQueryServiceTest {

    @Mock
    private IVehicleRepository vehicleRepository;

    private VehicleQueryService queryService;
    private Vehicle testVehicle1;
    private Vehicle testVehicle2;
    private Instant testTime;

    @BeforeEach
    void setUp() {
        queryService = new VehicleQueryService(vehicleRepository);
        testTime = Instant.now();

        testVehicle1 = new Vehicle("VEH-001");
        testVehicle1.processNewTelemetry(new LocationPoint(40.7128, -74.0060, 30.0f, testTime));

        testVehicle2 = new Vehicle("VEH-002");
        testVehicle2.processNewTelemetry(new LocationPoint(34.0522, -118.2437, 0.0f, testTime));
    }

    @Test
    void testGetVehicleByIdFound() {
        when(vehicleRepository.findById("VEH-001")).thenReturn(Optional.of(testVehicle1));

        Optional<Vehicle> result = queryService.getVehicleById("VEH-001");

        assertTrue(result.isPresent());
        assertEquals("VEH-001", result.get().getVehicleId());
        verify(vehicleRepository, times(1)).findById("VEH-001");
    }

    @Test
    void testGetVehicleByIdNotFound() {
        when(vehicleRepository.findById("VEH-999")).thenReturn(Optional.empty());

        Optional<Vehicle> result = queryService.getVehicleById("VEH-999");

        assertFalse(result.isPresent());
        verify(vehicleRepository, times(1)).findById("VEH-999");
    }

    @Test
    void testGetAllVehicles() {
        List<Vehicle> vehicles = Arrays.asList(testVehicle1, testVehicle2);
        when(vehicleRepository.findAll()).thenReturn(vehicles);

        List<Vehicle> result = queryService.getAllVehicles();

        assertEquals(2, result.size());
        assertTrue(result.contains(testVehicle1));
        assertTrue(result.contains(testVehicle2));
        verify(vehicleRepository, times(1)).findAll();
    }

    @Test
    void testGetAllVehiclesEmpty() {
        when(vehicleRepository.findAll()).thenReturn(List.of());

        List<Vehicle> result = queryService.getAllVehicles();

        assertTrue(result.isEmpty());
        verify(vehicleRepository, times(1)).findAll();
    }

    @Test
    void testGetCurrentLocationFound() {
        when(vehicleRepository.findById("VEH-001")).thenReturn(Optional.of(testVehicle1));

        Optional<LocationPoint> result = queryService.getCurrentLocation("VEH-001");

        assertTrue(result.isPresent());
        assertEquals(40.7128, result.get().latitude(), 0.0001);
        assertEquals(-74.0060, result.get().longitude(), 0.0001);
        assertEquals(30.0f, result.get().speedKmh(), 0.1);
        verify(vehicleRepository, times(1)).findById("VEH-001");
    }

    @Test
    void testGetCurrentLocationNotFound() {
        when(vehicleRepository.findById("VEH-999")).thenReturn(Optional.empty());

        Optional<LocationPoint> result = queryService.getCurrentLocation("VEH-999");

        assertFalse(result.isPresent());
        verify(vehicleRepository, times(1)).findById("VEH-999");
    }

    @Test
    void testIsVehicleMovingTrue() {
        when(vehicleRepository.findById("VEH-001")).thenReturn(Optional.of(testVehicle1));

        boolean result = queryService.isVehicleMoving("VEH-001");

        assertTrue(result);
        verify(vehicleRepository, times(1)).findById("VEH-001");
    }

    @Test
    void testIsVehicleMovingFalse() {
        when(vehicleRepository.findById("VEH-002")).thenReturn(Optional.of(testVehicle2));

        boolean result = queryService.isVehicleMoving("VEH-002");

        assertFalse(result);
        verify(vehicleRepository, times(1)).findById("VEH-002");
    }

    @Test
    void testIsVehicleMovingNotFound() {
        when(vehicleRepository.findById("VEH-999")).thenReturn(Optional.empty());

        boolean result = queryService.isVehicleMoving("VEH-999");

        assertFalse(result);
        verify(vehicleRepository, times(1)).findById("VEH-999");
    }

    @Test
    void testGetVehicleSpeedMoving() {
        when(vehicleRepository.findById("VEH-001")).thenReturn(Optional.of(testVehicle1));

        Optional<Float> result = queryService.getVehicleSpeed("VEH-001");

        assertTrue(result.isPresent());
        assertEquals(30.0f, result.get(), 0.1);
        verify(vehicleRepository, times(1)).findById("VEH-001");
    }

    @Test
    void testGetVehicleSpeedStationary() {
        when(vehicleRepository.findById("VEH-002")).thenReturn(Optional.of(testVehicle2));

        Optional<Float> result = queryService.getVehicleSpeed("VEH-002");

        assertTrue(result.isPresent());
        assertEquals(0.0f, result.get(), 0.1);
        verify(vehicleRepository, times(1)).findById("VEH-002");
    }

    @Test
    void testGetVehicleSpeedNotFound() {
        when(vehicleRepository.findById("VEH-999")).thenReturn(Optional.empty());

        Optional<Float> result = queryService.getVehicleSpeed("VEH-999");

        assertFalse(result.isPresent());
        verify(vehicleRepository, times(1)).findById("VEH-999");
    }

    @Test
    void testQueryMethodsCallRepositoryOnlyOnce() {
        when(vehicleRepository.findById("VEH-001")).thenReturn(Optional.of(testVehicle1));

        // Multiple queries should only call repository once each
        queryService.getVehicleById("VEH-001");
        queryService.getCurrentLocation("VEH-001");
        queryService.isVehicleMoving("VEH-001");
        queryService.getVehicleSpeed("VEH-001");

        verify(vehicleRepository, times(4)).findById("VEH-001");
    }
}
