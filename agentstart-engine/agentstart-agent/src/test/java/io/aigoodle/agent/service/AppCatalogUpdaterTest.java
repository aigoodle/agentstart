package io.aigoodle.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.agent.entity.AppEntity;
import org.junit.jupiter.api.Test;

class AppCatalogUpdaterTest {

    private final AppCatalogUpdater catalogUpdater = new AppCatalogUpdater();

    @Test
    void appliesReadableDefaultsToANewCatalogEntry() {
        AppEntity agent = new AppEntity();

        catalogUpdater.applyRequest(SaveAppRequest.builder().build(), agent);

        assertThat(agent.getMode()).isEqualTo("agent");
        assertThat(agent.getStatus()).isEqualTo("normal");
        assertThat(agent.getPublished()).isTrue();
    }

    @Test
    void blankModeAndStatusDoNotEraseExistingValues() {
        AppEntity agent = new AppEntity();
        agent.setMode("workflow");
        agent.setStatus("disabled");
        SaveAppRequest request = SaveAppRequest.builder()
                .mode(" ")
                .status("")
                .name("Updated name")
                .build();

        catalogUpdater.applyRequest(request, agent);

        assertThat(agent.getMode()).isEqualTo("workflow");
        assertThat(agent.getStatus()).isEqualTo("disabled");
        assertThat(agent.getName()).isEqualTo("Updated name");
    }

    @Test
    void omittedOptionalFieldsRemainUnchanged() {
        AppEntity agent = new AppEntity();
        agent.setDescription("Existing description");
        agent.setEnableApi(true);

        catalogUpdater.applyRequest(SaveAppRequest.builder().build(), agent);

        assertThat(agent.getDescription()).isEqualTo("Existing description");
        assertThat(agent.getEnableApi()).isTrue();
    }
}
