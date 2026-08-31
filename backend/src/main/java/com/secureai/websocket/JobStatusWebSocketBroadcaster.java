package com.secureai.websocket;

import tools.jackson.databind.json.JsonMapper;
import com.secureai.messaging.JobStatusMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobStatusWebSocketBroadcaster {

    private final JsonMapper jsonMapper;
    private final Map<String, Set<WebSocketSession>> sessionsByJob = new ConcurrentHashMap<>();

    public void register(String jobId, WebSocketSession session) {
        sessionsByJob.computeIfAbsent(jobId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.debug("WebSocket registered jobId={} session={}", jobId, session.getId());
    }

    public void unregister(String jobId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByJob.get(jobId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                sessionsByJob.remove(jobId);
            }
        }
    }

    public void broadcast(JobStatusMessage message) {
        Set<WebSocketSession> sessions = sessionsByJob.get(message.jobId());
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        try {
            String payload = jsonMapper.writeValueAsString(message);
            TextMessage text = new TextMessage(payload);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    session.sendMessage(text);
                }
            }
            log.debug("Broadcast status jobId={} to {} sessions", message.jobId(), sessions.size());
        } catch (Exception e) {
            log.warn("Failed to broadcast WebSocket update jobId={}: {}", message.jobId(), e.getMessage());
        }
    }
}
