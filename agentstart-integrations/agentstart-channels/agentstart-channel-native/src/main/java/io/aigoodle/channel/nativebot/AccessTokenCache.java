package io.aigoodle.channel.nativebot;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Small per-JVM token cache; refreshes early and never persists credentials. */
public final class AccessTokenCache {
    private record Entry(String token, Instant expiresAt) {}

    private final ConcurrentHashMap<String, Entry> values = new ConcurrentHashMap<>();

    public String get(String key, Duration ttl, Supplier<String> loader) {
        Entry current = values.get(key);
        Instant now = Instant.now();
        if (current != null && current.expiresAt().isAfter(now.plusSeconds(60))) return current.token();
        synchronized (values) {
            current = values.get(key);
            now = Instant.now();
            if (current != null && current.expiresAt().isAfter(now.plusSeconds(60))) return current.token();
            String token = loader.get();
            values.put(key, new Entry(token, now.plus(ttl)));
            return token;
        }
    }
}
