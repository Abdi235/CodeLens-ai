package com.secureai.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobStatusWebSocketHandler extends TextWebSocketHandler {

    private final JobStatusWebSocketBroadcaster broadcaster;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String jobId = extractJobId(session);
        if (jobId == null) {
            closeQuietly(session, CloseStatus.BAD_DATA);
            return;
        }
        broadcaster.register(jobId, session);
        log.info("WebSocket connected jobId={} session={}", jobId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String jobId = extractJobId(session);
        if (jobId != null) {
            broadcaster.unregister(jobId, session);
            log.info("WebSocket closed jobId={} session={} status={}", jobId, session.getId(), status);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Client may send ping; respond with pong for keepalive
        if ("ping".equalsIgnoreCase(message.getPayload())) {
            try {
                session.sendMessage(new TextMessage("pong"));
            } catch (Exception e) {
                log.debug("Failed pong response: {}", e.getMessage());
            }
        }
    }

    private String extractJobId(WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : null;
        if (path == null) {
            return null;
        }
        String[] parts = path.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : null;
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception ignored) {
            // ignore
        }
    }
}
