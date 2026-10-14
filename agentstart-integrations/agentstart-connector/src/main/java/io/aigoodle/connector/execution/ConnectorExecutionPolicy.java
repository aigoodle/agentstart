package io.aigoodle.connector.execution;

import io.aigoodle.connector.ConnectorActionDefinition;

@FunctionalInterface
public interface ConnectorExecutionPolicy {
    Decision evaluate(ConnectorActionDefinition action, ConnectorExecutionRequest request);

    record Decision(boolean allowed, String reason) {
        public static Decision allow() {
            return new Decision(true, null);
        }

        public static Decision deny(String reason) {
            return new Decision(false, reason);
        }
    }
}
