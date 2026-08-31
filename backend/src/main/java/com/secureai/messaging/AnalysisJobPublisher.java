package com.secureai.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisJobPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(AnalysisJobMessage message) {
        log.info("Publishing analysis job jobId={} attempt={}", message.jobId(), message.attempt());
        rabbitTemplate.convertAndSend(
                RabbitTopology.EXCHANGE,
                RabbitTopology.ROUTING_KEY,
                message
        );
    }
}
