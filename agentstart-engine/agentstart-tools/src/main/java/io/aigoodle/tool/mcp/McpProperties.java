package io.aigoodle.tool.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configures MCP servers whose tools should be exposed to agents
 * ({@code spring-agent.tools.mcp.servers[*]}).
 */
@ConfigurationProperties(prefix = "spring-agent.tools.mcp")
public class McpProperties {

    private List<Server> servers = new ArrayList<>();
    /** JSON file used by the admin API for durable runtime-managed servers. */
    private String configFile = "./data/mcp-servers.json";
    /** Separate secret for encrypting MCP child-process environment variables at rest. */
    private String encryptionSecret = "spring-agent-start-mcp-demo-secret-change-me";

    public List<Server> getServers() {
        return servers;
    }

    public void setServers(List<Server> servers) {
        this.servers = servers;
    }

    public String getConfigFile() {
        return configFile;
    }

    public void setConfigFile(String configFile) {
        this.configFile = configFile;
    }

    public String getEncryptionSecret() {
        return encryptionSecret;
    }

    public void setEncryptionSecret(String encryptionSecret) {
        this.encryptionSecret = encryptionSecret;
    }

    public static class Server {
        /** Stable opaque identifier used by administration APIs. */
        private String id;
        /** Logical name (used for logging / tool grouping). */
        private String name;
        /** {@code stdio}, Streamable {@code http}, or legacy HTTP {@code sse}. */
        private String type = "stdio";
        /** stdio: the executable, e.g. {@code npx} or {@code java}. */
        private String command;
        /** stdio: command arguments. */
        private List<String> args = new ArrayList<>();
        /** http: the MCP server base URL. */
        private String url;

        private int requestTimeoutSeconds = 30;
        /** stdio: environment variables supplied only to the child process. */
        private Map<String, String> env = new LinkedHashMap<>();

        private boolean enabled = true;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public List<String> getArgs() {
            return args;
        }

        public void setArgs(List<String> args) {
            this.args = args;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public int getRequestTimeoutSeconds() {
            return requestTimeoutSeconds;
        }

        public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
            this.requestTimeoutSeconds = requestTimeoutSeconds;
        }

        public Map<String, String> getEnv() {
            return env;
        }

        public void setEnv(Map<String, String> env) {
            this.env = env == null ? new LinkedHashMap<>() : env;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
