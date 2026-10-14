package io.aigoodle.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.aigoodle.model.entity.PredefinedModelEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PredefinedModelMapper extends BaseMapper<PredefinedModelEntity> {}
