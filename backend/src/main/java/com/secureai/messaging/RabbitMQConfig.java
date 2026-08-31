package com.secureai.messaging;

import tools.jackson.databind.json.JsonMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
@ConditionalOnProperty(name = "secureai.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMQConfig {

    @Value("${secureai.rabbitmq.prefetch:1}")
    private int prefetch;

    private final JsonMapper jsonMapper;

    public RabbitMQConfig(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Bean
    DirectExchange analysisExchange() {
        return ExchangeBuilder.directExchange(RabbitTopology.EXCHANGE).durable(true).build();
    }

    @Bean
    Queue analysisDlq() {
        return QueueBuilder.durable(RabbitTopology.DLQ).build();
    }

    @Bean
    Queue analysisQueue() {
        return QueueBuilder.durable(RabbitTopology.QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", RabbitTopology.DLQ)
                .build();
    }

    @Bean
    Queue statusQueue() {
        return QueueBuilder.durable(RabbitTopology.STATUS_QUEUE).build();
    }

    @Bean
    Binding analysisBinding(Queue analysisQueue, DirectExchange analysisExchange) {
        return BindingBuilder.bind(analysisQueue).to(analysisExchange).with(RabbitTopology.ROUTING_KEY);
    }

    @Bean
    Binding statusBinding(Queue statusQueue, DirectExchange analysisExchange) {
        return BindingBuilder.bind(statusQueue).to(analysisExchange).with(RabbitTopology.STATUS_ROUTING_KEY);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setPrefetchCount(prefetch);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
