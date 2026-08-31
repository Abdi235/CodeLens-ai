package com.secureai.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "secureai.rabbitmq.enabled", havingValue = "false")
public class NoOpAnalysisJobPublisher implements AnalysisJobPublisher {

    @Override
    public void publish(AnalysisJobMessage message) {
        log.warn("RabbitMQ disabled — jobId={} saved as QUEUED but not enqueued", message.jobId());
    }
}
