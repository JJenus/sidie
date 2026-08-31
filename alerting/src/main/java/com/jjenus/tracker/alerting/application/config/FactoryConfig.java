package com.jjenus.tracker.alerting.application.config;

import com.jjenus.tracker.alerting.application.factory.AlertRuleFactory;
import com.jjenus.tracker.alerting.application.service.GeofenceService;
import com.jjenus.tracker.alerting.domain.RuleStateStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class FactoryConfig {

    @Bean
    public AlertRuleFactory alertRuleFactory(GeofenceService geofenceService, RuleStateStore stateStore, Clock clock) {
        return new AlertRuleFactory(geofenceService, stateStore, clock);
    }
}
