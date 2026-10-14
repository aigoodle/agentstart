package io.aigoodle.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.agent.api.AgentStrategyType;
import io.aigoodle.agent.entity.AppModelConfigEntity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AppModelConfigFactoryTest {

    @Test
    void mapsRuntimeSettingsUsingTheEditorsPreferredPrompt() {
        SaveAppRequest request = SaveAppRequest.builder()
                .instructions("Legacy instructions")
                .prePrompt("Explicit system prompt")
                .modelProvider("qwen")
                .modelName("qwen-plus")
                .strategy(AgentStrategyType.PLAN_EXECUTE)
                .toolNames(List.of("search"))
                .datasetIds(List.of("dataset-1"))
                .modelSettings(Map.of("temperature", 0.2))
                .maxIterations(8)
                .maxModelCalls(12)
                .maxToolCalls(20)
                .memoryWindow(30)
                .memoryEnabled(true)
                .build();

        AppModelConfigEntity configuration = AppModelConfigFactory.from(request);

        assertThat(configuration.getPrePrompt()).isEqualTo("Explicit system prompt");
        assertThat(configuration.getModelProvider()).isEqualTo("qwen");
        assertThat(configuration.getModelName()).isEqualTo("qwen-plus");
        assertThat(configuration.getStrategy()).isEqualTo("PLAN_EXECUTE");
        assertThat(configuration.getToolNamesJson()).isEqualTo("[\"search\"]");
        assertThat(configuration.getDatasetIdsJson()).isEqualTo("[\"dataset-1\"]");
        assertThat(configuration.getConfigs()).contains("\"temperature\":0.2");
        assertThat(configuration.getMaxIterations()).isEqualTo(8);
        assertThat(configuration.getMaxModelCalls()).isEqualTo(12);
        assertThat(configuration.getMaxToolCalls()).isEqualTo(20);
        assertThat(configuration.getMemoryWindow()).isEqualTo(30);
    }

    @Test
    void fallsBackToLegacyInstructionsAndLeavesNonPositiveLimitsUnspecified() {
        SaveAppRequest request = SaveAppRequest.builder()
                .instructions("Legacy instructions")
                .prePrompt(" ")
                .maxIterations(-1)
                .maxModelCalls(0)
                .maxToolCalls(-1)
                .memoryWindow(0)
                .build();

        AppModelConfigEntity configuration = AppModelConfigFactory.from(request);

        assertThat(configuration.getPrePrompt()).isEqualTo("Legacy instructions");
        assertThat(configuration.getMaxIterations()).isNull();
        assertThat(configuration.getMaxModelCalls()).isNull();
        assertThat(configuration.getMaxToolCalls()).isNull();
        assertThat(configuration.getMemoryWindow()).isNull();
    }

    @Test
    void keepsNullableEditorPayloadsAbsentInsteadOfSerializingJsonNull() {
        SaveAppRequest request = SaveAppRequest.builder().build();
        request.setModelSettings(null);
        request.setUserInputForm(null);
        request.setFileUpload(null);
        request.setRetrievalConfig(null);

        AppModelConfigEntity configuration = AppModelConfigFactory.from(request);

        assertThat(configuration.getConfigs()).isNull();
        assertThat(configuration.getUserInputFormJson()).isNull();
        assertThat(configuration.getFileUploadJson()).isNull();
        assertThat(configuration.getDatasetConfigsJson()).isNull();
    }

    @Test
    void returnsNoSidecarForANullRequest() {
        assertThat(AppModelConfigFactory.from(null)).isNull();
    }
}
