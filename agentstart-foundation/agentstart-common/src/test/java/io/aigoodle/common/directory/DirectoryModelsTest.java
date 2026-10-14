package io.aigoodle.common.directory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

class DirectoryModelsTest {

    @Test
    void directoryCollectionsAreImmutableAndNullSafe() {
        UserInfo user = new UserInfo("u1", "t1", "alice", "Alice", null, null, null, null, "d1", null, null, "ACTIVE");
        assertThat(user.departmentIds()).isEmpty();
        assertThat(user.roleIds()).isEmpty();

        UserPage page = new UserPage(List.of(user), 1, 1, 20);
        assertThat(page.items()).containsExactly(user);
        assertThatThrownByUnsupportedOperation(() -> page.items().add(user));
    }

    @Test
    void queryEnforcesBoundedPagination() {
        assertThatIllegalArgumentException().isThrownBy(() -> new UserQuery(null, null, 0, 20));
        assertThatIllegalArgumentException().isThrownBy(() -> new UserQuery(null, null, 1, 201));
        assertThat(UserQuery.firstPage("ali")).isEqualTo(new UserQuery("ali", null, 1, 20));
    }

    @Test
    void departmentDirectoryAlsoSuppliesHierarchyIds() {
        DepartmentDirectoryProvider directory = new StubDepartmentDirectory();
        assertThat(directory.descendantDepartmentIds("t1", "root")).containsExactlyInAnyOrder("child", "grandchild");
    }

    private static void assertThatThrownByUnsupportedOperation(Runnable action) {
        org.assertj.core.api.Assertions.assertThatThrownBy(action::run)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static final class StubDepartmentDirectory implements DepartmentDirectoryProvider {
        @Override
        public java.util.Optional<DepartmentInfo> findDepartment(String tenantId, String departmentId) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Map<String, DepartmentInfo> findDepartments(
                String tenantId, java.util.Collection<String> departmentIds) {
            return java.util.Map.of();
        }

        @Override
        public List<DepartmentInfo> findChildren(String tenantId, String parentDepartmentId) {
            return List.of();
        }

        @Override
        public List<DepartmentInfo> findDescendants(String tenantId, String departmentId) {
            return List.of(department("root"), department("child"), department("grandchild"));
        }

        private DepartmentInfo department(String id) {
            return new DepartmentInfo(id, "t1", null, null, id, null, null, null, null, "ACTIVE", false);
        }
    }
}
