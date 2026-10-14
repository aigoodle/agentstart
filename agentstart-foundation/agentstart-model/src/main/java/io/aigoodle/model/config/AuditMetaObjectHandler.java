package io.aigoodle.model.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;

/**
 * Auto-populates {@code createdAt}/{@code updatedAt} on insert/update and defaults a
 * blank {@code tenantId} to {@code "default"} so tenancy is opt-in.
 */
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        Object tenantId = getFieldValByName("tenantId", metaObject);
        if (tenantId == null || String.valueOf(tenantId).isBlank()) {
            strictInsertFill(metaObject, "tenantId", String.class, "default");
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
