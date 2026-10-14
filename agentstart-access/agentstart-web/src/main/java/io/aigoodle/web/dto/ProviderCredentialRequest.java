package io.aigoodle.web.dto;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;

/**
 * Payload for {@code PUT /model-providers/{name}/credential}. Absent / blank fields
 * are treated as "keep existing" (see {@code ProviderCredentialService.upsertPrimary}),
 * so the UI can rotate just the api key without re-typing base url.
 */
@Data
public class ProviderCredentialRequest {

    /** Field name -> raw value; matches the provider's credential schema. */
    private Map<String, Object> credentials = new HashMap<>();
}
