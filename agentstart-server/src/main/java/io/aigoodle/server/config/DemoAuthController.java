package io.aigoodle.server.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.aigoodle.api.ApiResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal authentication facade for the downloadable demo application.
 *
 * <p>The returned token is deliberately only a front-end login-state marker. It is not parsed or
 * trusted by the server. {@link DemoCurrentUserWebFilter} restores the configured demo identity on
 * every request. Embedding applications disable demo mode and replace that filter with their own
 * JWT, session or SSO principal bridge.</p>
 */
@RestController
@ConditionalOnProperty(prefix = "spring-agent.demo", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DemoAuthController {

    static final String DEMO_ACCESS_TOKEN = "demo-session";
    private static final List<String> ACCESS_CODES =
            List.of("AGENT_ADMIN", "WORKFLOW_ADMIN", "KNOWLEDGE_ADMIN", "CONNECTOR_ADMIN");

    private final String loginUsername;
    private final String loginPassword;
    private final String userId;
    private final String displayName;
    private final String tenantId;
    private final List<String> roles;

    public DemoAuthController(
            @Value("${spring-agent.demo.login-username:admin}") String loginUsername,
            @Value("${spring-agent.demo.login-password:123456}") String loginPassword,
            @Value("${spring-agent.demo.user-id:demo-user}") String userId,
            @Value("${spring-agent.demo.username:Demo Administrator}") String displayName,
            @Value("${spring-agent.demo.tenant-id:default}") String tenantId,
            @Value("${spring-agent.demo.roles:TENANT_ADMIN,CONNECTOR_ADMIN}") String roles) {
        this.loginUsername = loginUsername;
        this.loginPassword = loginPassword;
        this.userId = userId;
        this.displayName = displayName;
        this.tenantId = tenantId;
        this.roles = DemoCurrentUserWebFilter.roles(roles).stream().sorted().toList();
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<LoginResult>> login(@RequestBody LoginRequest request) {
        if (request == null || !loginUsername.equals(request.username()) || !loginPassword.equals(request.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("invalid_credentials", "用户名或密码错误"));
        }
        return ResponseEntity.ok(ApiResponse.ok(new LoginResult(DEMO_ACCESS_TOKEN)));
    }

    @PostMapping("/auth/refresh")
    public ApiResponse<LoginResult> refresh() {
        return ApiResponse.ok(new LoginResult(DEMO_ACCESS_TOKEN));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok();
    }

    @GetMapping("/auth/codes")
    public ApiResponse<List<String>> accessCodes() {
        return ApiResponse.ok(ACCESS_CODES);
    }

    @GetMapping("/user/info")
    public ApiResponse<UserInfo> userInfo() {
        return ApiResponse.ok(new UserInfo(
                userId,
                loginUsername,
                displayName,
                roles,
                "/workspace",
                "Agent Start demo user (tenant: " + tenantId + ")",
                tenantId));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LoginRequest(String username, String password) {}

    public record LoginResult(String accessToken) {}

    public record UserInfo(
            String id,
            String username,
            String realName,
            List<String> roles,
            String homePath,
            String desc,
            String tenantId) {}
}
