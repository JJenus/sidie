package com.jjenus.tracker.devicecomm.application;

import com.jjenus.tracker.shared.pubsub.DeviceCommandBuilder;
import com.jjenus.tracker.devicecomm.infrastructure.AutoseekerProtocolParser;
import org.springframework.stereotype.Component;

@Component
public class AutoseekerCommandBuilder implements DeviceCommandBuilder {
    private final AutoseekerProtocolParser parser;

    public AutoseekerCommandBuilder(AutoseekerProtocolParser parser) {
        this.parser = parser;
    }

    @Override
    public boolean supports(String protocol) {
        return "AUTOSEEKER".equalsIgnoreCase(protocol);
    }

    @Override
    public String buildFuelCutCommand(String deviceId) {
        return parser.buildFuelCutCommand(deviceId);
    }

    @Override
    public String buildEngineOnCommand(String deviceId) {
        return parser.buildEngineOnCommand(deviceId);
    }
}
