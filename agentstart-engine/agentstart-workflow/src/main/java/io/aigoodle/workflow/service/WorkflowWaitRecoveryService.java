package io.aigoodle.workflow.service;

import io.aigoodle.common.exception.PlatformException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/** Polls durable waits; work is coordinated through checkpoint CAS and run leases. */
@Slf4j
public class WorkflowWaitRecoveryService {
    private static final Duration NULL_LEASE_GRACE = Duration.ofMinutes(1);
    private static final ThreadPoolExecutor RECOVERY_EXECUTOR = new ThreadPoolExecutor(
            4,
            4,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(100),
            Thread.ofVirtual().name("workflow-recovery-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());
    private final WorkflowCheckpointStore store;
    private final PersistentWorkflowRunner runner;
    private final WorkflowRuntimeMetrics metrics;

    public WorkflowWaitRecoveryService(WorkflowCheckpointStore store, PersistentWorkflowRunner runner) {
        this(store, runner, WorkflowRuntimeMetrics.NOOP);
    }

    public WorkflowWaitRecoveryService(
            WorkflowCheckpointStore store, PersistentWorkflowRunner runner, WorkflowRuntimeMetrics metrics) {
        this.store = store;
        this.runner = runner;
        this.metrics = metrics == null ? WorkflowRuntimeMetrics.NOOP : metrics;
    }

    @Scheduled(fixedDelayString = "${goodle.workflow.wait-recovery-delay-ms:1000}")
    public void recoverDueWaits() {
        LocalDateTime now = LocalDateTime.now();
        store.staleRunning(now, now.minus(NULL_LEASE_GRACE), 100).forEach(checkpoint -> {
            try {
                RECOVERY_EXECUTOR.execute(() -> recoverRunning(checkpoint));
            } catch (java.util.concurrent.RejectedExecutionException saturated) {
                metrics.recovery("queue_saturated");
                log.error(
                        "Workflow recovery queue is saturated; run {} will be retried by the next scan",
                        checkpoint.getRunId());
            }
        });
        store.cancelling(100).forEach(checkpoint -> {
            boolean delivered = runner.cancelLocal(
                    checkpoint.getTenantId(), checkpoint.getRunId(), checkpoint.getInterruptReason());
            if (!delivered
                    && (checkpoint.getLeaseExpiresAt() == null
                            || checkpoint.getLeaseExpiresAt().isBefore(now))) {
                store.finalizeAbandonedCancellation(checkpoint);
            }
        });
        store.pausing(100).forEach(checkpoint -> {
            boolean delivered =
                    runner.pauseLocal(checkpoint.getTenantId(), checkpoint.getRunId(), checkpoint.getInterruptReason());
            if (!delivered
                    && (checkpoint.getLeaseExpiresAt() == null
                            || checkpoint.getLeaseExpiresAt().isBefore(now))) {
                store.finalizeAbandonedPause(checkpoint);
            }
        });
        store.expiredWaits(now, 100).forEach(checkpoint -> {
            try {
                runner.timeoutWait(checkpoint);
            } catch (RuntimeException exception) {
                log.warn("Could not timeout workflow wait {}: {}", checkpoint.getRunId(), exception.getMessage());
            }
        });
        store.dueSleeps(now, 100).forEach(checkpoint -> {
            try {
                runner.wakeSleep(checkpoint);
            } catch (PlatformException exception) {
                if (!"run_lease_conflict".equals(exception.getCode())) {
                    log.warn("Could not wake workflow run {}: {}", checkpoint.getRunId(), exception.getMessage());
                }
            } catch (RuntimeException exception) {
                log.warn("Could not wake workflow run {}: {}", checkpoint.getRunId(), exception.getMessage());
            }
        });
    }

    private void recoverRunning(io.aigoodle.workflow.entity.WorkflowCheckpointEntity checkpoint) {
        try {
            runner.resume(
                    checkpoint.getTenantId(),
                    checkpoint.getRunId(),
                    io.aigoodle.workflow.engine.WorkflowRunOptions.defaults());
            metrics.recovery("recovered");
            log.info("Recovered abandoned workflow run {}", checkpoint.getRunId());
        } catch (PlatformException exception) {
            if ("run_lease_conflict".equals(exception.getCode()) || "checkpoint_conflict".equals(exception.getCode()))
                return;
            if ("unsafe_resume".equals(exception.getCode())) {
                metrics.recovery("manual_reconciliation");
                store.failRecovery(
                        checkpoint,
                        "Automatic recovery refused; manual reconciliation required: " + exception.getMessage());
                log.error(
                        "Workflow run {} requires manual reconciliation: {}",
                        checkpoint.getRunId(),
                        exception.getMessage());
            } else {
                metrics.recovery("permanent_failure");
                store.failRecovery(
                        checkpoint,
                        "Automatic recovery failed permanently [" + exception.getCode() + "]: "
                                + exception.getMessage());
                log.error(
                        "Workflow run {} cannot be recovered [{}]: {}",
                        checkpoint.getRunId(),
                        exception.getCode(),
                        exception.getMessage());
            }
        } catch (RuntimeException exception) {
            metrics.recovery("failed");
            log.warn("Could not recover workflow run {}: {}", checkpoint.getRunId(), exception.getMessage());
        }
    }
}
