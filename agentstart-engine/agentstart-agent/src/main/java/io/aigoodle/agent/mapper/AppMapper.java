package io.aigoodle.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.aigoodle.agent.entity.AppEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppMapper extends BaseMapper<AppEntity> {}
