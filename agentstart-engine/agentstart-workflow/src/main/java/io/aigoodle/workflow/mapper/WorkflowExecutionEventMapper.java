package io.aigoodle.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.aigoodle.workflow.entity.WorkflowExecutionEventEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowExecutionEventMapper extends BaseMapper<WorkflowExecutionEventEntity> {}
