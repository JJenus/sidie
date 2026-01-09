package com.jjenus.tracker.core.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "geofence_points")
public class GeofencePoint {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "point_id")
    private Long pointId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "geofence_id", nullable = false)
    private Geofence geofence;
    
    @Column(name = "point_order", nullable = false)
    private Integer pointOrder;
    
    @Column(name = "latitude", precision = 10, scale = 8, nullable = false)
    private Double latitude;
    
    @Column(name = "longitude", precision = 11, scale = 8, nullable = false)
    private Double longitude;
    
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    
    // Getters and Setters
    public Long getPointId() { return pointId; }
    public void setPointId(Long pointId) { this.pointId = pointId; }
    
    public Geofence getGeofence() { return geofence; }
    public void setGeofence(Geofence geofence) { this.geofence = geofence; }
    
    public Integer getPointOrder() { return pointOrder; }
    public void setPointOrder(Integer pointOrder) { this.pointOrder = pointOrder; }
    
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}