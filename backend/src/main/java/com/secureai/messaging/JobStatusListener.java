package com.secureai.messaging;

import com.secureai.service.AnalysisJobService;
import com.secureai.websocket.JobStatusWebSocketBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "secureai.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class JobStatusListener {

    private final AnalysisJobService analysisJobService;
    private final JobStatusWebSocketBroadcaster broadcaster;

    @RabbitListener(queues = RabbitTopology.STATUS_QUEUE)
    public void onStatus(JobStatusMessage message) {
        log.info("Received status update jobId={} status={}", message.jobId(), message.status());
        analysisJobService.applyStatusUpdate(message);
        broadcaster.broadcast(message);
    }
}
