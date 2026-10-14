package io.aigoodle.channel;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/** Single-JVM fixed-window fallback used when no shared quota module is installed. */
public final class InMemoryChannelOutboundRateLimiter implements ChannelOutboundRateLimiter {
    private final ConcurrentHashMap<Scope, Window> windows = new ConcurrentHashMap<>();

    @Override
    public Duration acquire(Scope scope, int maxPerSecond) {
        if (maxPerSecond <= 0) return Duration.ZERO;
        long now = System.currentTimeMillis();
        Window window = windows.computeIfAbsent(scope, ignored -> new Window(now / 1000, 0));
        synchronized (window) {
            long second = now / 1000;
            if (window.second != second) {
                window.second = second;
                window.count = 0;
            }
            if (window.count < maxPerSecond) {
                window.count++;
                return Duration.ZERO;
            }
            return Duration.ofMillis(Math.max(1, (second + 1) * 1000 - now));
        }
    }

    private static final class Window {
        private long second;
        private int count;

        private Window(long second, int count) {
            this.second = second;
            this.count = count;
        }
    }
}
