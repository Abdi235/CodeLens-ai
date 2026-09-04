package com.secureai.service;

import com.secureai.dto.SystemMetricsResponse;
import com.secureai.model.AnalysisJobStatus;
import com.secureai.monitoring.HttpRequestMetrics;
import com.secureai.repository.AnalysisJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MonitoringService {

    private final HttpRequestMetrics httpRequestMetrics;
    private final AnalysisJobRepository analysisJobRepository;
    private final DataSource dataSource;
    private final AiServiceClient aiServiceClient;
    private final ObjectProvider<ConnectionFactory> rabbitConnectionFactory;

    @Value("${secureai.rabbitmq.enabled:true}")
    private boolean rabbitmqEnabled;

    public SystemMetricsResponse snapshot() {
        HttpRequestMetrics.Snapshot http = httpRequestMetrics.snapshot();
        Map<String, String> dependencies = new LinkedHashMap<>();
        dependencies.put("api", "UP");
        dependencies.put("database", databaseStatus());
        dependencies.put("rabbitmq", rabbitmqStatus());
        dependencies.put("ai", aiServiceClient.isHealthy() ? "UP" : "DOWN");

        Double avgJobMs = analysisJobRepository.averageProcessingDurationMs();
        SystemMetricsResponse.PipelineMetrics pipeline = new SystemMetricsResponse.PipelineMetrics(
                analysisJobRepository.countByStatus(AnalysisJobStatus.QUEUED),
                analysisJobRepository.countByStatus(AnalysisJobStatus.PROCESSING),
                analysisJobRepository.countByStatus(AnalysisJobStatus.COMPLETED),
                analysisJobRepository.countByStatus(AnalysisJobStatus.FAILED),
                avgJobMs == null ? null : Math.round(avgJobMs * 10.0) / 10.0
        );

        return new SystemMetricsResponse(
                http.uptimeSeconds(),
                http.requestCount(),
                http.errorCount(),
                http.errorRatePercent(),
                http.avgLatencyMs(),
                http.p95LatencyMs(),
                dependencies,
                pipeline
        );
    }

    private String databaseStatus() {
        try (java.sql.Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? "UP" : "DOWN";
        } catch (SQLException e) {
            return "DOWN";
        }
    }

    private String rabbitmqStatus() {
        if (!rabbitmqEnabled) {
            return "DISABLED";
        }
        ConnectionFactory factory = rabbitConnectionFactory.getIfAvailable();
        if (factory == null) {
            return "DISABLED";
        }
        try (Connection connection = factory.createConnection()) {
            return connection != null ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }
}
