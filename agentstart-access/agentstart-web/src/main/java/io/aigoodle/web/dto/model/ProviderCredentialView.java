package io.aigoodle.web.dto.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** Safe credential status view; encrypted credential values are never included. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProviderCredentialView {
    private String providerName;
    private boolean configured;
    private String credentialId;
    private String credentialName;
}
