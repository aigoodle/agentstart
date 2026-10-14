package io.aigoodle.persistence;

import java.util.Set;

/** Table groups are public so embedded hosts can extend the guard without replacing it. */
public final class TenantGuardTables {
    public static final Set<String> MODEL = Set.of(
            "goodle_model", "goodle_provider_credential",
            "goodle_provider_model_setting", "goodle_tenant_default_model");
    public static final Set<String> AGENT = Set.of(
            "goodle_apps",
            "goodle_app_permissions",
            "goodle_agent_versions",
            "goodle_app_model_configs",
            "goodle_app_annotations",
            "goodle_app_annotation_settings",
            "goodle_conversations",
            "goodle_runs",
            "goodle_run_events",
            "goodle_api_tokens",
            "goodle_app_sites",
            "goodle_tags",
            "goodle_tag_bindings");
    public static final Set<String> WORKFLOW = Set.of("goodle_workflows", "goodle_workflow_runs");
    public static final Set<String> KNOWLEDGE = Set.of(
            "goodle_dataset",
            "goodle_documents",
            "goodle_document_segments",
            "goodle_document_ingest_queue",
            "goodle_dataset_query");
    public static final Set<String> SKILL = Set.of("goodle_skills");
    public static final Set<String> MEMORY = Set.of("goodle_memories");
    public static final Set<String> TRIGGER = Set.of("goodle_app_triggers", "goodle_trigger_invocations");
    public static final Set<String> CONNECTOR =
            Set.of("agent_connector_installation", "agent_connector_connection", "agent_connector_execution");
    public static final Set<String> CHANNEL = Set.of(
            "agent_channel_connection",
            "agent_channel_event",
            "agent_channel_identity",
            "agent_tenant_agent_binding",
            "agent_employee_agent_binding",
            "agent_channel_audit",
            "agent_channel_conversation");

    private TenantGuardTables() {}

    public static Set<String> defaults() {
        java.util.LinkedHashSet<String> tables = new java.util.LinkedHashSet<>(CONNECTOR);
        tables.addAll(CHANNEL);
        tables.addAll(MODEL);
        tables.addAll(AGENT);
        tables.addAll(WORKFLOW);
        tables.addAll(KNOWLEDGE);
        tables.addAll(MEMORY);
        tables.addAll(SKILL);
        tables.addAll(TRIGGER);
        return java.util.Collections.unmodifiableSet(tables);
    }
}
