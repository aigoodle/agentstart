package io.aigoodle.skill.service;

import static org.junit.jupiter.api.Assertions.*;

import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = SkillPersistenceIntegrationTest.TestApplication.class,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:skillmigration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.sql.init.mode=always",
            "spring.sql.init.schema-locations=classpath:db/skill-schema.sql"
        })
class SkillPersistenceIntegrationTest {
    @Autowired
    private SkillService service;

    @Test
    void lifecycleAndTenantIsolationWorkWithRealPersistence() {
        var principal =
                CurrentUser.builder().tenantId("skill-a").userId("publisher").build();
        UserContextHolder.runAs(principal, () -> {
            var request = new SaveSkillRequest();
            request.setCode("sales-report");
            request.setName("Sales report");
            request.setInstructions("Summarize sales");
            request.setToolNames(List.of("query_sales"));
            var created = service.create("skill-a", request);
            assertEquals("DRAFT", created.getStatus());
            assertThrows(RuntimeException.class, () -> service.resolve("skill-a", List.of(created.getId())));
            service.setStatus("skill-a", created.getId(), "PUBLISHED");
            assertEquals(
                    List.of("query_sales"),
                    service.resolve("skill-a", List.of(created.getId())).toolNames());
            request.setName("Updated report");
            service.update("skill-a", created.getId(), request);
            assertEquals(
                    "Updated report",
                    service.require("skill-a", created.getId()).getName());
            assertThrows(SecurityException.class, () -> service.list("skill-b", null));
            assertThrows(SecurityException.class, () -> service.create("skill-b", request));
            assertThrows(SecurityException.class, () -> service.delete("skill-b", created.getId()));
            service.delete("skill-a", created.getId());
            assertTrue(service.list("skill-a", null).isEmpty());
        });
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {}
}
