package io.aigoodle.memory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.aigoodle.memory.entity.MemoryEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MemoryMapper extends BaseMapper<MemoryEntity> {}
