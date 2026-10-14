package io.aigoodle.trigger.cron;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TriggerScheduleTest {

    @Test
    void calculatesDailyScheduleInConfiguredTimeZone() {
        TriggerSchedule schedule = TriggerSchedule.from(
                Map.of("scheduleType", "CRON", "expression", "0 0 8 * * *", "timeZone", "Asia/Shanghai"));
        assertThat(schedule.firstFireAt(LocalDateTime.of(2026, 8, 17, 7, 0)))
                .isEqualTo(LocalDateTime.of(2026, 8, 17, 8, 0));
    }

    @Test
    void oneTimeScheduleHasNoSecondOccurrence() {
        TriggerSchedule schedule = TriggerSchedule.from(Map.of("scheduleType", "ONCE", "runAt", "2026-08-18 20:00:00"));
        assertThat(schedule.oneTime()).isTrue();
        assertThat(schedule.nextAfter(LocalDateTime.now())).isNull();
    }

    @Test
    void acceptsDisplayDateTimeWithoutExplicitTimeZone() {
        TriggerSchedule schedule = TriggerSchedule.from(Map.of("scheduleType", "ONCE", "runAt", "2026-08-18 20:00:00"));
        assertThat(schedule.firstFireAt(LocalDateTime.of(2026, 8, 18, 19, 59, 59)))
                .isEqualTo(LocalDateTime.of(2026, 8, 18, 20, 0));
    }

    @Test
    void acceptsOneAliasProducedByScheduleExtractionPrompt() {
        TriggerSchedule schedule = TriggerSchedule.from(Map.of("scheduleType", "ONE", "runAt", "2099-08-18 20:00:00"));
        assertThat(schedule.oneTime()).isTrue();
    }

    @Test
    void rejectsPastOneTimeSchedule() {
        TriggerSchedule schedule =
                TriggerSchedule.from(Map.of("scheduleType", "ONCE", "runAt", "2020-01-01T00:00:00Z"));
        assertThatThrownBy(() -> schedule.firstFireAt(LocalDateTime.now())).hasMessageContaining("future");
    }
}
