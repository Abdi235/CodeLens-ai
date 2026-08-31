package com.secureai.messaging;

public final class RabbitTopology {
    public static final String EXCHANGE = "secureai.analysis.exchange";
    public static final String QUEUE = "secureai.analysis.queue";
    public static final String DLQ = "secureai.analysis.dlq";
    public static final String STATUS_QUEUE = "secureai.job.status.queue";
    public static final String ROUTING_KEY = "analysis";
    public static final String STATUS_ROUTING_KEY = "status";

    private RabbitTopology() {}
}
