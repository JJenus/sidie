package com.jjenus.tracker.devicecomm.application;

import com.jjenus.tracker.shared.pubsub.DeviceCommandBuilder;
import com.jjenus.tracker.devicecomm.infrastructure.GT06ProtocolParser;
import org.springframework.stereotype.Component;

@Component
public class GT06CommandBuilderAdapter implements DeviceCommandBuilder {
    private final GT06ProtocolParser parser;

    public GT06CommandBuilderAdapter(GT06ProtocolParser parser) {
        this.parser = parser;
    }

    @Override
    public boolean supports(String protocol) {
        return "GT06".equalsIgnoreCase(protocol);
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
