package com.jjenus.tracker.devicecomm.application;

import com.jjenus.tracker.devicecomm.domain.DeviceDataPacket;
import com.jjenus.tracker.devicecomm.domain.ITrackerProtocolParser;
import com.jjenus.tracker.shared.events.LocationDataEvent;
import com.jjenus.tracker.shared.domain.LocationPoint;
import com.jjenus.tracker.shared.pubsub.EventPublisher;
import com.jjenus.tracker.devicecomm.exception.ProtocolException;
import com.jjenus.tracker.devicecomm.exception.DeviceException;
import org.springframework.stereotype.Service;

@Service
public class DeviceDataProcessor {
    private final ParserFactory parserFactory;
    private final EventPublisher eventPublisher;

    public DeviceDataProcessor(ParserFactory parserFactory, EventPublisher eventPublisher) {
        this.parserFactory = parserFactory;
        this.eventPublisher = eventPublisher;
    }

    public void processDeviceData(DeviceDataPacket packet) {
        try {
            ITrackerProtocolParser parser = parserFactory.getParser(packet.rawData());
            LocationPoint location = parser.parse(packet.rawData());

            LocationDataEvent event = new LocationDataEvent(
                packet.deviceId(),
                location,
                parser.getProtocolName()
            );

            eventPublisher.publish(event);
            
        } catch (ProtocolException | DeviceException e) {
            throw e;
        } catch (Exception e) {
            throw new com.jjenus.tracker.shared.exception.InfrastructureException(
                "DEVICE_DATA_PROCESS_ERROR",
                "Failed to process device data for " + packet.deviceId(),
                e
            );
        }
    }
}