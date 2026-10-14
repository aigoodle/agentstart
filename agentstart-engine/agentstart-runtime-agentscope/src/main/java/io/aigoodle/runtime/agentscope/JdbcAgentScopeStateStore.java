package io.aigoodle.runtime.agentscope;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import io.agentscope.core.state.VersionedState;
import io.aigoodle.common.util.JsonUtils;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Database-backed SDK session state. CAS writes protect shared sessions across processes. */
public final class JdbcAgentScopeStateStore implements AgentStateStore {
    private final JdbcTemplate jdbc;

    public JdbcAgentScopeStateStore(JdbcTemplate jdbc) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc);
    }

    @Override
    public boolean supportsVersioning() {
        return true;
    }

    @Override
    public void save(String user, String session, String key, State value) {
        write(user, session, key, JsonUtils.toJson(value), false);
    }

    @Override
    public void save(String user, String session, String key, List<? extends State> values) {
        write(user, session, key, JsonUtils.toJson(values), true);
    }

    private void write(String user, String session, String key, String json, boolean list) {
        if (update(user, session, key, json, list) != 0) return;
        try {
            insert(user, session, key, json, list);
        } catch (DuplicateKeyException conflict) {
            if (update(user, session, key, json, list) == 0) throw conflict;
        }
    }

    private int update(String user, String session, String key, String json, boolean list) {
        return jdbc.update(
                "UPDATE agent_scope_state SET payload_json=?, list_value=?, version=version+1 WHERE user_id=? AND session_id=? AND state_key=?",
                json,
                list,
                user,
                session,
                key);
    }

    private void insert(String user, String session, String key, String json, boolean list) {
        jdbc.update(
                "INSERT INTO agent_scope_state (user_id,session_id,state_key,payload_json,list_value,version) VALUES (?,?,?,?,?,1)",
                user,
                session,
                key,
                json,
                list);
    }

    @Override
    public long saveIfVersion(String user, String session, String key, State value, long expectedVersion) {
        if (expectedVersion == UNVERSIONED) {
            save(user, session, key, value);
            return rows(user, session, key).getFirst().version();
        }
        if (expectedVersion < 0) throw new IllegalArgumentException("Version must be nonnegative");
        String json = JsonUtils.toJson(value);
        if (expectedVersion == 0) {
            try {
                insert(user, session, key, json, false);
                return 1;
            } catch (DuplicateKeyException conflict) {
                return UNVERSIONED;
            }
        }
        int changed = jdbc.update(
                "UPDATE agent_scope_state SET payload_json=?, list_value=false, version=version+1 WHERE user_id=? AND session_id=? AND state_key=? AND version=?",
                json,
                user,
                session,
                key,
                expectedVersion);
        return changed == 1 ? expectedVersion + 1 : UNVERSIONED;
    }

    private record Stored(String json, boolean list, long version) {}

    private List<Stored> rows(String user, String session, String key) {
        return jdbc.query(
                "SELECT payload_json,list_value,version FROM agent_scope_state WHERE user_id=? AND session_id=? AND state_key=?",
                (row, index) -> new Stored(row.getString(1), row.getBoolean(2), row.getLong(3)),
                user,
                session,
                key);
    }

    @Override
    public <T extends State> VersionedState<T> getVersioned(String user, String session, String key, Class<T> type) {
        var found = rows(user, session, key);
        if (found.isEmpty()) return new VersionedState<>(null, 0);
        var value = found.getFirst();
        if (value.list()) throw new IllegalArgumentException("State key contains a list");
        return new VersionedState<>(JsonUtils.parse(value.json(), type), value.version());
    }

    @Override
    public <T extends State> Optional<T> get(String user, String session, String key, Class<T> type) {
        return Optional.ofNullable(getVersioned(user, session, key, type).value());
    }

    @Override
    public <T extends State> List<T> getList(String user, String session, String key, Class<T> type) {
        var found = rows(user, session, key);
        if (found.isEmpty()) return List.of();
        if (!found.getFirst().list()) throw new IllegalArgumentException("State key contains a single value");
        return JsonUtils.parseList(found.getFirst().json(), type);
    }

    @Override
    public boolean exists(String user, String session) {
        return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM agent_scope_state WHERE user_id=? AND session_id=?",
                        Long.class,
                        user,
                        session)
                > 0;
    }

    @Override
    public void delete(String user, String session) {
        jdbc.update("DELETE FROM agent_scope_state WHERE user_id=? AND session_id=?", user, session);
    }

    @Override
    public void delete(String user, String session, String key) {
        jdbc.update(
                "DELETE FROM agent_scope_state WHERE user_id=? AND session_id=? AND state_key=?", user, session, key);
    }

    @Override
    public Set<String> listSessionIds(String user) {
        return new LinkedHashSet<>(jdbc.queryForList(
                "SELECT DISTINCT session_id FROM agent_scope_state WHERE user_id=? ORDER BY session_id",
                String.class,
                user));
    }
}
