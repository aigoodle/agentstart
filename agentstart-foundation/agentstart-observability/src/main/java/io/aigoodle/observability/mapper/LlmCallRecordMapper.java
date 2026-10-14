package io.aigoodle.observability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.aigoodle.observability.entity.LlmCallRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LlmCallRecordMapper extends BaseMapper<LlmCallRecord> {}
