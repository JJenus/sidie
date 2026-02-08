package com.jjenus.tracker.alerting.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {

        RedisCacheConfiguration defaultConfig =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofHours(1))
                        .disableCachingNullValues()
                        .prefixCacheNameWith("cache:")
                        .serializeKeysWith(RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                        .serializeValuesWith(RedisSerializationContext.SerializationPair
                                .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)));

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        cacheConfigs.put("alertRules", defaultConfig.entryTtl(Duration.ofHours(12)));
        cacheConfigs.put("vehicleRules", defaultConfig.entryTtl(Duration.ofHours(6)));
        cacheConfigs.put("geofences", defaultConfig.entryTtl(Duration.ofHours(12)));
        cacheConfigs.put("vehicleGeofences", defaultConfig.entryTtl(Duration.ofHours(6)));

        cacheConfigs.put("alerts", defaultConfig.entryTtl(Duration.ofMinutes(20)));
        cacheConfigs.put("vehicleAlerts", defaultConfig.entryTtl(Duration.ofMinutes(10)));

        cacheConfigs.put("alertStats", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put("geofenceStats", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}