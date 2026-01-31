package com.jjenus.tracker.devicecomm.infrastructure;

import com.jjenus.tracker.devicecomm.exception.ProtocolParseException;
import com.jjenus.tracker.shared.domain.LocationPoint;
import com.jjenus.tracker.devicecomm.exception.ProtocolException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AutoseekerProtocolParserTest {

    private AutoseekerProtocolParser parser;

    @BeforeEach
    void setUp() {
        parser = new AutoseekerProtocolParser();
    }

    @Test
    void testCanParseValidAutoseekerData() {
        String data = "$POS,DEV001,40.7128,-74.0060,55.5,1700000000,1#";

        assertTrue(parser.canParse(data));
    }

    @Test
    void testCanParseInvalidHeader() {
        String data = "$GPS,DEV001,40.7128,-74.0060,55.5,1700000000,1#";

        assertFalse(parser.canParse(data));
    }

    @Test
    void testCanParseNullData() {
        assertFalse(parser.canParse(null));
    }

    @Test
    void testCanParseEmptyData() {
        assertFalse(parser.canParse(null));
    }

    @Test
    void testParseValidAutoseekerData() throws ProtocolParseException {
        String data = "$POS,DEV001,40.7128,-74.0060,55.5,1700000000,1#";

        LocationPoint location = parser.parse(data);

        assertNotNull(location);
        assertTrue(location.isValid());
        assertEquals(40.7128, location.latitude(), 0.0001);
        assertEquals(-74.0060, location.longitude(), 0.0001);
        assertEquals(55.5f, location.speedKmh(), 0.1f);
    }

    @Test
    void testParseInvalidHeader() {
        String data = "$GPS,DEV001,40.7128,-74.0060,55.5,1700000000,1#";

        ProtocolException exception = assertThrows(ProtocolException.class,
            () -> parser.parse(data));

        assertEquals("PROTOCOL_INVALID_HEADER", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Autoseeker"));
    }

    @Test
    void testParseIncompleteData() {
        String data = "$POS,DEV001,40.7128";

        ProtocolException exception = assertThrows(ProtocolException.class,
            () -> parser.parse(data));

        assertEquals("PROTOCOL_PARSE_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Incomplete data packet"));
    }

    @Test
    void testParseMalformedNumber() {
        String data = "$POS,DEV001,invalid,-74.0060,55.5,1700000000,1#";

        ProtocolException exception = assertThrows(ProtocolException.class,
            () -> parser.parse(data));

        assertEquals("PROTOCOL_PARSE_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Autoseeker"));
    }

    @Test
    void testBuildFuelCutCommand() {
        String deviceId = "DEV-001";
        String command = parser.buildFuelCutCommand(deviceId);

        assertNotNull(command);
        String commandString = new String(command);
        assertEquals("$CMD,DEV-001,FUEL,OFF#", commandString);
    }

    @Test
    void testBuildEngineOnCommand() {
        String deviceId = "DEV-001";
        String command = parser.buildEngineOnCommand(deviceId);

        assertNotNull(command);
        String commandString = new String(command);
        assertEquals("$CMD,DEV-001,FUEL,ON#", commandString);
    }

    @Test
    void testGetProtocolName() {
        assertEquals("Autoseeker", parser.getProtocolName());
    }

    @Test
    void testParseWithDifferentFormats() throws ProtocolParseException {
        // Test with extra fields (should still parse basic data)
        String data = "$POS,DEV001,34.0522,-118.2437,75.0,1700000000,1,extra,fields#";

        LocationPoint location = parser.parse(data);

        assertNotNull(location);
        assertEquals(34.0522, location.latitude(), 0.0001);
        assertEquals(-118.2437, location.longitude(), 0.0001);
        assertEquals(75.0f, location.speedKmh(), 0.1f);
    }

    @Test
    void testParseZeroSpeed() throws ProtocolParseException {
        String data = "$POS,DEV001,40.7128,-74.0060,0.0,1700000000,0#";

        LocationPoint location = parser.parse(data);

        assertNotNull(location);
        assertEquals(0.0f, location.speedKmh(), 0.1f);
        assertTrue(location.isValid());
    }

    @Test
    void testParseNegativeSpeed() throws ProtocolParseException {
        // Even though negative speed doesn't make sense, parser should handle it
        String data = "$POS,DEV001,40.7128,-74.0060,-10.5,1700000000,1#";

        LocationPoint location = parser.parse(data);

        assertNotNull(location);
        assertEquals(-10.5f, location.speedKmh(), 0.1f);
        // Note: LocationPoint.isValid() will return false for negative speed
    }
}
