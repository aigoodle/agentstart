package io.aigoodle.trigger;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * Test context wiring the model + workflow + trigger modules. Trigger targets are
 * template-only workflows, so no LLM is needed.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class TriggerTestApplication {}
