package com.secureai.monitoring;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-process HTTP request metrics for the service-health dashboard.
 * Tracks counts and a bounded latency sample window for avg / p95.
 */
@Component
public class HttpRequestMetrics {

    private static final int MAX_SAMPLES = 1000;

    private final Instant startedAt = Instant.now();
    private final AtomicLong requestCount = new AtomicLong();
    private final AtomicLong serverErrorCount = new AtomicLong();
    private final ConcurrentLinkedDeque<Long> latencySamplesMs = new ConcurrentLinkedDeque<>();

    public void record(long latencyMs, int statusCode) {
        requestCount.incrementAndGet();
        if (statusCode >= 500) {
            serverErrorCount.incrementAndGet();
        }
        latencySamplesMs.addLast(Math.max(0, latencyMs));
        while (latencySamplesMs.size() > MAX_SAMPLES) {
            latencySamplesMs.pollFirst();
        }
    }

    public Snapshot snapshot() {
        List<Long> samples = new ArrayList<>(latencySamplesMs);
        Collections.sort(samples);
        long requests = requestCount.get();
        long errors = serverErrorCount.get();
        double errorRate = requests == 0 ? 0.0 : (errors * 100.0) / requests;
        return new Snapshot(
                Duration.between(startedAt, Instant.now()).getSeconds(),
                requests,
                errors,
                round1(errorRate),
                averageMs(samples),
                percentileMs(samples, 0.95)
        );
    }

    public static double averageMs(List<Long> sortedSamples) {
        if (sortedSamples.isEmpty()) {
            return 0.0;
        }
        long sum = 0;
        for (Long sample : sortedSamples) {
            sum += sample;
        }
        return round1(sum / (double) sortedSamples.size());
    }

    public static double percentileMs(List<Long> sortedSamples, double percentile) {
        if (sortedSamples.isEmpty()) {
            return 0.0;
        }
        int index = (int) Math.ceil(percentile * sortedSamples.size()) - 1;
        index = Math.max(0, Math.min(sortedSamples.size() - 1, index));
        return sortedSamples.get(index).doubleValue();
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public record Snapshot(
            long uptimeSeconds,
            long requestCount,
            long errorCount,
            double errorRatePercent,
            double avgLatencyMs,
            double p95LatencyMs
    ) {}
}
