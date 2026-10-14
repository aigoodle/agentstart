package io.aigoodle.server;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class AgentScopeProcessRecoveryTest {
    @TempDir
    Path directory;

    @Test
    void separateJavaProcessesResumeThePersistedSdkApproval() throws Exception {
        String database = directory.resolve("sdk-state").toString();
        new ResourceDatabasePopulator(new ClassPathResource("db/agentscope-schema.sql"))
                .execute(new DriverManagerDataSource("jdbc:h2:file:" + database, "sa", ""));
        assertTrue(run("pause", database).contains("PROCESS_PAUSED"));
        assertTrue(run("resume", database).contains("PROCESS_RECOVERED"));
    }

    private String run(String phase, String database) throws Exception {
        Path log = directory.resolve(phase + ".log");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var process = new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-cp",
                        classpath,
                        AgentScopeStateProcessProbe.class.getName(),
                        phase,
                        database)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Child process timed out: " + phase);
        }
        String output = java.nio.file.Files.readString(log);
        assertEquals(0, process.exitValue(), output);
        return output;
    }
}
