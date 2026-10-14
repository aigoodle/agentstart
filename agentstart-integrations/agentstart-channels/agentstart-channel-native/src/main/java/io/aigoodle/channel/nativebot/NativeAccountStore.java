package io.aigoodle.channel.nativebot;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Optional persistence lookup used to restore native accounts lazily after an application restart.
 */
public interface NativeAccountStore {
    record Saved(String name, boolean enabled, Map<String, Object> configuration) {}

    record SavedAccount(String channelId, String accountId, Saved account) {}

    Optional<Saved> find(String channelId, String runtimeAccountId);

    /** Accounts which should establish a long-lived connection when the application becomes ready. */
    default List<SavedAccount> findEnabled() {
        return List.of();
    }
}
