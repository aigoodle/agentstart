package io.aigoodle.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.aigoodle.workflow.entity.WorkflowRunNodeEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowRunNodeMapper extends BaseMapper<WorkflowRunNodeEntity> {}
