package io.aigoodle.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.agent.entity.AppModelConfigEntity;
import org.junit.jupiter.api.Test;

class AppModelConfigPatchTest {

    @Test
    void preservesOmittedFieldsAndAllowsExplicitClearing() {
        AppModelConfigEntity existing = new AppModelConfigEntity();
        existing.setModelProvider("qwen");
        existing.setPrePrompt("Existing prompt");
        existing.setMemoryWindow(20);
        existing.setMaxModelCalls(5);
        AppModelConfigEntity patch = new AppModelConfigEntity();
        patch.setPrePrompt("");
        patch.setMemoryWindow(40);
        patch.setMaxToolCalls(12);

        AppModelConfigPatch.apply(existing, patch);

        assertThat(existing.getModelProvider()).isEqualTo("qwen");
        assertThat(existing.getPrePrompt()).isEmpty();
        assertThat(existing.getMemoryWindow()).isEqualTo(40);
        assertThat(existing.getMaxModelCalls()).isEqualTo(5);
        assertThat(existing.getMaxToolCalls()).isEqualTo(12);
    }
}
