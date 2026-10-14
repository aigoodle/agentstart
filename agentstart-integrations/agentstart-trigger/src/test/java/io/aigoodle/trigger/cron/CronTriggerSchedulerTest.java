package io.aigoodle.trigger.cron;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.aigoodle.trigger.entity.TriggerEntity;
import io.aigoodle.trigger.service.TriggerService;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.TaskScheduler;

class CronTriggerSchedulerTest {

    @Test
    void pollsDatabaseClaimsInsteadOfRegisteringNodeLocalCronTasks() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        TriggerService triggerService = mock(TriggerService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<TriggerService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(triggerService);
        when(taskScheduler.scheduleWithFixedDelay(any(Runnable.class), any(Duration.class)))
                .thenReturn(mock(ScheduledFuture.class));
        TriggerEntity claimed = new TriggerEntity();
        when(triggerService.claimDueSchedules(anyString(), any(Duration.class), anyInt()))
                .thenReturn(List.of(claimed));

        CronTriggerScheduler scheduler = new CronTriggerScheduler(taskScheduler, provider);
        scheduler.afterSingletonsInstantiated();
        scheduler.poll();

        verify(triggerService).fireClaimedAsynchronously(claimed);
        assertThat(scheduler.instanceId()).isNotBlank();
    }
}
