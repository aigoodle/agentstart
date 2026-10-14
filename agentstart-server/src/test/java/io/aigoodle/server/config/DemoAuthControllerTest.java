package io.aigoodle.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DemoAuthControllerTest {

    private final DemoAuthController controller = new DemoAuthController(
            "admin", "123456", "demo-user", "Demo Administrator", "default", "TENANT_ADMIN,CONNECTOR_ADMIN");

    @Test
    void acceptsConfiguredDemoCredentialsAndReturnsOnlyAFrontEndMarker() {
        var response = controller.login(new DemoAuthController.LoginRequest("admin", "123456"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().accessToken()).isEqualTo(DemoAuthController.DEMO_ACCESS_TOKEN);
    }

    @Test
    void rejectsOtherCredentials() {
        var response = controller.login(new DemoAuthController.LoginRequest("admin", "wrong"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("invalid_credentials");
    }

    @Test
    void exposesTheSameConfiguredDemoIdentityToTheFrontEnd() {
        var info = controller.userInfo().getData();

        assertThat(info.id()).isEqualTo("demo-user");
        assertThat(info.username()).isEqualTo("admin");
        assertThat(info.tenantId()).isEqualTo("default");
        assertThat(info.roles()).containsExactly("CONNECTOR_ADMIN", "TENANT_ADMIN");
    }
}
