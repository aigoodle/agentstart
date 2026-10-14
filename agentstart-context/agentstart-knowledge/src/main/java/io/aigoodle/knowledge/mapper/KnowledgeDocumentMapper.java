package io.aigoodle.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.aigoodle.knowledge.entity.KnowledgeDocumentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocumentEntity> {
    @Select("SELECT parsed_document_json FROM goodle_documents WHERE tenant_id = #{tenantId} AND id = #{documentId}")
    String selectParsedDocumentJson(String tenantId, String documentId);

    @Select("SELECT source_data_base64 FROM goodle_documents WHERE tenant_id = #{tenantId} AND id = #{documentId}")
    String selectSourceDataBase64(String tenantId, String documentId);
}
