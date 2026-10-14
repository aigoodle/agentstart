package io.aigoodle.trigger.cron;

import io.aigoodle.common.exception.PlatformException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import org.springframework.scheduling.support.CronExpression;

/** Parses the public trigger JSON and calculates its persisted scheduling cursor. */
public final class TriggerSchedule {

    public static final String ONCE = "ONCE";
    public static final String CRON = "CRON";
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String kind;
    private final String expression;
    private final ZoneId zoneId;
    private final LocalDateTime runAt;

    private TriggerSchedule(String kind, String expression, ZoneId zoneId, LocalDateTime runAt) {
        this.kind = kind;
        this.expression = expression;
        this.zoneId = zoneId;
        this.runAt = runAt;
    }

    public static TriggerSchedule from(Map<String, Object> config) {
        Map<String, Object> safe = config == null ? Map.of() : config;
        String kind = text(safe.get("scheduleType"));
        if (kind == null) kind = safe.containsKey("runAt") ? ONCE : CRON;
        kind = kind.toUpperCase(Locale.ROOT);
        if ("ONE".equals(kind)) kind = ONCE;
        ZoneId zone = parseZone(text(safe.get("timeZone")));
        if (ONCE.equals(kind)) {
            String runAt = text(safe.get("runAt"));
            if (runAt == null) throw invalid("runAt is required for a one-time trigger");
            return new TriggerSchedule(kind, null, zone, parseDateTime(runAt, zone));
        }
        if (!CRON.equals(kind)) throw invalid("scheduleType must be CRON or ONCE");
        String expression = text(safe.get("expression"));
        if (expression == null) throw invalid("expression is required for a cron trigger");
        try {
            CronExpression.parse(expression);
        } catch (IllegalArgumentException exception) {
            throw invalid("invalid cron expression: " + expression);
        }
        return new TriggerSchedule(kind, expression, zone, null);
    }

    public LocalDateTime firstFireAt(LocalDateTime now) {
        if (ONCE.equals(kind)) {
            if (!runAt.isAfter(now)) throw invalid("runAt must be in the future");
            return runAt;
        }
        return nextAfter(now);
    }

    public LocalDateTime nextAfter(LocalDateTime previous) {
        if (ONCE.equals(kind)) return null;
        ZonedDateTime configuredNow = previous.atZone(ZoneId.systemDefault()).withZoneSameInstant(zoneId);
        ZonedDateTime next = CronExpression.parse(expression).next(configuredNow);
        if (next == null) throw invalid("cron expression has no next execution time");
        return next.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    public boolean oneTime() {
        return ONCE.equals(kind);
    }

    private static LocalDateTime parseDateTime(String value, ZoneId zone) {
        try {
            return LocalDateTime.parse(value, DISPLAY_DATE_TIME)
                    .atZone(zone)
                    .withZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDateTime();
            } catch (DateTimeParseException ignoredAgain) {
                try {
                    return ZonedDateTime.parse(value)
                            .withZoneSameInstant(ZoneId.systemDefault())
                            .toLocalDateTime();
                } catch (DateTimeParseException ignoredThird) {
                    try {
                        return LocalDateTime.parse(value)
                                .atZone(zone)
                                .withZoneSameInstant(ZoneId.systemDefault())
                                .toLocalDateTime();
                    } catch (DateTimeParseException exception) {
                        throw invalid("runAt must use yyyy-MM-dd HH:mm:ss, for example 2026-08-18 20:00:00");
                    }
                }
            }
        }
    }

    private static ZoneId parseZone(String value) {
        try {
            return ZoneId.of(value == null ? ZoneId.systemDefault().getId() : value);
        } catch (RuntimeException exception) {
            throw invalid("invalid timeZone: " + value);
        }
    }

    private static String text(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        return String.valueOf(value).trim();
    }

    private static PlatformException invalid(String message) {
        return new PlatformException("invalid_trigger_schedule", message, null);
    }
}
