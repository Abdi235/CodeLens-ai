package com.secureai.messaging;

public interface AnalysisJobPublisher {

    void publish(AnalysisJobMessage message);
}
