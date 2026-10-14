package io.aigoodle.agent;

import static org.assertj.core.api.Assertions.*;

import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.mapper.AppMapper;
import io.aigoodle.agent.service.*;
import io.aigoodle.common.context.*;
import io.aigoodle.common.exception.PlatformException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = AgentTestApplication.class, properties = "spring-agent.tools.builtin=false")
@Import(AppPermissionIntegrationTest.Organization.class)
@Transactional
class AppPermissionIntegrationTest {
    @TestConfiguration
    static class Organization {
        @Bean
        DepartmentHierarchyProvider testDepartments() {
            return (tenant, dept) ->
                    "t1".equals(tenant) && "project".equals(dept) ? Set.of("engineering", "team") : Set.of();
        }
    }

    @Autowired
    AppMapper apps;

    @Autowired
    io.aigoodle.agent.mapper.AppPermissionMapper permissionRows;

    @Autowired
    AppPermissionService permissions;

    @Autowired
    AgentService agents;

    @Autowired
    DepartmentHierarchyProvider departments;

    private String id;

    @BeforeEach
    void setup() {
        UserContextHolder.set(manager("t1"));
        AppEntity app = new AppEntity();
        app.setTenantId("t1");
        app.setName("Permission test");
        app.setMode("workflow");
        app.setWorkflowId("workflow-test");
        app.setPublished(true);
        app.setDataAccessMode("ALL");
        apps.insert(app);
        id = app.getId();
    }

    @AfterEach
    void clear() {
        UserContextHolder.clear();
    }

    @Test
    void sharedDiscoveryDoesNotDisableTenantIsolation() {
        AppEntity shared = agents.require("t1", id);
        shared.setAppCode("shared-assistant");
        shared.setVisibility("GLOBAL");
        apps.update(
                shared,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AppEntity>()
                        .eq(AppEntity::getTenantId, "t1")
                        .eq(AppEntity::getId, id));
        assertThat(agents.requireVisibleByCode("shared-assistant", "t1", "t1").getId())
                .isEqualTo(id);
        assertThat(agents.requireForChat("t1", id).getTenantId()).isEqualTo("t1");
        var caller = manager("t2");
        UserContextHolder.set(caller);
        assertThat(agents.requireVisibleByCode("shared-assistant", "t2", "t1").getId())
                .isEqualTo(id);
        assertThat(agents.requireForChat("t2", id).getTenantId()).isEqualTo("t1");
        assertThat(UserContextHolder.get()).isSameAs(caller);
        assertThat(io.aigoodle.persistence.TenantSqlScope.isBypassed()).isFalse();
        assertThatThrownBy(() -> agents.require("t1", id)).hasRootCauseInstanceOf(SecurityException.class);
        UserContextHolder.set(manager("t1"));
        assertThat(agents.requireVisibleByCode("shared-assistant", "t1", "t1").getId())
                .isEqualTo(id);
        assertThat(agents.requireForChat("t1", id).getTenantId()).isEqualTo("t1");
        assertThat(io.aigoodle.persistence.TenantSqlScope.isBypassed()).isFalse();
        shared.setPublished(false);
        apps.update(
                shared,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AppEntity>()
                        .eq(AppEntity::getTenantId, "t1")
                        .eq(AppEntity::getId, id));
        UserContextHolder.set(caller);
        assertThatThrownBy(() -> agents.requireForChat("t2", id)).isInstanceOf(PlatformException.class);
    }

    @Test
    void privateAndRestrictedApplicationsAreNotSharedAcrossTenants() {
        UserContextHolder.set(manager("t2"));
        assertThatThrownBy(() -> agents.requireForChat("t2", id)).isInstanceOf(PlatformException.class);
        UserContextHolder.set(manager("t1"));
        AppEntity shared = agents.require("t1", id);
        shared.setAppCode("restricted-shared");
        shared.setVisibility("GLOBAL");
        shared.setDataAccessMode("RESTRICTED");
        apps.update(
                shared,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AppEntity>()
                        .eq(AppEntity::getTenantId, "t1")
                        .eq(AppEntity::getId, id));
        permissions.replace("t1", id, new AppPermissionSettings("RESTRICTED", List.of()));
        UserContextHolder.set(manager("t2"));
        assertThatThrownBy(() -> agents.requireVisibleByCode("restricted-shared", "t2", "t1"))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("无权访问");
    }

    @Test
    void descendantsInheritButParentsAndSiblingsDoNot() {
        grant("DEPARTMENT", "project", true);
        for (String dept : List.of("project", "engineering", "team")) {
            UserContextHolder.set(user(dept));
            assertThat(agents.require("t1", id).getId()).isEqualTo(id);
        }
        for (String dept : List.of("company", "sales")) {
            CurrentUser user = user(dept);
            user.setDepartmentIds(Set.of("project", "engineering", "team"));
            UserContextHolder.set(user);
            assertDenied();
        }
    }

    @Test
    void exactDepartmentDoesNotIncludeChildren() {
        grant("DEPARTMENT", "project", false);
        UserContextHolder.set(user("team"));
        assertDenied();
        UserContextHolder.set(user("project"));
        assertThat(agents.require("t1", id)).isNotNull();
    }

    @Test
    void userAndBusinessRoleIdsAreOrConditions() {
        permissions.replace(
                "t1",
                id,
                new AppPermissionSettings(
                        "RESTRICTED",
                        List.of(
                                new AppPermissionSettings.Grant("USER", "alice", false),
                                new AppPermissionSettings.Grant("ROLE", "role-123", false))));
        UserContextHolder.set(
                CurrentUser.builder().tenantId("t1").userId("alice").build());
        assertThat(agents.require("t1", id)).isNotNull();
        UserContextHolder.set(
                CurrentUser.builder().tenantId("t1").roleIds(Set.of("role-123")).build());
        assertThat(agents.require("t1", id)).isNotNull();
        UserContextHolder.set(
                CurrentUser.builder().tenantId("t1").roles(Set.of("role-123")).build());
        assertDenied();
    }

    @Test
    void restrictedEmptyDeniesAndExistingAllRemainsCompatible() {
        UserContextHolder.clear();
        assertThat(agents.require("t1", id)).isNotNull();
        UserContextHolder.set(manager("t1"));
        permissions.replace("t1", id, new AppPermissionSettings("RESTRICTED", List.of()));
        UserContextHolder.clear();
        assertDenied();
    }

    @Test
    void sameDepartmentOrManagerInDifferentTenantCannotReadOrConfigure() {
        grant("DEPARTMENT", "project", true);
        UserContextHolder.set(manager("t2"));
        assertThatThrownBy(() -> agents.require("t1", id)).hasRootCauseInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> permissions.get("t1", id)).isInstanceOf(PlatformException.class);
        UserContextHolder.set(
                CurrentUser.builder().tenantId("t2").departmentId("project").build());
        assertThatThrownBy(() -> agents.require("t1", id)).hasRootCauseInstanceOf(SecurityException.class);
    }

    @Test
    void listAndSelectorsFilterAndRevocationIsImmediate() {
        grant("DEPARTMENT", "project", true);
        UserContextHolder.set(user("team"));
        assertThat(agents.list("t1")).extracting(AppEntity::getId).contains(id);
        UserContextHolder.set(manager("t1"));
        permissions.replace("t1", id, new AppPermissionSettings("RESTRICTED", List.of()));
        UserContextHolder.set(user("team"));
        assertThat(agents.list("t1")).extracting(AppEntity::getId).doesNotContain(id);
        assertThat(agents.listPublishedWorkflowApps("t1"))
                .extracting(AppEntity::getId)
                .doesNotContain(id);
        assertDenied();
    }

    @Test
    void readerCannotChangeGrantsEditOrDeleteApplication() {
        grant("DEPARTMENT", "project", true);
        UserContextHolder.set(user("team"));
        assertThatThrownBy(() -> permissions.replace("t1", id, new AppPermissionSettings("ALL", List.of())))
                .isInstanceOf(PlatformException.class);
        assertThatThrownBy(() -> agents.update(
                        "t1", id, SaveAppRequest.builder().name("changed").build()))
                .isInstanceOf(PlatformException.class);
        assertThatThrownBy(() -> agents.delete("t1", id)).isInstanceOf(PlatformException.class);
    }

    @Test
    void invalidReplacementPreservesPriorGrants() {
        grant("DEPARTMENT", "project", true);
        var invalid = new AppPermissionSettings.Grant("ROLE", "r", true);
        assertThatThrownBy(
                        () -> permissions.replace("t1", id, new AppPermissionSettings("RESTRICTED", List.of(invalid))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(permissions.get("t1", id).grants()).hasSize(1);
    }

    @Test
    void staleCatalogUpdateCannotResetARecentlyRestrictedApplication() {
        AppEntity stale = agents.require("t1", id);
        grant("DEPARTMENT", "project", false);
        stale.setName("Concurrent basic-info edit");
        apps.update(
                stale,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AppEntity>()
                        .eq(AppEntity::getTenantId, "t1")
                        .eq(AppEntity::getId, id));
        assertThat(permissions.get("t1", id).mode()).isEqualTo("RESTRICTED");
        UserContextHolder.set(user("sales"));
        assertDenied();
    }

    @Test
    void apiKeyOnlyGrantsItsBoundApplication() {
        permissions.replace("t1", id, new AppPermissionSettings("RESTRICTED", List.of()));
        UserContextHolder.set(CurrentUser.builder()
                .tenantId("t1")
                .principalType(PrincipalType.APP_API_KEY)
                .appId(id)
                .build());
        assertThat(agents.require("t1", id)).isNotNull();
        UserContextHolder.set(CurrentUser.builder()
                .tenantId("t1")
                .principalType(PrincipalType.APP_API_KEY)
                .appId("other")
                .build());
        assertDenied();
    }

    @Test
    void hierarchyPopulatesGlobalScopeAndDeletionRemovesGrants() {
        CurrentUser user = user("project");
        departments.populateDepartmentIds(user);
        UserContextHolder.runAs(user, () -> assertThat(UserContextHolder.currentDepartmentIds())
                .containsExactlyInAnyOrder("project", "engineering", "team"));
        grant("DEPARTMENT", "project", true);
        agents.delete("t1", id);
        assertThat(apps.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AppEntity>()
                        .eq(AppEntity::getTenantId, "t1")
                        .eq(AppEntity::getId, id)))
                .isNull();
        assertThat(permissionRows.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                                io.aigoodle.agent.entity.AppPermissionEntity>()
                        .eq(io.aigoodle.agent.entity.AppPermissionEntity::getTenantId, "t1")
                        .eq(io.aigoodle.agent.entity.AppPermissionEntity::getAppId, id)))
                .isZero();
    }

    private void grant(String type, String subject, boolean descendants) {
        permissions.replace(
                "t1",
                id,
                new AppPermissionSettings(
                        "RESTRICTED", List.of(new AppPermissionSettings.Grant(type, subject, descendants))));
    }

    private void assertDenied() {
        assertThatThrownBy(() -> agents.require("t1", id))
                .isInstanceOf(PlatformException.class)
                .extracting(e -> ((PlatformException) e).getCode())
                .isEqualTo("forbidden");
    }

    private static CurrentUser manager(String tenant) {
        return CurrentUser.builder()
                .tenantId(tenant)
                .userId("admin")
                .roles(Set.of("ADMIN"))
                .build();
    }

    private static CurrentUser user(String dept) {
        return CurrentUser.builder()
                .tenantId("t1")
                .userId("member")
                .departmentId(dept)
                .build();
    }
}
