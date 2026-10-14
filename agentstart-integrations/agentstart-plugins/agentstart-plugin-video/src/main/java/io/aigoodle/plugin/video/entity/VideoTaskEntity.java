package io.aigoodle.plugin.video.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plugin_video_task")
public class VideoTaskEntity extends BaseEntity {
    private String invocationKey;
    private String ownerId;
    private String requestHash;
    private String endpointCipher;
    private String vendorTaskId;
    private String status;
    private String resultJson;
    private String lastError;
    private Integer cancelRequested;
    private Integer attempts;
    private Instant nextPollAt;
    private Instant deadlineAt;
    private String leaseToken;
    private Instant leaseUntil;

    @Override
    public String toString() {
        return "VideoTaskEntity[id=" + getId() + ", status=" + status + "]";
    }
}
