package io.aigoodle.plugin.seedance.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

/** Legacy schema uses the tenant-scoped invocation digest as its primary key. */
@Data
@TableName("plugin_seedance_submission")
public class SeedanceSubmissionEntity {
    @TableId(value = "invocation_key", type = IdType.INPUT)
    private String invocationKey;

    private String requestHash;
    private String taskId;
    private LocalDateTime createdAt;
}
