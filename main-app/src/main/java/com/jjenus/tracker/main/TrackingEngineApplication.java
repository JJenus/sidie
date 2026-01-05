package com.jjenus.tracker.main;

import com.jjenus.tracker.devicecomm.application.DeviceDataProcessor;
import com.jjenus.tracker.devicecomm.application.ParserFactory;
import com.jjenus.tracker.devicecomm.domain.ITrackerProtocolParser;
import com.jjenus.tracker.devicecomm.infrastructure.AutoseekerProtocolParser;
import com.jjenus.tracker.devicecomm.infrastructure.SocketServer;
import com.jjenus.tracker.core.application.VehicleCommandService;
import com.jjenus.tracker.core.application.VehicleQueryService;
import com.jjenus.tracker.core.infrastructure.IVehicleRepository;
import com.jjenus.tracker.core.infrastructure.InMemoryVehicleRepository;
import com.jjenus.tracker.alerting.application.AlertingEngine;
import com.jjenus.tracker.alerting.application.AlertRuleEvaluationService;
import com.jjenus.tracker.alerting.domain.MaxSpeedRule;
import com.jjenus.tracker.alerting.domain.IdleTimeRule;
import com.jjenus.tracker.shared.pubsub.SimpleEventBus;
import com.jjenus.tracker.devicecomm.domain.LocationDataEvent;
import com.jjenus.tracker.core.domain.VehicleUpdatedEvent;
import com.jjenus.tracker.devicecomm.domain.FuelCutCommand;
import com.jjenus.tracker.alerting.domain.AlertEvent;
import java.time.Duration;
import java.util.List;

public class TrackingEngineApplication {
    private SimpleEventBus eventBus;
    private SocketServer socketServer;
    private AlertingEngine alertingEngine;
    private VehicleCommandService vehicleCommandService;
    private boolean running;
    
    public void start() {
        try {
            System.out.println("Starting Tracking Engine...");
            
            // 1. Initialize Event Bus (Pub/Sub)
            eventBus = new SimpleEventBus();
            
            // 2. Initialize repositories
            IVehicleRepository vehicleRepository = new InMemoryVehicleRepository();
            
            // 3. Initialize Device Communication BC
            List<ITrackerProtocolParser> parsers = List.of(
                new AutoseekerProtocolParser()
            );
            
            ParserFactory parserFactory = new ParserFactory(parsers);
            DeviceDataProcessor dataProcessor = new DeviceDataProcessor(parserFactory, eventBus);
            
            // 4. Initialize Core Tracking BC
            vehicleCommandService = new VehicleCommandService(vehicleRepository, eventBus);
            VehicleQueryService vehicleQueryService = new VehicleQueryService(vehicleRepository);
            
            // 5. Initialize Alerting BC
            AlertRuleEvaluationService evaluationService = new AlertRuleEvaluationService();
            alertingEngine = new AlertingEngine(eventBus, evaluationService);
            
            // Register alert rules
            alertingEngine.registerRule(new MaxSpeedRule("MAX_SPEED_100", 100.0f));
            alertingEngine.registerRule(new IdleTimeRule("IDLE_30_MIN", Duration.ofMinutes(30)));
            
            // 6. Set up event subscriptions
            setupEventSubscriptions(vehicleRepository, vehicleCommandService, alertingEngine);
            
            // 7. Start Socket Server
            socketServer = new SocketServer(8888, dataProcessor);
            new Thread(() -> {
                try {
                    socketServer.start();
                } catch (Exception e) {
                    System.err.println("Socket server error: " + e.getMessage());
                }
            }).start();
            
            running = true;
            System.out.println("Tracking Engine started successfully");
            
            // Keep application running
            while (running) {
                Thread.sleep(1000);
            }
            
        } catch (Exception e) {
            System.err.println("Failed to start Tracking Engine: " + e.getMessage());
            stop();
        }
    }
    
    private void setupEventSubscriptions(IVehicleRepository vehicleRepository,
                                        VehicleCommandService commandService,
                                        AlertingEngine alertingEngine) {
        // Subscribe to LocationDataEvent (Device Comm -> Core Tracking)
        eventBus.subscribe(LocationDataEvent.class, event -> {
            String vehicleId = "VEH_" + event.getDeviceId();
            commandService.updateVehicleLocation(vehicleId, event.getLocation());
        });
        
        // Subscribe to VehicleUpdatedEvent (Core Tracking -> Alerting)
        eventBus.subscribe(VehicleUpdatedEvent.class, event -> {
            alertingEngine.processVehicleUpdate(event.getVehicle(), event.getNewLocation());
        });
        
        // Subscribe to AlertEvent (Alerting -> Notification systems)
        eventBus.subscribe(AlertEvent.class, event -> {
            System.out.println("ALERT: " + event.getVehicleId() + " - " + event.getMessage());
        });
        
        // Subscribe to FuelCutCommand (Core Tracking -> Device Comm)
        eventBus.subscribe(FuelCutCommand.class, command -> {
            System.out.println("Received fuel cut command for device " + command.getDeviceId());
        });
    }
    
    public void stop() {
        System.out.println("Stopping Tracking Engine...");
        running = false;
        
        if (socketServer != null) {
            socketServer.stop();
        }
        
        if (eventBus != null) {
            eventBus.shutdown();
        }
        
        System.out.println("Tracking Engine stopped");
    }
    
    public static void main(String[] args) {
        TrackingEngineApplication app = new TrackingEngineApplication();
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
        
        // Start the application
        app.start();
    }
}
