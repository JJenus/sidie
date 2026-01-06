package com.jjenus.tracker.devicecomm.domain;

import com.jjenus.tracker.shared.domain.LocationPoint;
import com.jjenus.tracker.devicecomm.exception.ProtocolParseException;

public interface ITrackerProtocolParser {
    LocationPoint parse(String rawData) throws ProtocolParseException;
    boolean canParse(String rawData);
    String buildFuelCutCommand(String deviceId);
    String buildEngineOnCommand(String deviceId);
    String getProtocolName();
}

