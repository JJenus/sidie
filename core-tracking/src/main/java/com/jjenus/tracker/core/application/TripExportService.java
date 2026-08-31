package com.jjenus.tracker.core.application;

import com.jjenus.tracker.core.domain.entity.Trip;
import com.jjenus.tracker.core.infrastructure.repository.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

@Service
@Transactional(readOnly = true)
public class TripExportService {

    private static final String CSV_HEADER =
            "trip_id,vehicle_id,start_time,end_time,duration_minutes," +
            "start_reason,end_reason,start_lat,start_lon,end_lat,end_lon," +
            "total_distance_km,avg_speed_kmh,max_speed_kmh,idle_minutes," +
            "fuel_liters,is_active";

    private static final DateTimeFormatter DF = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneId.of("UTC"));

    private final TripRepository tripRepository;
    private final Clock clock;

    public TripExportService(TripRepository tripRepository, Clock clock) {
        this.tripRepository = tripRepository;
        this.clock = clock;
    }

    public void exportToCsv(
            String vehicleId,
            Instant startDate,
            Instant endDate,
            OutputStream out
    ) {
        try (PrintWriter w = new PrintWriter(out)) {
            w.println(CSV_HEADER);

            Consumer<Trip> rowWriter = trip -> {
                String[] row = toRow(trip);
                w.println(String.join(",", row));
            };

            if (vehicleId != null && startDate != null && endDate != null) {
                tripRepository.findByVehicleVehicleIdAndStartTimeBetween(vehicleId, startDate, endDate)
                        .forEach(rowWriter);
            } else if (vehicleId != null && startDate != null) {
                tripRepository.findByVehicleVehicleId(vehicleId).stream()
                        .filter(t -> t.getStartTime().compareTo(startDate) >= 0)
                        .forEach(rowWriter);
            } else if (vehicleId != null) {
                tripRepository.findByVehicleVehicleId(vehicleId).forEach(rowWriter);
            } else {
                tripRepository.findAll().forEach(rowWriter);
            }
        }
    }

    private String[] toRow(Trip trip) {
        return new String[] {
                escape(trip.getTripId()),
                escape(trip.getVehicle() != null ? trip.getVehicle().getVehicleId() : ""),
                trip.getStartTime() != null ? DF.format(trip.getStartTime()) : "",
                trip.getEndTime() != null ? DF.format(trip.getEndTime()) : "",
                String.valueOf(durationMinutes(trip)),
                escape(trip.getStartReason() != null ? trip.getStartReason().name() : ""),
                escape(trip.getEndReason() != null ? trip.getEndReason().name() : ""),
                trip.getStartLocation() != null ? String.valueOf(trip.getStartLocation().getLatitude()) : "",
                trip.getStartLocation() != null ? String.valueOf(trip.getStartLocation().getLongitude()) : "",
                trip.getEndLocation() != null ? String.valueOf(trip.getEndLocation().getLatitude()) : "",
                trip.getEndLocation() != null ? String.valueOf(trip.getEndLocation().getLongitude()) : "",
                trip.getTotalDistanceKm() != null ? String.valueOf(trip.getTotalDistanceKm()) : "",
                trip.getAverageSpeedKmh() != null ? String.valueOf(trip.getAverageSpeedKmh()) : "",
                trip.getMaxSpeedKmh() != null ? String.valueOf(trip.getMaxSpeedKmh()) : "",
                trip.getIdleTimeMinutes() != null ? String.valueOf(trip.getIdleTimeMinutes()) : "",
                trip.getFuelConsumedLiters() != null ? String.valueOf(trip.getFuelConsumedLiters()) : "",
                String.valueOf(Boolean.TRUE.equals(trip.getIsActive()))
        };
    }

    private long durationMinutes(Trip trip) {
        if (trip.getStartTime() == null) return 0;
        Instant end = trip.getEndTime() != null ? trip.getEndTime() : clock.instant();
        return java.time.Duration.between(trip.getStartTime(), end).toMinutes();
    }

    private String escape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
