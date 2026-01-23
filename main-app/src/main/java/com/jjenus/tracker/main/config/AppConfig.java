package com.jjenus.tracker.main.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.jjenus.tracker.shared.domain.ConnectionInfo;
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import jakarta.jms.ConnectionFactory;

@Configuration
@EnableJms
public class AppConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Essential modules for Java 17+ and records
        mapper.registerModule(new JavaTimeModule());          // For Instant
        mapper.registerModule(new ParameterNamesModule());    // For record parameter names

        // Configure for better compatibility
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        // Enable polymorphic types for DomainEvent hierarchy
        mapper.activateDefaultTyping(
                mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
        );

        return mapper;
    }
    
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                      ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        
        GenericJackson2JsonRedisSerializer serializer = 
            new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        
        return template;
    }
    
    @Bean
    public RedisTemplate<String, ConnectionInfo> connectionInfoRedisTemplate(
            RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        
        RedisTemplate<String, ConnectionInfo> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        
        GenericJackson2JsonRedisSerializer serializer = 
            new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setValueSerializer(serializer);
        
        return template;
    }
    
//    @Bean
//    public JmsListenerContainerFactory<?> jmsListenerContainerFactory(
//            ConnectionFactory connectionFactory,
//            DefaultJmsListenerContainerFactoryConfigurer configurer) {
//
//        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
//        configurer.configure(factory, connectionFactory);
//        factory.setConcurrency("3-10");
//        factory.setErrorHandler(t -> {
//            System.err.println("Error in JMS listener: " + t.getMessage());
//        });
//        return factory;
//    }
//
//    @Bean
//    public MessageConverter jacksonJmsMessageConverter(ObjectMapper objectMapper) {
//        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
//        converter.setTargetType(MessageType.TEXT);
//        converter.setTypeIdPropertyName("_type");
//        converter.setObjectMapper(objectMapper);
//        return converter;
//    }
}