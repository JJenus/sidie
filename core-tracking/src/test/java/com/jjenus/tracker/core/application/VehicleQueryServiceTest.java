package com.jjenus.tracker.core.application;

import com.jjenus.tracker.core.domain.entity.TrackerLocation;
import com.jjenus.tracker.core.domain.entity.Vehicle;
import com.jjenus.tracker.core.infrastructure.repository.VehicleRepository;
import com.jjenus.tracker.shared.domain.LocationPoint;
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
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class VehicleQueryServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    private VehicleQueryService queryService;
    private Vehicle testVehicle1;
    private Vehicle testVehicle2;
    private Instant testTime;

    @BeforeEach
    void setUp() {
        queryService = new VehicleQueryService(vehicleRepository);
        testTime = Instant.now();

        testVehicle1 = new Vehicle();
        testVehicle1.setVehicleId("VEH-001");
        testVehicle1.setCurrentLocation(makeLocation(40.7128, -74.0060, 30.0f));

        testVehicle2 = new Vehicle();
        testVehicle2.setVehicleId("VEH-002");
        testVehicle2.setCurrentLocation(makeLocation(34.0522, -118.2437, 0.0f));
    }

    private TrackerLocation makeLocation(double lat, double lon, float speed) {
        TrackerLocation loc = new TrackerLocation();
        loc.setLatitude(lat);
        loc.setLongitude(lon);
        loc.setSpeedKmh(speed);
        loc.setRecordedAt(testTime);
        return loc;
    }

    @Test
    void getVehicleByIdFound_returnsVehicle() {
        when(vehicleRepository.findById("VEH-001")).thenReturn(Optional.of(testVehicle1));

        Optional<Vehicle> result = queryService.getVehicleById("VEH-001");

        assertThat(result).isPresent();
        assertThat(result.get().getVehicleId()).isEqualTo("VEH-001");
        verify(vehicleRepository, times(1)).findById("VEH-001");
    }

    @Test
    void getVehicleByIdNotFound_returnsEmpty() {
        when(vehicleRepository.findById("VEH-999")).thenReturn(Optional.empty());

        Optional<Vehicle> result = queryService.getVehicleById("VEH-999");

        assertThat(result).isEmpty();
        verify(vehicleRepository, times(1)).findById("VEH-999");
    }

    @Test
    void getAllVehicles_returnsAll() {
        List<Vehicle> vehicles = Arrays.asList(testVehicle1, testVehicle2);
        when(vehicleRepository.findAll()).thenReturn(vehicles);

        List<Vehicle> result = queryService.getAllVehicles();

        assertThat(result).hasSize(2);
        assertThat(result).contains(testVehicle1, testVehicle2);
        verify(vehicleRepository, times(1)).findAll();
    }

    @Test
    void getAllVehiclesEmpty_returnsEmpty() {
        when(vehicleRepository.findAll()).thenReturn(List.of());

        List<Vehicle> result = queryService.getAllVehicles();

        assertThat(result).isEmpty();
        verify(vehicleRepository, times(1)).findAll();
    }

    @Test
    void getCurrentLocationFound_returnsLocation() {
        when(vehicleRepository.findById("VEH-001")).thenReturn(Optional.of(testVehicle1));

        Optional<LocationPoint> result = queryService.getCurrentLocation("VEH-001");

        assertThat(result).isPresent();
        assertThat(result.get().latitude()).isEqualTo(40.7128);
        assertThat(result.get().longitude()).isEqualTo(-74.0060);
        assertThat(result.get().speedKmh()).isEqualTo(30.0f);
        verify(vehicleRepository, times(1)).findById("VEH-001");
    }

    @Test
    void getCurrentLocationNotFound_returnsEmpty() {
        when(vehicleRepository.findById("VEH-999")).thenReturn(Optional.empty());

        Optional<LocationPoint> result = queryService.getCurrentLocation("VEH-999");

        assertThat(result).isEmpty();
        verify(vehicleRepository, times(1)).findById("VEH-999");
    }

    @Test
    void isVehicleMovingTrue() {
        when(vehicleRepository.findById("VEH-001")).thenReturn(Optional.of(testVehicle1));

        boolean result = queryService.isVehicleMoving("VEH-001");

        assertThat(result).isTrue();
        verify(vehicleRepository, times(1)).findById("VEH-001");
    }

    @Test
    void isVehicleMovingFalse() {
        when(vehicleRepository.findById("VEH-002")).thenReturn(Optional.of(testVehicle2));

        boolean result = queryService.isVehicleMoving("VEH-002");

        assertThat(result).isFalse();
        verify(vehicleRepository, times(1)).findById("VEH-002");
    }

    @Test
    void isVehicleMovingNotFound_returnsFalse() {
        when(vehicleRepository.findById("VEH-999")).thenReturn(Optional.empty());

        boolean result = queryService.isVehicleMoving("VEH-999");

        assertThat(result).isFalse();
        verify(vehicleRepository, times(1)).findById("VEH-999");
    }

    @Test
    void getVehicleSpeedMoving() {
        when(vehicleRepository.findById("VEH-001")).thenReturn(Optional.of(testVehicle1));

        Optional<Float> result = queryService.getVehicleSpeed("VEH-001");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(30.0f);
        verify(vehicleRepository, times(1)).findById("VEH-001");
    }

    @Test
    void getVehicleSpeedStationary() {
        when(vehicleRepository.findById("VEH-002")).thenReturn(Optional.of(testVehicle2));

        Optional<Float> result = queryService.getVehicleSpeed("VEH-002");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(0.0f);
        verify(vehicleRepository, times(1)).findById("VEH-002");
    }

    @Test
    void getVehicleSpeedNotFound_returnsEmpty() {
        when(vehicleRepository.findById("VEH-999")).thenReturn(Optional.empty());

        Optional<Float> result = queryService.getVehicleSpeed("VEH-999");

        assertThat(result).isEmpty();
        verify(vehicleRepository, times(1)).findById("VEH-999");
    }

    @Test
    void queryMethodsCallRepositoryOnlyOnce() {
        when(vehicleRepository.findById("VEH-001")).thenReturn(Optional.of(testVehicle1));

        queryService.getVehicleById("VEH-001");
        queryService.getCurrentLocation("VEH-001");
        queryService.isVehicleMoving("VEH-001");
        queryService.getVehicleSpeed("VEH-001");

        verify(vehicleRepository, times(4)).findById("VEH-001");
    }
}
