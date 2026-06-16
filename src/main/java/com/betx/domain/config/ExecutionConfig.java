package com.betx.domain.config;

/** Execution-level runtime controls for real betting operations. */
public record ExecutionConfig(ExecutionQueueConfig queue) {
    public ExecutionConfig {
        queue = queue == null ? ExecutionQueueConfig.defaults() : queue;
    }

    public static ExecutionConfig defaults() {
        return new ExecutionConfig(null);
    }
}
