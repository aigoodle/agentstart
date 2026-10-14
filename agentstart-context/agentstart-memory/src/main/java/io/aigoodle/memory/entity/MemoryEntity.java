package io.aigoodle.memory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_memories")
public class MemoryEntity extends BaseEntity {
    private String ownerId;
    private String conversationId;
    private String tier;
    private String role;
    private String content;
    private Double importance;
    private LocalDateTime expiresAt;
    private Integer accessCount;
}
