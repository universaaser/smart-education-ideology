package com.smartedu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartedu.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    @Select("""
            SELECT
                id,
                source_type,
                source_id,
                chunk_index,
                title,
                content,
                source,
                source_url,
                course_id,
                knowledge_point_name,
                ideology_element,
                created_at,
                updated_at,
                deleted,
                MATCH(title, content) AGAINST (#{query} IN NATURAL LANGUAGE MODE) AS search_score
            FROM knowledge_chunks
            WHERE deleted = 0
              AND MATCH(title, content) AGAINST (#{query} IN NATURAL LANGUAGE MODE)
            ORDER BY search_score DESC, updated_at DESC
            LIMIT #{limit}
            """)
    List<KnowledgeChunk> searchFullText(@Param("query") String query, @Param("limit") int limit);
}
