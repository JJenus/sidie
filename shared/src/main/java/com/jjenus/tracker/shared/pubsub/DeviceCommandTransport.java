package com.jjenus.tracker.shared.pubsub;

import reactor.core.publisher.Mono;

public interface DeviceCommandTransport {
    Mono<Boolean> sendCommand(String deviceId, String command);
}
