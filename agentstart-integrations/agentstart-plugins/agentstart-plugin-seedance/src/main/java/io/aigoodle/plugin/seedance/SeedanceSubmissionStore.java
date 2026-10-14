package io.aigoodle.plugin.seedance;

import io.aigoodle.plugin.seedance.entity.SeedanceSubmissionEntity;
import io.aigoodle.plugin.seedance.mapper.SeedanceSubmissionMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Each reservation commits before HTTP. Uncertain submissions are never automatically resubmitted. */
public final class SeedanceSubmissionStore {
    public record Submission(String requestHash, String taskId) {}

    private final SeedanceSubmissionMapper mapper;
    private final TransactionTemplate transactions;

    public SeedanceSubmissionStore(SeedanceSubmissionMapper mapper, PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        transactions = new TransactionTemplate(transactionManager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public boolean reserve(String key, String requestHash) {
        var entity = new SeedanceSubmissionEntity();
        entity.setInvocationKey(key);
        entity.setRequestHash(requestHash);
        entity.setCreatedAt(java.time.LocalDateTime.now());
        try {
            transactions.executeWithoutResult(status -> mapper.insert(entity));
            return true;
        } catch (DuplicateKeyException exists) {
            return false;
        }
    }

    public Submission get(String key) {
        return transactions.execute(status -> {
            var entity = mapper.selectById(key);
            return entity == null ? null : new Submission(entity.getRequestHash(), entity.getTaskId());
        });
    }

    public void accepted(String key, String taskId) {
        transactions.executeWithoutResult(status -> {
            if (mapper.accept(key, taskId) != 1)
                throw new IllegalStateException("Seedance submission reservation changed");
        });
    }
}
