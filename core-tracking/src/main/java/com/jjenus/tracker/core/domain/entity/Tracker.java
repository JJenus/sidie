package com.jjenus.tracker.core.domain.entity;

import com.jjenus.tracker.core.domain.enums.TrackerStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trackers")
@Getter
@Setter
public class Tracker {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracker_id", length = 50)
    private String trackerId;
    
    @Column(name = "model", length = 100)
    private String model;
    
    @Column(name = "protocol", length = 20)
    private String protocol;
    
    @Column(name = "firmware_version", length = 50)
    private String firmwareVersion;
    
    @Column(name = "sim_number", length = 20)
    private String simNumber;
    
    @Column(name = "battery_level")
    private Float batteryLevel;
    
    @Column(name = "signal_strength")
    private Integer signalStrength;
    
    @Column(name = "is_online")
    private Boolean isOnline = false;
    
    @Column(name = "last_seen")
    private Instant lastSeen;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private TrackerStatus status = TrackerStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;
    
    @OneToMany(mappedBy = "tracker", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("recordedAt DESC")
    private List<TrackerLocation> locations = new ArrayList<>();
    
    @OneToMany(mappedBy = "tracker", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TrackerRawData> rawDataEntries = new ArrayList<>();
    
    @OneToMany(mappedBy = "tracker", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TrackerCommand> commands = new ArrayList<>();
    
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    
    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
    
    // Business methods
    public void updateLastSeen() {
        this.lastSeen = Instant.now();
        this.isOnline = true;
    }
    
    public void updateBatteryLevel(Float level) {
        this.batteryLevel = level;
    }
    
    public void addLocation(TrackerLocation location) {
        locations.add(location);
        location.setTracker(this);
    }
    
    public void addRawData(TrackerRawData rawData) {
        rawDataEntries.add(rawData);
        rawData.setTracker(this);
    }
    
    public TrackerLocation getLatestLocation() {
        return locations.isEmpty() ? null : locations.get(0);
    }

}