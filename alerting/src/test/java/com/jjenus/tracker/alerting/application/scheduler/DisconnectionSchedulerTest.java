package com.jjenus.tracker.alerting.application.scheduler;

import com.jjenus.tracker.alerting.application.dedup.AlertDeduplicator;
import com.jjenus.tracker.alerting.domain.AlertDetectedEvent;
import com.jjenus.tracker.alerting.domain.enums.AlertSeverity;
import com.jjenus.tracker.alerting.domain.enums.AlertType;
import com.jjenus.tracker.shared.domain.LocationPoint;
import com.jjenus.tracker.shared.pubsub.EventPublisher;
import com.jjenus.tracker.shared.redis.VehicleActivityTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisconnectionSchedulerTest {

    @Mock
    private VehicleActivityTracker activityTracker;

    @Mock
    private AlertDeduplicator deduplicator;

    @Mock
    private EventPublisher eventPublisher;

    private DisconnectionScheduler scheduler;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-30T14:00:00Z"), ZoneOffset.UTC);
        scheduler = new DisconnectionScheduler(activityTracker, deduplicator, eventPublisher, clock);
        ReflectionTestUtils.setField(scheduler, "thresholdMinutes", 5);
    }

    @Test
    void checkDisconnectedVehicles_silentVehicle_publishesAlert() {
        Clock fixed = Clock.fixed(
                Instant.parse("2026-08-30T14:00:00Z"),
                ZoneOffset.UTC
        );
        Instant tenMinutesAgo = fixed.instant().minusSeconds(600);

        when(activityTracker.getAllActiveVehicleIds()).thenReturn(Set.of("v1"));
        when(activityTracker.getLastSeen("v1")).thenReturn(Optional.of(tenMinutesAgo));
        when(deduplicator.tryAcquire(any(AlertDetectedEvent.class))).thenReturn(true);

        scheduler.checkDisconnectedVehicles();

        ArgumentCaptor<AlertDetectedEvent> captor = ArgumentCaptor.forClass(AlertDetectedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().getVehicleId()).isEqualTo("v1");
        assertThat(captor.getValue().getAlertType()).isEqualTo(AlertType.DEVICE_DISCONNECTED);
    }

    @Test
    void checkDisconnectedVehicles_vehicleWithinThreshold_doesNotPublish() {
        Instant oneMinuteAgo = fixed().instant().minusSeconds(60);

        when(activityTracker.getAllActiveVehicleIds()).thenReturn(Set.of("v1"));
        when(activityTracker.getLastSeen("v1")).thenReturn(Optional.of(oneMinuteAgo));

        scheduler.checkDisconnectedVehicles();

        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void checkDisconnectedVehicles_vehicleNotSeen_doesNotCrash() {
        when(activityTracker.getAllActiveVehicleIds()).thenReturn(Set.of("v1"));
        when(activityTracker.getLastSeen("v1")).thenReturn(Optional.empty());

        scheduler.checkDisconnectedVehicles();

        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void checkDisconnectedVehicles_multipleVehicles_publishesForEach() {
        Instant tenMinutesAgo = fixed().instant().minusSeconds(600);

        when(activityTracker.getAllActiveVehicleIds()).thenReturn(Set.of("v1", "v2"));
        when(activityTracker.getLastSeen("v1")).thenReturn(Optional.of(tenMinutesAgo));
        when(activityTracker.getLastSeen("v2")).thenReturn(Optional.of(tenMinutesAgo));
        when(deduplicator.tryAcquire(any())).thenReturn(true);

        scheduler.checkDisconnectedVehicles();

        verify(eventPublisher, times(2)).publish(any(AlertDetectedEvent.class));
    }

    @Test
    void checkDisconnectedVehicles_allActive_doesNotPublish() {
        Instant justNow = fixed().instant();
        when(activityTracker.getAllActiveVehicleIds()).thenReturn(Set.of("v1"));
        when(activityTracker.getLastSeen("v1")).thenReturn(Optional.of(justNow));

        scheduler.checkDisconnectedVehicles();

        verify(eventPublisher, never()).publish(any());
    }

    private static Clock fixed() {
        return Clock.fixed(Instant.parse("2026-08-30T14:00:00Z"), ZoneOffset.UTC);
    }
}
