package com.jjenus.tracker.devicecomm.application;

import com.jjenus.tracker.devicecomm.domain.ITrackerProtocolParser;
import com.jjenus.tracker.devicecomm.exception.ProtocolException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ParserFactory {
    private final List<ITrackerProtocolParser> parsers;

    public ParserFactory(List<ITrackerProtocolParser> parsers) {
        this.parsers = parsers;
    }

    public ITrackerProtocolParser getParser(String rawData) {
        return parsers.stream()
            .filter(parser -> parser.canParse(rawData))
            .findFirst()
            .orElseThrow(() -> ProtocolException.parserNotFound(rawData));
    }

    public Optional<ITrackerProtocolParser> getParserByName(String protocolName) {
        return parsers.stream()
            .filter(parser -> parser.getProtocolName().equalsIgnoreCase(protocolName))
            .findFirst();
    }
}
