package io.aigoodle.persistence;

import io.aigoodle.common.context.UserContextHolder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * Validates tenant-owned SQL without rewriting it. Authenticated calls must both contain a
 * tenant predicate and bind the tenant supplied by the host-owned {@link UserContextHolder}.
 */
@Intercepts({
    @Signature(
            type = Executor.class,
            method = "queryCursor",
            args = {MappedStatement.class, Object.class, RowBounds.class}),
    @Signature(
            type = Executor.class,
            method = "update",
            args = {MappedStatement.class, Object.class}),
    @Signature(
            type = Executor.class,
            method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(
            type = Executor.class,
            method = "query",
            args = {
                MappedStatement.class,
                Object.class,
                RowBounds.class,
                ResultHandler.class,
                CacheKey.class,
                BoundSql.class
            })
})
public final class TenantSqlGuardInterceptor implements Interceptor {
    private static final Pattern TENANT_EQUALS_PARAMETER =
            Pattern.compile("(?:\\btenant_id\\b\\s*=\\s*\\?|\\?\\s*=\\s*\\btenant_id\\b)");

    private final Set<String> guardedTables;

    public TenantSqlGuardInterceptor(Set<String> guardedTables) {
        this.guardedTables = guardedTables == null
                ? Set.of()
                : guardedTables.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(value -> value.trim().toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!TenantSqlScope.isBypassed()) {
            Object[] args = invocation.getArgs();
            MappedStatement statement = (MappedStatement) args[0];
            BoundSql boundSql = args.length == 6 ? (BoundSql) args[5] : statement.getBoundSql(args[1]);
            requireTenantConstraint(boundSql.getSql(), guardedTables);
            if (UserContextHolder.isAuthenticated() && guards(boundSql.getSql(), guardedTables)) {
                requireCurrentTenantValues(statement, boundSql, args[1]);
            }
        }
        return invocation.proceed();
    }

    public static void requireTenantConstraint(String sql, Set<String> guardedTables) {
        if (sql == null || !guards(sql, guardedTables)) return;
        String normalized = normalize(sql);
        if (normalized.startsWith("insert ")) {
            int values = normalized.indexOf(" values");
            String columns = values < 0 ? normalized : normalized.substring(0, values);
            if (columns.contains("tenant_id")) return;
        } else {
            int where = normalized.lastIndexOf(" where ");
            if (where >= 0 && normalized.substring(where).contains("tenant_id")) return;
        }
        throw new SecurityException("tenant_id predicate is required for protected persistence operation");
    }

    public static void requireTenantValues(String sql, String expectedTenant, List<?> parameterValues) {
        List<Integer> indexes = tenantParameterIndexes(sql);
        if (indexes.isEmpty()) {
            throw new SecurityException("tenant_id must be bound as a SQL parameter");
        }
        for (int index : indexes) {
            Object actual = index < parameterValues.size() ? parameterValues.get(index) : null;
            if (actual == null || !expectedTenant.equals(String.valueOf(actual))) {
                throw new SecurityException("tenant_id parameter does not match the authenticated tenant");
            }
        }
    }

    private static void requireCurrentTenantValues(
            MappedStatement statement, BoundSql boundSql, Object parameterObject) {
        List<Object> values = new ArrayList<>();
        for (ParameterMapping mapping : boundSql.getParameterMappings()) {
            String property = mapping.getProperty();
            Object value;
            if (boundSql.hasAdditionalParameter(property)) {
                value = boundSql.getAdditionalParameter(property);
            } else if (parameterObject == null) {
                value = null;
            } else {
                var metaObject = statement.getConfiguration().newMetaObject(parameterObject);
                value = metaObject.hasGetter(property) ? metaObject.getValue(property) : parameterObject;
            }
            values.add(value);
        }
        requireTenantValues(boundSql.getSql(), UserContextHolder.currentTenantId(), values);
    }

    private static List<Integer> tenantParameterIndexes(String sql) {
        String normalized = normalize(sql);
        List<Integer> indexes = new ArrayList<>();
        if (normalized.startsWith("insert ")) {
            int columnsStart = normalized.indexOf('(');
            int columnsEnd = normalized.indexOf(')', columnsStart + 1);
            int values = normalized.indexOf(" values", columnsEnd);
            int valueStart = normalized.indexOf('(', values);
            if (columnsStart >= 0 && columnsEnd > columnsStart && valueStart >= 0) {
                String[] columns =
                        normalized.substring(columnsStart + 1, columnsEnd).split(",");
                for (int index = 0; index < columns.length; index++) {
                    if (columns[index].trim().endsWith("tenant_id")) indexes.add(index);
                }
            }
            return indexes;
        }
        Matcher matcher = TENANT_EQUALS_PARAMETER.matcher(normalized);
        while (matcher.find()) {
            int question = normalized.indexOf('?', matcher.start());
            indexes.add(countQuestions(normalized, question));
        }
        return indexes;
    }

    private static int countQuestions(String sql, int beforeIndex) {
        int count = 0;
        for (int index = 0; index < beforeIndex; index++) {
            if (sql.charAt(index) == '?') count++;
        }
        return count;
    }

    private static boolean guards(String sql, Set<String> tables) {
        String normalized = normalize(sql);
        return tables != null && tables.stream().anyMatch(table -> containsIdentifier(normalized, table));
    }

    private static boolean containsIdentifier(String sql, String identifier) {
        if (identifier == null || identifier.isBlank()) return false;
        String table = identifier.trim().toLowerCase(Locale.ROOT);
        int from = 0;
        while (true) {
            int index = sql.indexOf(table, from);
            if (index < 0) return false;
            int end = index + table.length();
            boolean leftBoundary = index == 0 || !isIdentifierCharacter(sql.charAt(index - 1));
            boolean rightBoundary = end == sql.length() || !isIdentifierCharacter(sql.charAt(end));
            if (leftBoundary && rightBoundary) return true;
            from = index + 1;
        }
    }

    private static boolean isIdentifierCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }

    private static String normalize(String sql) {
        return sql == null
                ? ""
                : sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
