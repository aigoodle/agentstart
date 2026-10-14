package io.aigoodle.model.service;

import java.util.Map;

/** Complete values required to register a named provider credential. */
public record ProviderCredentialRegistration(
        String tenantId, String providerName, String credentialName, Map<String, Object> credentials) {}
