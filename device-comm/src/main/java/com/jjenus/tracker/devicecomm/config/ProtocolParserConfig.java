package com.jjenus.tracker.devicecomm.config;

import com.jjenus.tracker.devicecomm.domain.ITrackerProtocolParser;
import com.jjenus.tracker.devicecomm.infrastructure.AutoseekerProtocolParser;
import com.jjenus.tracker.devicecomm.infrastructure.GT06ProtocolParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ProtocolParserConfig {
    
    @Bean
    public List<ITrackerProtocolParser> protocolParsers() {
        return List.of(
            new GT06ProtocolParser(),
            new AutoseekerProtocolParser()
        );
    }
}