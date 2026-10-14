package io.aigoodle.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.model.entity.PromptTemplateEntity;
import io.aigoodle.model.mapper.PromptTemplateMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PromptTemplateServiceTest {

    @Test
    void createsATemplateFromOneCoherentDraft() {
        PromptTemplateMapper mapper = mock(PromptTemplateMapper.class);
        PromptTemplateService service = new PromptTemplateService(mapper);

        PromptTemplateEntity created = service.create(new PromptTemplateDraft(
                "", "Summarize", "summary", "Short summary", "Summarize {{#input#}}", List.of("writing", "summary")));

        assertThat(created.getTenantId()).isEqualTo("default");
        assertThat(created.getName()).isEqualTo("Summarize");
        assertThat(created.getContent()).isEqualTo("Summarize {{#input#}}");
        assertThat(created.getTagsJson()).isEqualTo("[\"writing\",\"summary\"]");
        verify(mapper).insert(created);
    }

    @Test
    void appliesOnlyFieldsPresentInAPatch() {
        PromptTemplateMapper mapper = mock(PromptTemplateMapper.class);
        PromptTemplateService service = new PromptTemplateService(mapper);
        PromptTemplateEntity existing = new PromptTemplateEntity();
        existing.setId("template-id");
        existing.setName("Original");
        existing.setCategory("original-category");
        existing.setDescription("Original description");
        when(mapper.selectById("template-id")).thenReturn(existing);

        PromptTemplateEntity updated =
                service.update("template-id", new PromptTemplatePatch("Updated", null, null, "New content", List.of()));

        assertThat(updated.getName()).isEqualTo("Updated");
        assertThat(updated.getCategory()).isEqualTo("original-category");
        assertThat(updated.getDescription()).isEqualTo("Original description");
        assertThat(updated.getContent()).isEqualTo("New content");
        assertThat(updated.getTagsJson()).isEqualTo("[]");
        verify(mapper).updateById(existing);
    }

    @Test
    void rendersAndDiscoversVariablesUsingTheSharedTemplateSyntax() {
        PromptTemplateService service = new PromptTemplateService(mock(PromptTemplateMapper.class));
        String template = "Hello {{# user.name #}}, {{#missing#}} / {{#user.name#}}";

        assertThat(service.variablesOf(template)).containsExactly("user.name", "missing");
        assertThat(service.render(template, Map.of("user.name", "$Alice\\Admin")))
                .isEqualTo("Hello $Alice\\Admin,  / $Alice\\Admin");
    }

    @Test
    void seedsTheCuratedCatalogForAnEmptyTenant() {
        PromptTemplateMapper mapper = mock(PromptTemplateMapper.class);
        PromptTemplateService service = new PromptTemplateService(mapper);
        ArgumentCaptor<PromptTemplateEntity> templates = ArgumentCaptor.forClass(PromptTemplateEntity.class);

        service.seedStartersIfEmpty("tenant-a");

        verify(mapper, org.mockito.Mockito.times(3)).insert(templates.capture());
        assertThat(templates.getAllValues())
                .extracting(PromptTemplateEntity::getName)
                .containsExactly("summarize-en", "classify-intent", "extract-json");
        assertThat(templates.getAllValues())
                .extracting(PromptTemplateEntity::getTenantId)
                .containsOnly("tenant-a");
    }

    @Test
    void tenantScopedMutationNeverTouchesForeignTemplate() {
        PromptTemplateMapper mapper = mock(PromptTemplateMapper.class);
        when(mapper.selectOne(any())).thenReturn(null);
        PromptTemplateService service = new PromptTemplateService(mapper);

        assertThatThrownBy(() -> service.update(
                        "tenant-a", "template-b", new PromptTemplatePatch("stolen", null, null, null, null)))
                .hasMessageContaining("Prompt template not found");
        assertThatThrownBy(() -> service.delete("tenant-a", "template-b"))
                .hasMessageContaining("Prompt template not found");

        verify(mapper, never()).update(any(), any());
        verify(mapper, never()).delete(any());
    }
}
