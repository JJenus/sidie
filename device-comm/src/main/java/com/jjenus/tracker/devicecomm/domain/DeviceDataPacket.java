package com.jjenus.tracker.devicecomm.domain;

import java.time.Instant;

public record DeviceDataPacket(
    String deviceId,
    byte[] rawData,
    Instant receivedAt,
    String sourceIp
) {}
