package com.secureai.messaging;

public final class RabbitTopology {
    public static final String EXCHANGE = "codelens.analysis.exchange";
    public static final String QUEUE = "codelens.analysis.queue";
    public static final String DLQ = "codelens.analysis.dlq";
    public static final String STATUS_QUEUE = "codelens.job.status.queue";
    public static final String ROUTING_KEY = "analysis";
    public static final String STATUS_ROUTING_KEY = "status";

    private RabbitTopology() {}
}
