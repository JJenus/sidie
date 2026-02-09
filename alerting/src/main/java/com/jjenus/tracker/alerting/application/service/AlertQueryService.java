package com.jjenus.tracker.alerting.application.service;

import com.jjenus.tracker.alerting.domain.entity.TrackerAlert;
import com.jjenus.tracker.alerting.exception.AlertException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jjenus.tracker.alerting.infrastructure.repository.TrackerAlertRepository;
import org.springframework.cache.annotation.Cacheable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public  class AlertQueryService {
  private final TrackerAlertRepository alertRepository;

  public AlertQueryService(TrackerAlertRepository alertRepository) {
      this.alertRepository = alertRepository;
  }

  @Cacheable(value = "alerts", key = "#alertId")
  public TrackerAlert getAlertById(Long alertId) {
      return alertRepository.findById(alertId)
              .orElseThrow(() -> AlertException.alertNotFound(alertId));
  }

  @Cacheable(value = "alertStats", key = "'statistics_' + #startDate.toString()")
  public Map<String, Long> getAlertStatistics(Instant startDate, Instant endDate) {
      List<Object[]> stats = alertRepository.getAlertTypeStatistics(startDate);
      return stats.stream()
              .collect(Collectors.toMap(
                      obj -> (String) obj[0],
                      obj -> (Long) obj[1]
              ));
  }
}