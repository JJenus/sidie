package com.jjenus.tracker.notification.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class UpdatePreferencesRequest {
    
    @NotEmpty(message = "Preferences list cannot be empty")
    @Valid
    private List<PreferenceUpdateDto> preferences;
    
    // Getters and Setters
    public List<PreferenceUpdateDto> getPreferences() { return preferences; }
    public void setPreferences(List<PreferenceUpdateDto> preferences) { this.preferences = preferences; }
    
    public static class PreferenceUpdateDto {
        @NotEmpty(message = "Alert type is required")
        private String alertType;
        
        private boolean enabled = true;
        
        @NotEmpty(message = "At least one channel must be specified")
        private List<String> channels;
        
        // Getters and Setters
        public String getAlertType() { return alertType; }
        public void setAlertType(String alertType) { this.alertType = alertType; }
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public List<String> getChannels() { return channels; }
        public void setChannels(List<String> channels) { this.channels = channels; }
    }
}
