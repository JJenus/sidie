package com.jjenus.tracker.notification.application.service;

import com.jjenus.tracker.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailNotificationService implements NotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationService.class);
    
    private final JavaMailSender mailSender;
    
    @Value("${notification.email.from:alerts@tracking-system.com}")
    private String fromEmail;
    
    @Value("${notification.email.enabled:false}")
    private boolean enabled;
    
    public EmailNotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }
    
    @Override
    public void send(Notification notification) {
        if (!enabled) {
            logger.warn("Email notifications are disabled");
            notification.markAsFailed("Email notifications are disabled");
            return;
        }
        
        try {
            notification.markAsSending();
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            
            helper.setFrom(fromEmail);
            helper.setTo(notification.getRecipient());
            helper.setSubject(notification.getTitle());
            helper.setText(notification.getMessage(), false); // Plain text for now
            
            mailSender.send(message);
            
            notification.markAsSent();
            logger.info("Email notification sent to: {}", notification.getRecipient());
            
        } catch (Exception e) {
            logger.error("Failed to send email notification", e);
            notification.markAsFailed(e.getMessage());
            throw new RuntimeException("Failed to send email notification", e);
        }
    }
    
    @Override
    public boolean isAvailable() {
        return enabled && mailSender != null;
    }
    
    @Override
    public String getChannel() {
        return "EMAIL";
    }
}
