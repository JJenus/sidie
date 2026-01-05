package com.jjenus.tracker.devicecomm.infrastructure;

import com.jjenus.tracker.devicecomm.domain.ITrackerProtocolParser;
import com.jjenus.tracker.core.domain.LocationPoint;
import com.jjenus.tracker.devicecomm.exception.ProtocolException;
import java.time.Instant;

public class AutoseekerProtocolParser implements ITrackerProtocolParser {

    @Override
    public LocationPoint parse(byte[] rawData) throws ProtocolException {
        try {
            String dataString = new String(rawData);

            if (!dataString.startsWith("$POS,")) {
                throw ProtocolException.invalidHeader("Autoseeker");
            }

            String[] parts = dataString.split(",");
            if (parts.length < 7) {
                throw ProtocolException.parseError("Autoseeker", "Incomplete data packet");
            }

            double lat = Double.parseDouble(parts[2]);
            double lng = Double.parseDouble(parts[3]);
            float speed = Float.parseFloat(parts[4]);

            return new LocationPoint(lat, lng, speed, Instant.now());

        } catch (ProtocolException e) {
            throw e;
        } catch (Exception e) {
            throw ProtocolException.parseError("Autoseeker", e.getMessage());
        }
    }

    @Override
    public boolean canParse(byte[] rawData) {
        if (rawData == null || rawData.length == 0) return false;
        String dataString = new String(rawData);
        return dataString.startsWith("$POS,");
    }

    @Override
    public byte[] buildFuelCutCommand(String deviceId) {
        try {
            String command = String.format("$CMD,%s,FUEL,OFF#", deviceId);
            return command.getBytes();
        } catch (Exception e) {
            throw ProtocolException.commandBuildError("Autoseeker", "fuel cut");
        }
    }

    @Override
    public byte[] buildEngineOnCommand(String deviceId) {
        try {
            String command = String.format("$CMD,%s,FUEL,ON#", deviceId);
            return command.getBytes();
        } catch (Exception e) {
            throw ProtocolException.commandBuildError("Autoseeker", "engine on");
        }
    }

    @Override
    public String getProtocolName() {
        return "Autoseeker";
    }
}
