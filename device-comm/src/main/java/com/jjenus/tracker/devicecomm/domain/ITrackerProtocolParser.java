package com.jjenus.tracker.devicecomm.domain;

import com.jjenus.tracker.core.domain.LocationPoint;
import com.jjenus.tracker.devicecomm.exception.ProtocolParseException;

public interface ITrackerProtocolParser {
    LocationPoint parse(byte[] rawData) throws ProtocolParseException;
    boolean canParse(byte[] rawData);
    byte[] buildFuelCutCommand(String deviceId);
    byte[] buildEngineOnCommand(String deviceId);
    String getProtocolName();
}

