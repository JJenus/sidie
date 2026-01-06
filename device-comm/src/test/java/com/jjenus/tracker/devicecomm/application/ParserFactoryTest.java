package com.jjenus.tracker.devicecomm.application;

import com.jjenus.tracker.devicecomm.domain.ITrackerProtocolParser;
import com.jjenus.tracker.devicecomm.exception.ProtocolException;
import com.jjenus.tracker.devicecomm.infrastructure.AutoseekerProtocolParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class ParserFactoryTest {

    private ParserFactory parserFactory;
    private AutoseekerProtocolParser autoseekerParser;

    @BeforeEach
    void setUp() {
        autoseekerParser = new AutoseekerProtocolParser();
        List<ITrackerProtocolParser> parsers = List.of(autoseekerParser);
        parserFactory = new ParserFactory(parsers);
    }

    @Test
    void testGetParserForAutoseekerData() {
        String autoseekerData = "$POS,DEV001,40.7128,-74.0060,55.5,1700000000,1#";

        ITrackerProtocolParser parser = parserFactory.getParser(autoseekerData);

        assertNotNull(parser);
        assertTrue(parser instanceof AutoseekerProtocolParser);
        assertEquals("Autoseeker", parser.getProtocolName());
    }

    @Test
    void testGetParserForUnknownData() {
        String unknownData = "UNKNOWN,FORMAT,DATA";

        ProtocolException exception = assertThrows(ProtocolException.class,
            () -> parserFactory.getParser(unknownData));

        assertEquals("PROTOCOL_PARSER_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void testGetParserForNullData() {
        ProtocolException exception = assertThrows(ProtocolException.class,
            () -> parserFactory.getParser(null));

        assertEquals("PROTOCOL_PARSER_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void testGetParserForEmptyData() {
        ProtocolException exception = assertThrows(ProtocolException.class,
            () -> parserFactory.getParser("new byte[0]"));

        assertEquals("PROTOCOL_PARSER_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void testGetParserByNameNotFound() {
        Optional<ITrackerProtocolParser> parser = parserFactory.getParserByName("UNKNOWN");

        assertFalse(parser.isPresent());
    }

    @Test
    void testGetParserByNameNull() {
        Optional<ITrackerProtocolParser> parser = parserFactory.getParserByName(null);

        assertFalse(parser.isPresent());
    }

    @Test
    void testFactoryWithEmptyParserList() {
        ParserFactory emptyFactory = new ParserFactory(List.of());

        String anyData = "any data";
        ProtocolException exception = assertThrows(ProtocolException.class,
            () -> emptyFactory.getParser(anyData));

        assertEquals("PROTOCOL_PARSER_NOT_FOUND", exception.getErrorCode());
    }
}
