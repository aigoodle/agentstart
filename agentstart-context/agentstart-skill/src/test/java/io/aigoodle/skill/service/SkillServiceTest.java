package io.aigoodle.skill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.skill.entity.SkillEntity;
import io.aigoodle.skill.mapper.SkillMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillServiceTest {
    private final SkillMapper mapper = mock(SkillMapper.class);
    private final SkillService service = new SkillService(mapper);

    @org.junit.jupiter.api.BeforeAll
    static void initializeMybatisMetadata() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "skill-test"),
                SkillEntity.class);
    }

    @Test
    void resolvesPublishedSkillsInRequestedOrderAndCollectsTools() {
        SkillEntity first = skill("one", "核对销售额", "[\"query_sales\"]", "PUBLISHED");
        SkillEntity second = skill("two", "输出周报", "[\"render_report\"]", "PUBLISHED");
        when(mapper.selectList(any())).thenReturn(List.of(second, first));

        SkillResolution result = service.resolve("tenant-a", List.of("one", "two"));

        assertThat(result.prompt()).contains("已启用的企业技能", "核对销售额", "输出周报");
        assertThat(result.prompt().indexOf("核对销售额")).isLessThan(result.prompt().indexOf("输出周报"));
        assertThat(result.toolNames()).containsExactly("query_sales", "render_report");
    }

    @Test
    void refusesDraftOrMissingSkillAtRuntime() {
        when(mapper.selectList(any())).thenReturn(List.of(skill("one", "draft", "[]", "DRAFT")));
        assertThatThrownBy(() -> service.resolve("tenant-a", List.of("one")))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("not published");
    }

    private static SkillEntity skill(String id, String instructions, String tools, String status) {
        SkillEntity skill = new SkillEntity();
        skill.setId(id);
        skill.setName(id);
        skill.setDescription("desc");
        skill.setInstructions(instructions);
        skill.setToolNamesJson(tools);
        skill.setStatus(status);
        return skill;
    }
}
