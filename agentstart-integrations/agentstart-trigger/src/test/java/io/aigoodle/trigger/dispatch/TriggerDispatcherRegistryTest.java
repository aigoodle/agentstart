package io.aigoodle.trigger.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aigoodle.common.exception.PlatformException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TriggerDispatcherRegistryTest {

    @Test
    void resolvesTargetTypesIndependentlyOfSystemLocale() {
        Locale originalLocale = Locale.getDefault();
        TriggerDispatcher pipelineDispatcher = dispatcher("PIPELINE");
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TriggerDispatcherRegistry registry = new TriggerDispatcherRegistry(List.of(pipelineDispatcher));

            assertThat(registry.get("pipeline")).isSameAs(pipelineDispatcher);
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void reportsResolvedDefaultTypeWhenDispatcherIsMissing() {
        TriggerDispatcherRegistry registry = new TriggerDispatcherRegistry(List.of());

        assertThatThrownBy(() -> registry.get(null))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("workflow");
    }

    private static TriggerDispatcher dispatcher(String targetType) {
        return new TriggerDispatcher() {
            @Override
            public String targetType() {
                return targetType;
            }

            @Override
            public DispatchResult dispatch(String targetId, Map<String, Object> inputs, String conversationId) {
                return DispatchResult.ok(conversationId, inputs);
            }
        };
    }
}
