package com.jjenus.tracker.alerting.application.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjenus.tracker.alerting.application.factory.AlertRuleFactory;
import com.jjenus.tracker.alerting.application.service.GeofenceService;
import com.jjenus.tracker.alerting.domain.RuleStateStore;
import com.jjenus.tracker.alerting.domain.parameters.RuleParametersMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class FactoryConfig {

    @Bean
    public RuleParametersMapper ruleParametersMapper(ObjectMapper objectMapper) {
        return new RuleParametersMapper(objectMapper);
    }

    @Bean
    public AlertRuleFactory alertRuleFactory(GeofenceService geofenceService, RuleStateStore stateStore, Clock clock,
                                             RuleParametersMapper ruleParametersMapper) {
        return new AlertRuleFactory(geofenceService, stateStore, clock, ruleParametersMapper);
    }
}
