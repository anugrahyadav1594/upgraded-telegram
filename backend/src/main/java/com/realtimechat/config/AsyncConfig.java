package com.realtimechat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Executors for background work.
 *
 * <p>Notification fan-out runs on its own bounded pool for two reasons: a slow notification path
 * must never add latency to sending a message, and an unbounded queue would turn a downstream
 * outage into an out-of-memory failure. Overflow is rejected and logged loudly rather than
 * silently growing the queue.
 */
@Configuration
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";

    @Bean(name = NOTIFICATION_EXECUTOR)
    public TaskExecutor notificationExecutor(AppProperties properties) {
        AppProperties.Notifications config = properties.getNotifications();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(config.getAsyncCorePoolSize());
        executor.setMaxPoolSize(config.getAsyncMaxPoolSize());
        executor.setQueueCapacity(config.getAsyncQueueCapacity());
        executor.setThreadNamePrefix("notification-");
        executor.setRejectedExecutionHandler((task, pool) -> {
            // Dropping a notification is acceptable; failing a user action because notifications
            // are backed up is not. The counter/log line is the operational signal.
            log.warn("notification queue full — task rejected (notifications may be delayed)");
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
