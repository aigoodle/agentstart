package io.aigoodle.web.controller;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.model.service.ProviderDefinitionSeeder;
import io.aigoodle.web.support.ChannelAdministrationPolicy;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Explicit administrator action; catalog maintenance never runs during boot. */
@RestController
@ConditionalOnBean(ProviderDefinitionSeeder.class)
public class ModelCatalogMaintenanceController {
    private final ProviderDefinitionSeeder seeder;
    private final ChannelAdministrationPolicy administration;

    public ModelCatalogMaintenanceController(
            ProviderDefinitionSeeder seeder, ChannelAdministrationPolicy administration) {
        this.seeder = seeder;
        this.administration = administration;
    }

    @PostMapping("/model-providers/catalog/sync")
    public ApiResponse<Map<String, Integer>> synchronize() {
        administration.requireAdministrator();
        return ApiResponse.ok(Map.of("insertedProviders", seeder.seed()));
    }
}
