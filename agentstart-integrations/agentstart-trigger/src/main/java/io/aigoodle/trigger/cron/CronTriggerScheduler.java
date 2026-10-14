package io.aigoodle.trigger.cron;

import io.aigoodle.trigger.entity.TriggerEntity;
import io.aigoodle.trigger.service.TriggerChangeListener;
import io.aigoodle.trigger.service.TriggerService;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.scheduling.TaskScheduler;

/** Database-backed, cluster-safe scheduler based on atomic row leases. */
public class CronTriggerScheduler implements TriggerChangeListener, SmartInitializingSingleton {

    private static final Logger logger = LoggerFactory.getLogger(CronTriggerScheduler.class);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(1);
    private static final Duration LEASE_DURATION = Duration.ofMinutes(5);

    private final TaskScheduler taskScheduler;
    private final ObjectProvider<TriggerService> triggerServiceProvider;
    private final String instanceId = UUID.randomUUID().toString();
    private volatile ScheduledFuture<?> pollingTask;

    public CronTriggerScheduler(TaskScheduler taskScheduler, ObjectProvider<TriggerService> triggerServiceProvider) {
        this.taskScheduler = taskScheduler;
        this.triggerServiceProvider = triggerServiceProvider;
    }

    @Override
    public void afterSingletonsInstantiated() {
        pollingTask = taskScheduler.scheduleWithFixedDelay(this::poll, POLL_INTERVAL);
    }

    void poll() {
        try {
            TriggerService service = triggerServiceProvider.getObject();
            List<TriggerEntity> claimed = service.claimDueSchedules(instanceId, LEASE_DURATION, 50);
            claimed.forEach(service::fireClaimedAsynchronously);
        } catch (RuntimeException exception) {
            logger.error("Trigger schedule polling failed: {}", exception.getMessage(), exception);
        }
    }

    @Override
    public void onSaved(TriggerEntity trigger) {
        // TriggerService persists the scheduling cursor; polling sees it on every node.
    }

    @Override
    public void onRemoved(String triggerId) {
        // There are no node-local per-trigger tasks to cancel.
    }

    public boolean isScheduled(String triggerId) {
        TriggerEntity trigger = triggerServiceProvider.getObject().require(triggerId);
        return Boolean.TRUE.equals(trigger.getEnabled()) && trigger.getNextFireAt() != null;
    }

    public String instanceId() {
        return instanceId;
    }

    public void stop() {
        ScheduledFuture<?> task = pollingTask;
        if (task != null) task.cancel(false);
    }
}
