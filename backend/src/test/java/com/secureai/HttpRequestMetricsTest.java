package com.secureai;

import com.secureai.monitoring.HttpRequestMetrics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpRequestMetricsTest {

    @Test
    void averageAndP95UseSortedLatencySamples() {
        List<Long> samples = List.of(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L);
        assertEquals(55.0, HttpRequestMetrics.averageMs(samples));
        assertEquals(100.0, HttpRequestMetrics.percentileMs(samples, 0.95));
    }

    @Test
    void emptySamplesReturnZero() {
        assertEquals(0.0, HttpRequestMetrics.averageMs(List.of()));
        assertEquals(0.0, HttpRequestMetrics.percentileMs(List.of(), 0.95));
    }

    @Test
    void snapshotTracksRequestsErrorsAndErrorRate() {
        HttpRequestMetrics metrics = new HttpRequestMetrics();
        metrics.record(12, 200);
        metrics.record(40, 500);
        metrics.record(18, 200);

        HttpRequestMetrics.Snapshot snap = metrics.snapshot();
        assertEquals(3, snap.requestCount());
        assertEquals(1, snap.errorCount());
        assertEquals(33.3, snap.errorRatePercent());
        assertEquals(23.3, snap.avgLatencyMs());
        assertEquals(40.0, snap.p95LatencyMs());
    }
}
