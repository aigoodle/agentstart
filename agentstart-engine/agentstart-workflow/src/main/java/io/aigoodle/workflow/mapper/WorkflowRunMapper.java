package io.aigoodle.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.aigoodle.workflow.entity.WorkflowRunEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowRunMapper extends BaseMapper<WorkflowRunEntity> {}
