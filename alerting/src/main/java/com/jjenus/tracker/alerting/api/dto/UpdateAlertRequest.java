package com.jjenus.tracker.alerting.api.dto;

import com.jjenus.tracker.alerting.domain.enums.AlertSeverity;
import jakarta.validation.constraints.Size;

public class UpdateAlertRequest {
    @Size(max = 2000, message = "Message must not exceed 2000 characters")
    private String message;

    private AlertSeverity severity;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public AlertSeverity getSeverity() { return severity; }
    public void setSeverity(AlertSeverity severity) { this.severity = severity; }
}
