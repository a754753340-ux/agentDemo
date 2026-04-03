package io.agentscope.examples.monolithchat.service;

import org.springframework.http.codec.ServerSentEvent;

public final class SseEventUtil {

    private SseEventUtil() {
    }

    public static ServerSentEvent<Object> build(String eventType, Object data) {
        return ServerSentEvent.builder(data).event(eventType).build();
    }
}
