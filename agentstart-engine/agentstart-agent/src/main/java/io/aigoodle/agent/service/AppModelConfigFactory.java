package io.aigoodle.agent.service;

import io.aigoodle.agent.api.AgentStrategyType;
import io.aigoodle.agent.entity.AppModelConfigEntity;
import io.aigoodle.common.util.JsonUtils;

/** Translates an agent-editor request into its model-configuration sidecar. */
final class AppModelConfigFactory {

    private AppModelConfigFactory() {}

    static AppModelConfigEntity from(SaveAppRequest request) {
        if (request == null) {
            return null;
        }

        AppModelConfigEntity sidecar = new AppModelConfigEntity();
        applyModelConfiguration(request, sidecar);
        applyPromptConfiguration(request, sidecar);
        applyAgentConfiguration(request, sidecar);
        applyKnowledgeConfiguration(request, sidecar);
        return sidecar;
    }

    private static void applyModelConfiguration(SaveAppRequest request, AppModelConfigEntity sidecar) {
        sidecar.setModelProvider(request.getModelProvider());
        sidecar.setModelName(request.getModelName());
        sidecar.setRuntimeType(request.getRuntimeType());
        sidecar.setRuntimeRef(request.getRuntimeRef());
        sidecar.setConfigs(toJson(request.getModelSettings()));
    }

    private static void applyPromptConfiguration(SaveAppRequest request, AppModelConfigEntity sidecar) {
        sidecar.setPrePrompt(preferredSystemPrompt(request));
        sidecar.setPromptType(request.getPromptType());
        sidecar.setOpeningStatement(request.getOpeningStatement());
        sidecar.setSuggestedQuestionsJson(toJson(request.getSuggestedQuestions()));
        sidecar.setUserInputFormJson(toJson(request.getUserInputForm()));
        sidecar.setFileUploadJson(toJson(request.getFileUpload()));
    }

    private static void applyAgentConfiguration(SaveAppRequest request, AppModelConfigEntity sidecar) {
        sidecar.setStrategy(strategyName(request.getStrategy()));
        sidecar.setToolNamesJson(toJson(request.getToolNames()));
        sidecar.setSkillIdsJson(toJson(request.getSkillIds()));
        sidecar.setApprovalToolsJson(toJson(request.getApprovalRequiredTools()));
        sidecar.setDelegateAgentIdsJson(toJson(request.getDelegateAgentIds()));
        sidecar.setMaxIterations(positiveOrNull(request.getMaxIterations()));
        sidecar.setMaxModelCalls(positiveOrNull(request.getMaxModelCalls()));
        sidecar.setMaxToolCalls(positiveOrNull(request.getMaxToolCalls()));
        sidecar.setMemoryEnabled(request.isMemoryEnabled());
        sidecar.setMemoryWindow(positiveOrNull(request.getMemoryWindow()));
    }

    private static void applyKnowledgeConfiguration(SaveAppRequest request, AppModelConfigEntity sidecar) {
        sidecar.setDatasetIdsJson(toJson(request.getDatasetIds()));
        sidecar.setDatasetConfigsJson(toJson(request.getRetrievalConfig()));
    }

    private static String preferredSystemPrompt(SaveAppRequest request) {
        return hasText(request.getPrePrompt()) ? request.getPrePrompt() : request.getInstructions();
    }

    private static String strategyName(AgentStrategyType strategy) {
        return strategy == null ? null : strategy.name();
    }

    private static Integer positiveOrNull(int value) {
        return value > 0 ? value : null;
    }

    private static String toJson(Object value) {
        return JsonUtils.toJson(value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
