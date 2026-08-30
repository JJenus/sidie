package com.jjenus.tracker.alerting.application.scheduler;

import com.jjenus.tracker.alerting.application.dedup.AlertDeduplicator;
import com.jjenus.tracker.alerting.domain.AlertDetectedEvent;
import com.jjenus.tracker.alerting.domain.enums.AlertSeverity;
import com.jjenus.tracker.alerting.domain.enums.AlertType;
import com.jjenus.tracker.shared.domain.LocationPoint;
import com.jjenus.tracker.shared.pubsub.EventPublisher;
import com.jjenus.tracker.shared.redis.VehicleActivityTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

@Component
public class DisconnectionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DisconnectionScheduler.class);

    private final VehicleActivityTracker activityTracker;
    private final AlertDeduplicator deduplicator;
    private final EventPublisher eventPublisher;

    @Value("${alerting.disconnect.threshold-minutes:5}")
    private int thresholdMinutes;

    public DisconnectionScheduler(
            VehicleActivityTracker activityTracker,
            AlertDeduplicator deduplicator,
            EventPublisher eventPublisher) {
        this.activityTracker = activityTracker;
        this.deduplicator = deduplicator;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${alerting.disconnect.check-interval-ms:60000}")
    public void checkDisconnectedVehicles() {
        Set<String> activeVehicles = activityTracker.getAllActiveVehicleIds();
        Instant now = Instant.now();
        int disconnected = 0;

        for (String vehicleId : activeVehicles) {
            Optional<Instant> lastSeen = activityTracker.getLastSeen(vehicleId);
            if (lastSeen.isEmpty()) {
                continue;
            }

            Duration silence = Duration.between(lastSeen.get(), now);
            if (silence.toMinutes() >= thresholdMinutes) {
                disconnected++;
                raiseDisconnectedAlert(vehicleId, lastSeen.get(), silence);
            }
        }

        if (disconnected > 0) {
            logger.info("Detected {} disconnected vehicles (threshold={}min)",
                    disconnected, thresholdMinutes);
        }
    }

    private void raiseDisconnectedAlert(String vehicleId, Instant lastSeen, Duration silence) {
        AlertDetectedEvent event = new AlertDetectedEvent(
                "device-disconnect",
                AlertType.DEVICE_DISCONNECTED,
                vehicleId,
                String.format("Vehicle %s has been silent for %d minutes (last seen: %s)",
                        vehicleId, silence.toMinutes(), lastSeen),
                AlertSeverity.WARNING,
                null
        );

        if (deduplicator.tryAcquire(event)) {
            logger.warn("Vehicle {} is disconnected (silent for {} minutes)",
                    vehicleId, silence.toMinutes());
            eventPublisher.publish(event);
        }
    }
}
