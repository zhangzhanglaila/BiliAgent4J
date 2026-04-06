package com.agent4j.bilibili.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE (Server-Sent Events) 流式处理服务。
 * 支持异步任务进度推送和实时事件流。
 * 对标 Python 版本的 SSE streaming 实现。
 */
@Service
public class SseEmitterService {

    private static final long DEFAULT_TIMEOUT_MS = 30 * 60 * 1000L; // 30分钟
    private static final int EVENT_COMPLETE = 0;
    private static final int EVENT_ERROR = 1;
    private static final int EVENT_PROGRESS = 2;

    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sse-emitter");
        t.setDaemon(true);
        return t;
    });

    // 存储活跃的 SseEmitter
    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    // 存储每个 emitter 的回调
    private final Map<String, Consumer<SseEvent>> eventCallbacks = new ConcurrentHashMap<>();

    public SseEmitterService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 创建一个新的 SSE 发射器。
     *
     * @param jobId 任务 ID
     * @return SseEmitter 实例
     */
    public SseEmitter createEmitter(String jobId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MS);

        activeEmitters.put(jobId, emitter);

        emitter.onCompletion(() -> {
            activeEmitters.remove(jobId);
            eventCallbacks.remove(jobId);
        });

        emitter.onTimeout(() -> {
            activeEmitters.remove(jobId);
            eventCallbacks.remove(jobId);
        });

        emitter.onError(e -> {
            activeEmitters.remove(jobId);
            eventCallbacks.remove(jobId);
        });

        return emitter;
    }

    /**
     * 为指定 job 设置事件回调。
     */
    public void setEventCallback(String jobId, Consumer<SseEvent> callback) {
        eventCallbacks.put(jobId, callback);
    }

    /**
     * 发送进度事件。
     */
    public void sendProgress(String jobId, String eventType, Map<String, Object> data) {
        SseEmitter emitter = activeEmitters.get(jobId);
        if (emitter == null) {
            return;
        }

        try {
            SseEvent event = new SseEvent(eventType, "progress", data);
            String eventData = objectMapper.writeValueAsString(event);

            emitter.send(SseEmitter.event()
                    .name(eventType)
                    .data(eventData));
        } catch (IOException e) {
            activeEmitters.remove(jobId);
        }
    }

    /**
     * 发送完成事件。
     */
    public void sendComplete(String jobId, Map<String, Object> result) {
        SseEmitter emitter = activeEmitters.get(jobId);
        if (emitter == null) {
            return;
        }

        try {
            SseEvent event = new SseEvent("done", "complete", result);
            String eventData = objectMapper.writeValueAsString(event);

            emitter.send(SseEmitter.event()
                    .name("done")
                    .data(eventData));

            emitter.complete();
            activeEmitters.remove(jobId);
            eventCallbacks.remove(jobId);
        } catch (IOException e) {
            activeEmitters.remove(jobId);
        }
    }

    /**
     * 发送错误事件。
     */
    public void sendError(String jobId, String errorMessage) {
        SseEmitter emitter = activeEmitters.get(jobId);
        if (emitter == null) {
            return;
        }

        try {
            SseEvent event = new SseEvent("error", "error", Map.of("error", errorMessage));
            String eventData = objectMapper.writeValueAsString(event);

            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(eventData));

            emitter.completeWithError(new RuntimeException(errorMessage));
            activeEmitters.remove(jobId);
            eventCallbacks.remove(jobId);
        } catch (IOException e) {
            activeEmitters.remove(jobId);
        }
    }

    /**
     * 异步发送进度（不阻塞当前线程）。
     */
    public void sendProgressAsync(String jobId, String eventType, Map<String, Object> data) {
        executor.execute(() -> sendProgress(jobId, eventType, data));
    }

    /**
     * 异步发送完成。
     */
    public void sendCompleteAsync(String jobId, Map<String, Object> result) {
        executor.execute(() -> sendComplete(jobId, result));
    }

    /**
     * 异步发送错误。
     */
    public void sendErrorAsync(String jobId, String errorMessage) {
        executor.execute(() -> sendError(jobId, errorMessage));
    }

    /**
     * 获取活跃的 emitter 数量。
     */
    public int getActiveEmitterCount() {
        return activeEmitters.size();
    }

    /**
     * 检查指定 job 是否还有活跃的 emitter。
     */
    public boolean hasActiveEmitter(String jobId) {
        return activeEmitters.containsKey(jobId);
    }

    /**
     * 取消指定 job 的 emitter。
     */
    public void cancelEmitter(String jobId) {
        SseEmitter emitter = activeEmitters.remove(jobId);
        if (emitter != null) {
            emitter.complete();
        }
        eventCallbacks.remove(jobId);
    }

    /**
     * SSE 事件数据类。
     */
    public static class SseEvent {
        private String event;
        private String type;
        private Map<String, Object> data;
        private long timestamp;

        public SseEvent(String event, String type, Map<String, Object> data) {
            this.event = event;
            this.type = type;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        public String getEvent() {
            return event;
        }

        public void setEvent(String event) {
            this.event = event;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Map<String, Object> getData() {
            return data;
        }

        public void setData(Map<String, Object> data) {
            this.data = data;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }
}
