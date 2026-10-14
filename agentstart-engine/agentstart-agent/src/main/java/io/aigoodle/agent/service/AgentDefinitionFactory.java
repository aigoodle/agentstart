package io.aigoodle.agent.service;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentStrategyType;
import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.entity.AppModelConfigEntity;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.common.util.JsonUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** Combines an application's catalog row and model sidecar into runtime views. */
final class AgentDefinitionFactory {

    private static final int DEFAULT_MAX_ITERATIONS = 6;
    private static final int DEFAULT_MEMORY_WINDOW = 20;

    private final AppModelConfigService modelConfigService;

    AgentDefinitionFactory(AppModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
    }

    AppEntity enrich(AppEntity agent) {
        if (agent == null || agent.getId() == null) {
            return agent;
        }
        AppModelConfigEntity modelConfig = modelConfigService.findByAppId(agent.getTenantId(), agent.getId());
        if (modelConfig == null) {
            return agent;
        }

        agent.setInstructions(modelConfig.getPrePrompt());
        agent.setOpeningStatement(modelConfig.getOpeningStatement());
        agent.setSuggestedQuestionsJson(modelConfig.getSuggestedQuestionsJson());
        agent.setDatasetIdsJson(modelConfig.getDatasetIdsJson());
        agent.setRetrievalConfigJson(modelConfig.getDatasetConfigsJson());
        agent.setModelSettingsJson(modelConfig.getConfigs());
        agent.setRuntimeType(firstText(modelConfig.getRuntimeType(), "NATIVE"));
        agent.setRuntimeRef(modelConfig.getRuntimeRef());
        agent.setStrategy(modelConfig.getStrategy());
        agent.setToolNamesJson(modelConfig.getToolNamesJson());
        agent.setSkillIdsJson(modelConfig.getSkillIdsJson());
        agent.setApprovalToolsJson(modelConfig.getApprovalToolsJson());
        agent.setDelegateAgentIdsJson(modelConfig.getDelegateAgentIdsJson());
        agent.setMaxIterations(modelConfig.getMaxIterations());
        agent.setMaxModelCalls(modelConfig.getMaxModelCalls());
        agent.setMaxToolCalls(modelConfig.getMaxToolCalls());
        agent.setMemoryEnabled(modelConfig.getMemoryEnabled());
        agent.setMemoryWindow(modelConfig.getMemoryWindow());
        agent.setModelName(firstText(modelConfig.getModelName(), agent.getModelName()));
        agent.setModelProvider(firstText(modelConfig.getModelProvider(), agent.getModelProvider()));
        return agent;
    }

    AgentDefinition create(AppEntity agent) {
        AppModelConfigEntity modelConfig = modelConfigService.findByAppId(agent.getTenantId(), agent.getId());
        return AgentDefinition.builder()
                .id(agent.getId())
                .tenantId(agent.getTenantId())
                .name(agent.getName())
                .runtimeType(firstText(configuredValue(modelConfig, AppModelConfigEntity::getRuntimeType), "NATIVE"))
                .runtimeRef(configuredValue(modelConfig, AppModelConfigEntity::getRuntimeRef))
                .instructions(configuredValue(modelConfig, AppModelConfigEntity::getPrePrompt))
                .modelName(firstText(
                        configuredValue(modelConfig, AppModelConfigEntity::getModelName), agent.getModelName()))
                .modelProvider(firstText(
                        configuredValue(modelConfig, AppModelConfigEntity::getModelProvider), agent.getModelProvider()))
                .strategy(resolveStrategy(modelConfig))
                .toolNames(parseStringList(configuredValue(modelConfig, AppModelConfigEntity::getToolNamesJson)))
                .skillIds(parseStringList(configuredValue(modelConfig, AppModelConfigEntity::getSkillIdsJson)))
                .approvalRequiredTools(new HashSet<>(
                        parseStringList(configuredValue(modelConfig, AppModelConfigEntity::getApprovalToolsJson))))
                .delegateAgentIds(
                        parseStringList(configuredValue(modelConfig, AppModelConfigEntity::getDelegateAgentIdsJson)))
                .maxIterations(valueOrDefault(
                        configuredValue(modelConfig, AppModelConfigEntity::getMaxIterations), DEFAULT_MAX_ITERATIONS))
                .maxModelCalls(valueOrDefault(configuredValue(modelConfig, AppModelConfigEntity::getMaxModelCalls), 0))
                .maxToolCalls(valueOrDefault(configuredValue(modelConfig, AppModelConfigEntity::getMaxToolCalls), 0))
                .memoryEnabled(
                        !Boolean.FALSE.equals(configuredValue(modelConfig, AppModelConfigEntity::getMemoryEnabled)))
                .memoryWindow(valueOrDefault(
                        configuredValue(modelConfig, AppModelConfigEntity::getMemoryWindow), DEFAULT_MEMORY_WINDOW))
                .modelSettings(parseSettings(configuredValue(modelConfig, AppModelConfigEntity::getConfigs)))
                .build();
    }

    private static AgentStrategyType resolveStrategy(AppModelConfigEntity modelConfig) {
        String configuredStrategy = configuredValue(modelConfig, AppModelConfigEntity::getStrategy);
        if (!hasText(configuredStrategy)) {
            return AgentStrategyType.REACT;
        }

        String enumName = configuredStrategy.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return AgentStrategyType.valueOf(enumName);
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(
                    "invalid_agent_strategy", "Unsupported agent strategy: " + configuredStrategy, exception);
        }
    }

    private static List<String> parseStringList(String json) {
        return hasText(json) ? JsonUtils.parseList(json, String.class) : List.of();
    }

    private static Map<String, Object> parseSettings(String json) {
        if (!hasText(json)) {
            return new HashMap<>();
        }
        Map<String, Object> settings = JsonUtils.parseMap(json);
        return settings == null ? new HashMap<>() : new HashMap<>(settings);
    }

    private static String firstText(String preferredValue, String fallbackValue) {
        return hasText(preferredValue) ? preferredValue : fallbackValue;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static <T> T configuredValue(
            AppModelConfigEntity modelConfig, Function<AppModelConfigEntity, T> valueExtractor) {
        return modelConfig == null ? null : valueExtractor.apply(modelConfig);
    }
}
