package io.aigoodle.agent.service;

/**
 * Optional bridge used to validate dataset tag targets without making the Agent starter require
 * the Knowledge module at runtime.
 */
@FunctionalInterface
public interface DatasetOwnershipResolver {
    void requireOwned(String tenantId, String datasetId);
}
