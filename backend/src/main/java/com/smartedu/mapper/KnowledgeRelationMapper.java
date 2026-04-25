package com.smartedu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartedu.entity.KnowledgeRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * 知识关系 Mapper 接口
 * 
 * @author SmartEducation Team
 */
@Mapper
public interface KnowledgeRelationMapper extends BaseMapper<KnowledgeRelation> {

    @Update("""
            UPDATE knowledge_relations
            SET from_node_id = #{fromNodeId},
                to_node_id = #{toNodeId},
                relation_type = #{relationType},
                line_style = #{lineStyle},
                weight = #{weight},
                description = #{description},
                creator_id = #{creatorId},
                updated_at = #{updatedAt},
                deleted = 0
            WHERE id = #{id}
            """)
    int restoreDeleted(KnowledgeRelation relation);
}
