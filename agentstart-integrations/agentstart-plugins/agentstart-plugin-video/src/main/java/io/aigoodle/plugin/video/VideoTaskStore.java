package io.aigoodle.plugin.video;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.aigoodle.plugin.video.entity.VideoTaskEntity;
import io.aigoodle.plugin.video.mapper.VideoTaskMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable submission reservation plus leased polling/cancellation queue. */
public final class VideoTaskStore {
    public record Task(
            String id,
            String tenant,
            String owner,
            String requestHash,
            String endpointCipher,
            String vendorId,
            String status,
            String result,
            String error,
            boolean cancel,
            int attempts,
            Instant deadline) {
        @Override
        public String toString() {
            return "VideoTask[id=" + id + ", status=" + status + "]";
        }
    }

    private final VideoTaskMapper mapper;
    private final TransactionTemplate tx;

    public VideoTaskStore(VideoTaskMapper mapper, PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        tx = new TransactionTemplate(transactionManager);
        // Commit before vendor HTTP, and isolate a duplicate-key failure from the caller transaction.
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public boolean reserve(String id, String invocationKey, String tenant, String owner, String hash, String cipher) {
        var entity = new VideoTaskEntity();
        entity.setId(id);
        entity.setInvocationKey(invocationKey);
        entity.setTenantId(tenant);
        entity.setOwnerId(owner);
        entity.setRequestHash(hash);
        entity.setEndpointCipher(cipher);
        entity.setStatus("SUBMITTING");
        entity.setCancelRequested(0);
        entity.setAttempts(0);
        entity.setCreatedAt(java.time.LocalDateTime.now());
        entity.setUpdatedAt(entity.getCreatedAt());
        entity.setNextPollAt(Instant.now().plusSeconds(60));
        entity.setDeadlineAt(Instant.now().plusSeconds(3600));
        try {
            tx.executeWithoutResult(status -> mapper.insert(entity));
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    public Task get(String tenant, String id) {
        return tx.execute(status -> task(mapper.selectOne(new LambdaQueryWrapper<VideoTaskEntity>()
                .eq(VideoTaskEntity::getTenantId, tenant)
                .eq(VideoTaskEntity::getId, id))));
    }

    public Task byInvocation(String tenant, String key) {
        return tx.execute(status -> task(mapper.selectOne(new LambdaQueryWrapper<VideoTaskEntity>()
                .eq(VideoTaskEntity::getTenantId, tenant)
                .eq(VideoTaskEntity::getInvocationKey, key))));
    }

    public void accepted(String tenant, String id, String vendorId) {
        tx.executeWithoutResult(status -> {
            Instant now = Instant.now();
            int updated = mapper.update(
                    null,
                    new LambdaUpdateWrapper<VideoTaskEntity>()
                            .eq(VideoTaskEntity::getTenantId, tenant)
                            .eq(VideoTaskEntity::getId, id)
                            .eq(VideoTaskEntity::getStatus, "SUBMITTING")
                            .set(VideoTaskEntity::getVendorTaskId, vendorId)
                            .set(VideoTaskEntity::getStatus, "QUEUED")
                            .set(VideoTaskEntity::getNextPollAt, now)
                            .set(VideoTaskEntity::getUpdatedAt, LocalDateTime.now()));
            if (updated != 1) throw new IllegalStateException("Submission reservation changed");
        });
    }

    public void unknown(String tenant, String id) {
        tx.executeWithoutResult(status -> mapper.update(
                null,
                new LambdaUpdateWrapper<VideoTaskEntity>()
                        .eq(VideoTaskEntity::getTenantId, tenant)
                        .eq(VideoTaskEntity::getId, id)
                        .eq(VideoTaskEntity::getStatus, "SUBMITTING")
                        .set(VideoTaskEntity::getStatus, "UNKNOWN")
                        .set(VideoTaskEntity::getLastError, "submission_outcome_unknown")
                        .set(VideoTaskEntity::getUpdatedAt, LocalDateTime.now())));
    }

    public void requestCancel(String tenant, String id) {
        tx.executeWithoutResult(status -> mapper.update(
                null,
                new LambdaUpdateWrapper<VideoTaskEntity>()
                        .eq(VideoTaskEntity::getTenantId, tenant)
                        .eq(VideoTaskEntity::getId, id)
                        .in(VideoTaskEntity::getStatus, "QUEUED", "RUNNING")
                        .set(VideoTaskEntity::getCancelRequested, 1)
                        .set(VideoTaskEntity::getNextPollAt, Instant.now())
                        .set(VideoTaskEntity::getUpdatedAt, LocalDateTime.now())));
    }

    public List<Task> due(int limit) {
        return tx.execute(status -> {
            Instant now = Instant.now();
            mapper.update(
                    null,
                    new LambdaUpdateWrapper<VideoTaskEntity>()
                            .eq(VideoTaskEntity::getStatus, "SUBMITTING")
                            .lt(VideoTaskEntity::getNextPollAt, now)
                            .set(VideoTaskEntity::getStatus, "UNKNOWN")
                            .set(VideoTaskEntity::getLastError, "submission_outcome_unknown")
                            .set(VideoTaskEntity::getUpdatedAt, LocalDateTime.now()));
            var query = new LambdaQueryWrapper<VideoTaskEntity>()
                    .in(VideoTaskEntity::getStatus, "QUEUED", "RUNNING")
                    .le(VideoTaskEntity::getNextPollAt, now)
                    .and(lease ->
                            lease.isNull(VideoTaskEntity::getLeaseUntil).or().lt(VideoTaskEntity::getLeaseUntil, now))
                    .orderByAsc(VideoTaskEntity::getNextPollAt);
            var page = new Page<VideoTaskEntity>(1, Math.max(1, Math.min(limit, 100)), false);
            return mapper.selectPage(page, query).getRecords().stream()
                    .map(VideoTaskStore::task)
                    .toList();
        });
    }

    public String claim(Task task) {
        String token = UUID.randomUUID().toString();
        Instant now = Instant.now();
        return tx.execute(status -> mapper.update(
                                null,
                                new LambdaUpdateWrapper<VideoTaskEntity>()
                                        .eq(VideoTaskEntity::getTenantId, task.tenant())
                                        .eq(VideoTaskEntity::getId, task.id())
                                        .in(VideoTaskEntity::getStatus, "QUEUED", "RUNNING")
                                        .le(VideoTaskEntity::getNextPollAt, now)
                                        .and(lease -> lease.isNull(VideoTaskEntity::getLeaseUntil)
                                                .or()
                                                .lt(VideoTaskEntity::getLeaseUntil, now))
                                        .set(VideoTaskEntity::getLeaseToken, token)
                                        .set(VideoTaskEntity::getLeaseUntil, now.plusSeconds(150)))
                        == 1
                ? token
                : null);
    }

    public void finish(Task task, String token, String status, String result, String error, int delaySeconds) {
        tx.executeWithoutResult(transaction -> mapper.update(
                null,
                new LambdaUpdateWrapper<VideoTaskEntity>()
                        .eq(VideoTaskEntity::getTenantId, task.tenant())
                        .eq(VideoTaskEntity::getId, task.id())
                        .eq(VideoTaskEntity::getLeaseToken, token)
                        .set(VideoTaskEntity::getStatus, status)
                        .set(VideoTaskEntity::getResultJson, result)
                        .set(VideoTaskEntity::getLastError, error)
                        .setIncrBy(VideoTaskEntity::getAttempts, 1)
                        .set(VideoTaskEntity::getNextPollAt, Instant.now().plusSeconds(delaySeconds))
                        .set(VideoTaskEntity::getLeaseToken, null)
                        .set(VideoTaskEntity::getLeaseUntil, null)
                        .set(VideoTaskEntity::getUpdatedAt, LocalDateTime.now())));
    }

    private static Task task(VideoTaskEntity row) {
        return row == null
                ? null
                : new Task(
                        row.getId(),
                        row.getTenantId(),
                        row.getOwnerId(),
                        row.getRequestHash(),
                        row.getEndpointCipher(),
                        row.getVendorTaskId(),
                        row.getStatus(),
                        row.getResultJson(),
                        row.getLastError(),
                        Integer.valueOf(1).equals(row.getCancelRequested()),
                        row.getAttempts() == null ? 0 : row.getAttempts(),
                        row.getDeadlineAt());
    }
}
