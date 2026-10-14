package io.aigoodle.agent.service;

import io.aigoodle.knowledge.entity.DatasetEntity;

/** Immutable dataset summary returned by application attachment APIs. */
public record AttachedDatasetView(
        String id, String name, String description, String indexingTechnique, Integer documentCount) {

    static AttachedDatasetView from(DatasetEntity dataset) {
        return new AttachedDatasetView(
                dataset.getId(),
                dataset.getName(),
                dataset.getDescription(),
                dataset.getIndexingTechnique() == null
                        ? null
                        : dataset.getIndexingTechnique().name(),
                dataset.getDocumentCount());
    }
}
