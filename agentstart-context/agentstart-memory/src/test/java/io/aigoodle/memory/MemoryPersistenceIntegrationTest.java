package io.aigoodle.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = MemoryPersistenceIntegrationTest.TestApplication.class)
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:memorytenant;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.sql.init.mode=always",
            "spring.sql.init.schema-locations=classpath:db/memory-schema.sql"
        })
class MemoryPersistenceIntegrationTest {

    @Autowired
    private MemoryManager memoryManager;

    @AfterEach
    void clearIdentity() {
        UserContextHolder.clear();
    }

    @Test
    void jdbcMemoryRejectsForgedTenantAndKeepsRecallScoped() {
        UserContextHolder.set(
                CurrentUser.builder().tenantId("memory-a").userId("employee-1").build());
        memoryManager.remember(new MemoryWrite(
                "memory-a",
                "employee-1",
                "conversation-1",
                MemoryTier.SHORT_TERM,
                MemoryRole.USER,
                "tenant a secret",
                0.5,
                null));

        assertEquals(
                1,
                memoryManager
                        .recall(new MemoryQuery(
                                "memory-a", "employee-1", "conversation-1", null, Set.of(MemoryTier.SHORT_TERM), 10))
                        .size());
        assertTenantMismatch(() -> memoryManager.recall(
                new MemoryQuery("memory-b", "employee-1", "conversation-1", null, Set.of(MemoryTier.SHORT_TERM), 10)));
        assertTenantMismatch(() -> memoryManager.remember(new MemoryWrite(
                "memory-b",
                "employee-1",
                "conversation-1",
                MemoryTier.SHORT_TERM,
                MemoryRole.USER,
                "forged write",
                0.5,
                null)));
    }

    private static void assertTenantMismatch(org.junit.jupiter.api.function.Executable action) {
        Throwable failure = assertThrows(RuntimeException.class, action);
        Throwable root = failure;
        while (root.getCause() != null) root = root.getCause();
        assertInstanceOf(SecurityException.class, root);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {}
}
