package com.jjenus.tracker.core.application;

import com.jjenus.tracker.core.domain.Vehicle;
import com.jjenus.tracker.shared.domain.LocationPoint;
import com.jjenus.tracker.core.domain.VehicleUpdatedEvent;
import com.jjenus.tracker.core.domain.FuelCutRequestedEvent;
import com.jjenus.tracker.core.infrastructure.IVehicleRepository;
import com.jjenus.tracker.shared.pubsub.EventPublisher;
import com.jjenus.tracker.core.exception.VehicleException;
import com.jjenus.tracker.shared.exception.ValidationException;
import org.springframework.stereotype.Service;

@Service
public class VehicleCommandService {
    private final IVehicleRepository vehicleRepository;
    private final EventPublisher eventPublisher;

    public VehicleCommandService(IVehicleRepository vehicleRepository,
                                 EventPublisher eventPublisher) {
        this.vehicleRepository = vehicleRepository;
        this.eventPublisher = eventPublisher;
    }

    public void handleFuelCutRequest(String vehicleId) {
        try {
            Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> VehicleException.notFound(vehicleId));

            vehicle.issueFuelCutOffCommand();
            vehicleRepository.save(vehicle);

            // Publish DOMAIN EVENT, not device command
            FuelCutRequestedEvent event = new FuelCutRequestedEvent(
                vehicle.getVehicleId(),
                vehicle.getDeviceId()
            );
            eventPublisher.publish(event);

            System.out.println("Fuel cut command processed for vehicle " + vehicleId);

        } catch (VehicleException e) {
            System.err.println("Fuel cut validation failed for vehicle " + vehicleId + ": " + e.getMessage());
            throw e;
        } catch (Exception e) {
            System.err.println("Failed to process fuel cut for vehicle " + vehicleId + ": " + e.getMessage());
            throw new ValidationException("FUEL_CUT_PROCESS_ERROR",
                "Failed to process fuel cut for vehicle " + vehicleId, e);
        }
    }

    public void handleFuelRestoreRequest(String vehicleId) {
        try {
            Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> VehicleException.notFound(vehicleId));

            vehicle.issueFuelRestoreCommand();
            vehicleRepository.save(vehicle);

            System.out.println("Fuel restore command processed for vehicle " + vehicleId);

        } catch (VehicleException e) {
            System.err.println("Fuel restore failed for vehicle " + vehicleId + ": " + e.getMessage());
            throw e;
        } catch (Exception e) {
            System.err.println("Failed to restore fuel for vehicle " + vehicleId + ": " + e.getMessage());
            throw new ValidationException("FUEL_RESTORE_PROCESS_ERROR",
                "Failed to restore fuel for vehicle " + vehicleId, e);
        }
    }

    public void updateVehicleLocation(String vehicleId, LocationPoint location) {
        try {
            Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> VehicleException.notFound(vehicleId));

            vehicle.processNewTelemetry(location);
            vehicleRepository.save(vehicle);

            VehicleUpdatedEvent event = new VehicleUpdatedEvent(vehicle, location);
            eventPublisher.publish(event);

            System.out.println("Updated location for vehicle " + vehicleId + ": " +
                             location.latitude() + ", " + location.longitude());

        } catch (VehicleException e) {
            System.err.println("Location update failed for vehicle " + vehicleId + ": " + e.getMessage());
            throw e;
        } catch (Exception e) {
            System.err.println("Failed to update location for vehicle " + vehicleId + ": " + e.getMessage());
            throw new ValidationException("LOCATION_UPDATE_ERROR",
                "Failed to update location for vehicle " + vehicleId, e);
        }
    }
}
