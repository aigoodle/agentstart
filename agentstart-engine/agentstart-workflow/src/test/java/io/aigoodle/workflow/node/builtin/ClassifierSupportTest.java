package io.aigoodle.workflow.node.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.aigoodle.model.entity.PromptTemplateEntity;
import io.aigoodle.model.service.PromptTemplateService;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClassifierSupportTest {

    @Test
    void normalizesCategoriesAndIgnoresBlankDesignerRows() {
        ClassifierCategorySet categories = ClassifierCategorySet.from(List.of(
                Map.of("id", "sales", "name", "Sales enquiry"),
                Map.of("id", "support", "name", "Technical support"),
                Map.of("id", " ", "name", "Blank row")));

        assertThat(categories.menu()).isEqualTo("- sales: Sales enquiry\n- support: Technical support\n");
    }

    @Test
    void matchesByIdOrNameAndFallsBackToTheLastCategory() {
        ClassifierCategorySet categories = ClassifierCategorySet.from(List.of(
                Map.of("id", "sales", "name", "Sales enquiry"),
                Map.of("id", "support", "name", "Technical support"),
                Map.of("id", "other", "name", "Other")));

        assertThat(categories.match("support").id()).isEqualTo("support");
        assertThat(categories.match("The answer is Sales Enquiry").id()).isEqualTo("sales");
        assertThat(categories.match("unrecognized response").id()).isEqualTo("other");
    }

    @Test
    void doesNotMatchAShortIdInsideAnUnrelatedWord() {
        ClassifierCategorySet categories = ClassifierCategorySet.from(
                List.of(Map.of("id", "hr", "name", "Human resources"), Map.of("id", "other", "name", "Other")));

        assertThat(categories.match("There are three pending invoices").id()).isEqualTo("other");
    }

    @Test
    void usesAConfiguredReusablePromptWhenItExists() {
        PromptTemplateService templateService = mock(PromptTemplateService.class);
        PromptTemplateEntity template = new PromptTemplateEntity();
        template.setContent("Choose from:\n{{#categories#}}");
        when(templateService.get("default", "classifier-template")).thenReturn(template);
        when(templateService.render(template.getContent(), Map.of("categories", "- sales: Sales\n")))
                .thenReturn("Choose from:\n- sales: Sales\n");
        NodeDef node = NodeDef.of("classifier", NodeType.QUESTION_CLASSIFIER)
                .with("systemPromptTemplateId", "classifier-template");
        ClassifierCategorySet categories = ClassifierCategorySet.from(List.of(Map.of("id", "sales", "name", "Sales")));

        String prompt = new ClassifierPromptBuilder(templateService).build(node, categories);

        assertThat(prompt).isEqualTo("Choose from:\n- sales: Sales\n");
    }

    @Test
    void fallsBackToASelfContainedDefaultPrompt() {
        NodeDef node = NodeDef.of("classifier", NodeType.QUESTION_CLASSIFIER);
        ClassifierCategorySet categories = ClassifierCategorySet.from(List.of(Map.of("id", "other", "name", "Other")));

        String prompt = new ClassifierPromptBuilder(null).build(node, categories);

        assertThat(prompt).contains("Reply with ONLY the category id").endsWith("- other: Other\n");
    }
}
