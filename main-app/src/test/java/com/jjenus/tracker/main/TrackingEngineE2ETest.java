package com.jjenus.tracker.main;

import com.jjenus.tracker.testsupport.AutoseekerDeviceSimulator;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrackingEngineE2ETest {

    @LocalServerPort
    private int port;

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    static GenericContainer<?> activeMQ = new GenericContainer<>("apache/activemq-artemis:latest")
            .withExposedPorts(61616, 8161)
            .withEnv("ARTEMIS_USER", "admin")
            .withEnv("ARTEMIS_PASSWORD", "admin");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.artemis.host", activeMQ::getHost);
        registry.add("spring.artemis.port", () -> activeMQ.getMappedPort(61616));
    }

    private static final String BASE_URL = "http://localhost";
    private static final int TCP_PORT = 8888;
    
    private final Map<String, String> testVehicles = new HashMap<>();
    private final Map<String, String> testTrackers = new HashMap<>();
    private final List<AutoseekerDeviceSimulator> simulators = new ArrayList<>();
    private final ExecutorService simulatorExecutor = Executors.newCachedThreadPool();

    @BeforeAll
    void setup() {
        RestAssured.baseURI = BASE_URL;
        RestAssured.port = port;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterAll
    void cleanup() {
        simulatorExecutor.shutdownNow();
        simulators.forEach(AutoseekerDeviceSimulator::stop);
        
        // Clean up test data
        testTrackers.values().forEach(trackerId -> 
            given().delete("/api/v1/trackers/{trackerId}", trackerId));
        
        testVehicles.values().forEach(vehicleId -> 
            given().delete("/api/v1/vehicles/{vehicleId}", vehicleId));
    }

    @Test
    @Order(1)
    @Timeout(30)
    void testHealthEndpoints() {
        given()
            .when()
                .get("/actuator/health")
            .then()
                .statusCode(200)
                .body("status", equalTo("UP"));

        given()
            .when()
                .get("/swagger-ui.html")
            .then()
                .statusCode(200);
    }

    @Test
    @Order(2)
    @Timeout(60)
    void testCreateVehicleAndTracker() {
        String vehicleId = "TEST-VEHICLE-001";
        String deviceId = "8168000005";
        String trackerId = "TEST-TRACKER-001";

        // Create Vehicle
        Map<String, Object> vehicleRequest = new HashMap<>();
        vehicleRequest.put("vehicleId", vehicleId);
        vehicleRequest.put("deviceId", deviceId);
        vehicleRequest.put("model", "Toyota Hilux");
        vehicleRequest.put("licensePlate", "ABC-123");
        vehicleRequest.put("vin", "VIN123456789");
        vehicleRequest.put("fuelLevel", 75.5);
        vehicleRequest.put("odometerKm", 12345.6);

        Response vehicleResponse = given()
            .contentType(ContentType.JSON)
            .body(vehicleRequest)
        .when()
            .post("/api/v1/vehicles");

        vehicleResponse.then()
            .statusCode(201)
            .body("vehicleId", equalTo(vehicleId))
            .body("deviceId", equalTo(deviceId));

        testVehicles.put(deviceId, vehicleId);

        // Create Tracker
        Map<String, Object> trackerRequest = new HashMap<>();
        trackerRequest.put("trackerId", trackerId);
        trackerRequest.put("deviceId", deviceId);
        trackerRequest.put("model", "Autoseeker AS-101");
        trackerRequest.put("protocol", "AUTOSEEKER");
        trackerRequest.put("firmwareVersion", "V2.1.8");
        trackerRequest.put("simNumber", "+12345678901");
        trackerRequest.put("vehicleId", vehicleId);

        Response trackerResponse = given()
            .contentType(ContentType.JSON)
            .body(trackerRequest)
        .when()
            .post("/api/v1/trackers");

        trackerResponse.then()
            .statusCode(201)
            .body("trackerId", equalTo(trackerId))
            .body("vehicleId", equalTo(vehicleId));

        testTrackers.put(deviceId, trackerId);

        // Verify assignment
        given()
            .when()
                .get("/api/v1/vehicles/{vehicleId}", vehicleId)
            .then()
                .statusCode(200)
                .body("trackerId", equalTo(trackerId));
    }

    @Test
    @Order(3)
    @Timeout(90)
    void testDeviceSimulatorAndLocationUpdates() throws InterruptedException {
        String deviceId = "8168000005";
        String trackerId = testTrackers.get(deviceId);
        
        assertNotNull(trackerId, "Tracker should be created first");

        // Start device simulator
        AutoseekerDeviceSimulator simulator = new AutoseekerDeviceSimulator("localhost", TCP_PORT, deviceId);
        simulators.add(simulator);
        
        simulatorExecutor.submit(() -> {
            simulator.start();
            try {
                Thread.sleep(60000); // Run for 60 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            simulator.stop();
        });

        // Wait for initial connection
        Thread.sleep(10000);

        // Verify tracker is online
        given()
            .when()
                .get("/api/v1/trackers/{trackerId}", trackerId)
            .then()
                .statusCode(200)
                .body("isOnline", equalTo(true));

        // Wait for location data
        Thread.sleep(15000);

        // Verify vehicle has location data
        given()
            .when()
                .get("/api/v1/vehicles/device/{deviceId}", deviceId)
            .then()
                .statusCode(200)
                .body("currentLocation", notNullValue())
                .body("currentLocation.latitude", notNullValue())
                .body("currentLocation.longitude", notNullValue())
                .body("lastTelemetryTime", notNullValue());
    }

    @Test
    @Order(4)
    @Timeout(60)
    void testTripCreation() {
        String deviceId = "8168000005";
        String vehicleId = testVehicles.get(deviceId);
        
        assertNotNull(vehicleId, "Vehicle should be created first");

        // Check for active trip
        given()
            .when()
                .get("/api/v1/trips/vehicle/{vehicleId}/active", vehicleId)
            .then()
                .statusCode(200)
                .body("vehicleId", equalTo(vehicleId))
                .body("isActive", equalTo(true));

        // Get all trips for vehicle
        Response tripsResponse = given()
            .when()
                .get("/api/v1/trips/vehicle/{vehicleId}", vehicleId);

        tripsResponse.then()
            .statusCode(200)
            .body("size()", greaterThan(0));
    }

    @Test
    @Order(5)
    @Timeout(60)
    void testCreateGeofenceAndAlertRules() {
        String deviceId = "8168000005";
        String vehicleId = testVehicles.get(deviceId);

        // Create Geofence
        Map<String, Object> geofenceRequest = new HashMap<>();
        geofenceRequest.put("vehicleId", vehicleId);
        geofenceRequest.put("name", "Warehouse Area");
        geofenceRequest.put("shapeType", "CIRCLE");
        geofenceRequest.put("centerLatitude", 22.582122);
        geofenceRequest.put("centerLongitude", 113.906633);
        geofenceRequest.put("radiusMeters", 500);
        geofenceRequest.put("active", true);
        geofenceRequest.put("createdBy", "e2e-test");

        Response geofenceResponse = given()
            .contentType(ContentType.JSON)
            .body(geofenceRequest)
        .when()
            .post("/alerts/geofences");

        String geofenceId = geofenceResponse.then()
            .statusCode(200)
            .extract()
            .path("geofenceId");

        // Create Overspeed Rule
        Map<String, Object> overspeedRule = new HashMap<>();
        overspeedRule.put("ruleKey", "overspeed-test-" + System.currentTimeMillis());
        overspeedRule.put("ruleName", "Test Overspeed Rule");
        overspeedRule.put("speedLimit", 80.0);
        overspeedRule.put("buffer", 5.0);
        overspeedRule.put("vehicleIds", List.of(vehicleId));
        overspeedRule.put("priority", 1);
        overspeedRule.put("enabled", true);

        given()
            .contentType(ContentType.JSON)
            .body(overspeedRule)
        .when()
            .post("/alerts/rules/templates/overspeed")
        .then()
            .statusCode(200)
            .body("ruleKey", notNullValue());

        // Create Geofence Rule
        Map<String, Object> geofenceRule = new HashMap<>();
        geofenceRule.put("ruleKey", "geofence-test-" + System.currentTimeMillis());
        geofenceRule.put("ruleName", "Test Geofence Rule");
        geofenceRule.put("geofenceId", geofenceId);
        geofenceRule.put("action", "BOTH");
        geofenceRule.put("vehicleIds", List.of(vehicleId));
        geofenceRule.put("priority", 2);
        geofenceRule.put("enabled", true);

        given()
            .contentType(ContentType.JSON)
            .body(geofenceRule)
        .when()
            .post("/alerts/rules/templates/geofence")
        .then()
            .statusCode(200)
            .body("ruleKey", notNullValue());
    }

    @Test
    @Order(6)
    @Timeout(90)
    void testAlertGeneration() throws InterruptedException {
        String deviceId = "8168000005";
        String vehicleId = testVehicles.get(deviceId);
        String trackerId = testTrackers.get(deviceId);

        // Send high-speed location to trigger overspeed alert
        AutoseekerDeviceSimulator simulator = simulators.stream()
            .filter(s -> s.getDeviceId().equals(deviceId))
            .findFirst()
            .orElseThrow();

        // Set high speed location
        simulator.setLocation(22.600000, 114.000000, 95.0);
        
        // Wait for alert processing
        Thread.sleep(10000);

        // Check for alerts
        given()
            .when()
                .get("/alerts/vehicle/{vehicleId}/recent?arg1=10", vehicleId)
            .then()
                .statusCode(200)
                .body("size()", greaterThan(0));

        // Check alert statistics
        given()
            .when()
                .get("/alerts/stats/unacknowledged")
            .then()
                .statusCode(200)
                .body("size()", greaterThan(0));
    }

    @Test
    @Order(7)
    @Timeout(60)
    void testMultipleDevicesStressTest() throws InterruptedException {
        int numDevices = 5;
        List<AutoseekerDeviceSimulator> stressSimulators = new ArrayList<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 1; i <= numDevices; i++) {
            String devId = "STRESS-" + i;
            String vehicleId = "STRESS-VEH-" + i;
            String trackerId = "STRESS-TRK-" + i;

            // Create vehicle and tracker
            createTestVehicleAndTracker(vehicleId, devId, trackerId);

            // Start simulator
            AutoseekerDeviceSimulator sim = new AutoseekerDeviceSimulator("localhost", TCP_PORT, devId);
            stressSimulators.add(sim);
            
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                sim.start();
                try {
                    Thread.sleep(30000); // Run for 30 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                sim.stop();
            }, simulatorExecutor);
            
            futures.add(future);
        }

        // Wait for all simulators to run
        Thread.sleep(35000);

        // Verify all vehicles have data
        given()
            .when()
                .get("/api/v1/vehicles/stale-telemetry?arg0=" + 
                     LocalDateTime.now().minusMinutes(5).toString())
            .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(numDevices));

        // Cleanup stress test data
        stressSimulators.forEach(AutoseekerDeviceSimulator::stop);
    }

    @Test
    @Order(8)
    @Timeout(60)
    void testCommandSendingToDevice() {
        String deviceId = "8168000005";
        String trackerId = testTrackers.get(deviceId);

        // Send fuel cut command
        Map<String, Object> commandRequest = new HashMap<>();
        commandRequest.put("trackerId", trackerId);
        commandRequest.put("commandType", "FUEL_CUT");
        commandRequest.put("commandData", "S20,1,1,1,1#");
        commandRequest.put("initiatedBy", "e2e-test");
        commandRequest.put("maxRetries", 3);

        Response commandResponse = given()
            .contentType(ContentType.JSON)
            .body(commandRequest)
        .when()
            .post("/api/v1/commands");

        String commandId = commandResponse.then()
            .statusCode(201)
            .extract()
            .path("commandId");

        // Wait for command to be sent
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Check command status
        given()
            .when()
                .get("/api/v1/commands/{commandId}", commandId)
            .then()
                .statusCode(200)
                .body("status", anyOf(equalTo("SENT"), equalTo("DELIVERED")));
    }

    @Test
    @Order(9)
    @Timeout(30)
    void testNotificationSystem() {
        // Get notifications
        given()
            .when()
                .get("/api/notifications?arg0=&arg1=&arg2=&arg3=&arg4={\"page\":0,\"size\":10,\"sort\":[]}")
            .then()
                .statusCode(200)
                .body("content", notNullValue());

        // Get notification templates
        given()
            .when()
                .get("/api/notifications/templates?arg0=&arg1=&arg2=&arg4={\"page\":0,\"size\":10,\"sort\":[]}")
            .then()
                .statusCode(200)
                .body("content", notNullValue());
    }

    @Test
    @Order(10)
    @Timeout(30)
    void testEndTripAndFinalVerification() {
        String deviceId = "8168000005";
        String vehicleId = testVehicles.get(deviceId);

        // End active trip
        given()
            .when()
                .post("/api/v1/trips/vehicle/{vehicleId}/end-active?arg1=MANUAL", vehicleId)
            .then()
                .statusCode(200);

        // Verify no active trip
        given()
            .when()
                .get("/api/v1/trips/vehicle/{vehicleId}/active", vehicleId)
            .then()
                .statusCode(404);

        // Final vehicle status check
        given()
            .when()
                .get("/api/v1/vehicles/{vehicleId}", vehicleId)
            .then()
                .statusCode(200)
                .body("vehicleId", equalTo(vehicleId))
                .body("currentLocation", notNullValue())
                .body("lastTelemetryTime", notNullValue());
    }

    private void createTestVehicleAndTracker(String vehicleId, String deviceId, String trackerId) {
        // Create Vehicle
        Map<String, Object> vehicleRequest = new HashMap<>();
        vehicleRequest.put("vehicleId", vehicleId);
        vehicleRequest.put("deviceId", deviceId);
        vehicleRequest.put("model", "Test Vehicle");
        vehicleRequest.put("licensePlate", "TEST-" + deviceId);
        vehicleRequest.put("vin", "VINTEST" + deviceId);
        vehicleRequest.put("fuelLevel", 50.0);
        vehicleRequest.put("odometerKm", 10000.0);

        given()
            .contentType(ContentType.JSON)
            .body(vehicleRequest)
        .when()
            .post("/api/v1/vehicles");

        // Create Tracker
        Map<String, Object> trackerRequest = new HashMap<>();
        trackerRequest.put("trackerId", trackerId);
        trackerRequest.put("deviceId", deviceId);
        trackerRequest.put("model", "Test Tracker");
        trackerRequest.put("protocol", "AUTOSEEKER");
        trackerRequest.put("firmwareVersion", "V1.0");
        trackerRequest.put("simNumber", "+123456789" + deviceId);
        trackerRequest.put("vehicleId", vehicleId);

        given()
            .contentType(ContentType.JSON)
            .body(trackerRequest)
        .when()
            .post("/api/v1/trackers");

        testVehicles.put(deviceId, vehicleId);
        testTrackers.put(deviceId, trackerId);
    }
}