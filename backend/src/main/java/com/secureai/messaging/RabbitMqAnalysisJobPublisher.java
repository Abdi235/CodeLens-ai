package com.secureai.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "secureai.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMqAnalysisJobPublisher implements AnalysisJobPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publish(AnalysisJobMessage message) {
        log.info("Publishing analysis job jobId={} attempt={}", message.jobId(), message.attempt());
        rabbitTemplate.convertAndSend(
                RabbitTopology.EXCHANGE,
                RabbitTopology.ROUTING_KEY,
                message
        );
    }
}
