package io.aigoodle.runtime.agentscope;

import static org.junit.jupiter.api.Assertions.*;

import io.aigoodle.common.exception.PlatformException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AgentScopeCallBudgetTest {
    @Test
    void resumedBudgetKeepsPreviousModelAndToolCounts() {
        var original = new AgentScopeCallBudget(3, 2);
        original.modelCall();
        original.toolCall();
        var resumed = new AgentScopeCallBudget(3, 2, original.modelCount(), original.toolCount());
        resumed.modelCall();
        resumed.toolCall();
        assertEquals(2, resumed.modelCount());
        assertEquals(2, resumed.toolCount());
        assertThrows(PlatformException.class, resumed::toolCall);
        assertThrows(PlatformException.class, resumed::modelCall);
    }

    @Test
    void parallelToolCallsCannotExceedLimitAndModelStopsAfterToolBudgetFailure() {
        var budget = new AgentScopeCallBudget(5, 3);
        var accepted = new AtomicInteger();
        IntStream.range(0, 50).parallel().forEach(index -> {
            try {
                budget.toolCall();
                accepted.incrementAndGet();
            } catch (PlatformException ignored) {
            }
        });
        assertEquals(3, accepted.get());
        assertThrows(PlatformException.class, budget::modelCall);
    }

    @Test
    void zeroBudgetIsUnlimitedAndCountersAreIsolatedBetweenRuns() {
        var unlimited = new AgentScopeCallBudget(0, 0);
        IntStream.range(0, 100).forEach(index -> {
            unlimited.modelCall();
            unlimited.toolCall();
        });
        var first = new AgentScopeCallBudget(1, 1);
        first.modelCall();
        assertThrows(PlatformException.class, first::modelCall);
        var next = new AgentScopeCallBudget(1, 1);
        assertDoesNotThrow(() -> next.modelCall());
        assertDoesNotThrow(next::toolCall);
    }
}
