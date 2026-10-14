package io.aigoodle.agent.service;

import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.service.DatasetService;

/** Resolves a dataset and enforces that it belongs to the application's tenant. */
final class OwnedDatasetResolver {

    private final DatasetService datasetService;

    OwnedDatasetResolver(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    DatasetEntity requireOwned(String datasetId, String applicationTenantId) {
        return datasetService.require(applicationTenantId, datasetId);
    }
}
