package com.agent4j.bilibili.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeUpdateJobService {

    private final KnowledgeSyncService knowledgeSyncService;
    private final LongTermMemoryService longTermMemoryService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "knowledge-update-job");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Map<String, Object>> jobs = new ConcurrentHashMap<>();
    private volatile String activeJobId = "";

    public KnowledgeUpdateJobService(
            KnowledgeSyncService knowledgeSyncService,
            LongTermMemoryService longTermMemoryService
    ) {
        this.knowledgeSyncService = knowledgeSyncService;
        this.longTermMemoryService = longTermMemoryService;
    }

    public synchronized Map<String, Object> startKnowledgeUpdate(int limit) {
        if (!activeJobId.isBlank()) {
            Map<String, Object> active = jobs.get(activeJobId);
            if (active != null && "running".equals(active.get("status"))) {
                return Map.of(
                        "job", new LinkedHashMap<>(active),
                        "already_running", true,
                        "error", ""
                );
            }
            activeJobId = "";
        }

        String jobId = UUID.randomUUID().toString();
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("id", jobId);
        job.put("job_id", jobId);
        job.put("status", "running");
        job.put("message", "Knowledge update started.");
        job.put("limit", limit);
        job.put("percent", 0);
        job.put("started_at", Instant.now().toString());
        jobs.put(jobId, job);
        activeJobId = jobId;

        executor.submit(() -> runJob(jobId, limit));
        return Map.of("job", new LinkedHashMap<>(job), "already_running", false, "error", "");
    }

    public Map<String, Object> getKnowledgeUpdateJob(String jobId) {
        Map<String, Object> job = jobs.get(jobId);
        return job == null ? null : new LinkedHashMap<>(job);
    }

    public Map<String, Object> getActiveKnowledgeUpdateJob() {
        if (activeJobId.isBlank()) {
            return null;
        }
        return getKnowledgeUpdateJob(activeJobId);
    }

    private void runJob(String jobId, int limit) {
        try {
            Map<String, Object> result = knowledgeSyncService.updateKnowledgeBase(limit, progress -> updateJob(jobId, progress));
            updateJob(jobId, Map.of(
                    "status", "completed",
                    "message", "Knowledge update completed.",
                    "percent", 100,
                    "ended_at", Instant.now().toString(),
                    "result", result,
                    "knowledge_status", knowledgeSyncService.buildKnowledgeBaseStatus(longTermMemoryService, null)
            ));
        } catch (Exception exception) {
            updateJob(jobId, Map.of(
                    "status", "failed",
                    "message", exception.getMessage(),
                    "ended_at", Instant.now().toString(),
                    "error", exception.getMessage()
            ));
        } finally {
            if (jobId.equals(activeJobId)) {
                activeJobId = "";
            }
        }
    }

    private void updateJob(String jobId, Map<String, Object> patch) {
        jobs.compute(jobId, (ignored, current) -> {
            Map<String, Object> target = current == null ? new LinkedHashMap<>() : new LinkedHashMap<>(current);
            target.put("id", jobId);
            target.put("job_id", jobId);
            target.putAll(patch);
            return target;
        });
    }
}
