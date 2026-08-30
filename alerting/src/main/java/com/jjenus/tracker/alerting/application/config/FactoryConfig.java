package com.jjenus.tracker.alerting.application.config;

import com.jjenus.tracker.alerting.application.factory.AlertRuleFactory;
import com.jjenus.tracker.alerting.application.service.GeofenceService;
import com.jjenus.tracker.alerting.domain.RuleStateStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FactoryConfig {

    @Bean
    public AlertRuleFactory alertRuleFactory(GeofenceService geofenceService, RuleStateStore stateStore) {
        return new AlertRuleFactory(geofenceService, stateStore);
    }
}
