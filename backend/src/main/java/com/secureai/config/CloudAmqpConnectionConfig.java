package com.secureai.config;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.net.URI;

@Configuration
@ConditionalOnProperty(name = "secureai.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class CloudAmqpConnectionConfig {

    @Bean
    @Primary
    public ConnectionFactory rabbitConnectionFactory(
            @Value("${RABBITMQ_URL:}") String rabbitUrl,
            @Value("${spring.rabbitmq.host:localhost}") String host,
            @Value("${spring.rabbitmq.port:5672}") int port,
            @Value("${spring.rabbitmq.username:guest}") String username,
            @Value("${spring.rabbitmq.password:guest}") String password
    ) {
        if (rabbitUrl != null && !rabbitUrl.isBlank()) {
            return new CachingConnectionFactory(URI.create(rabbitUrl));
        }
        CachingConnectionFactory factory = new CachingConnectionFactory(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);
        return factory;
    }
}
