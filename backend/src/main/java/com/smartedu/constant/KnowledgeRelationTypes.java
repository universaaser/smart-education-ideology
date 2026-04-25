package com.smartedu.constant;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 知识关系类型常量
 *
 * <p>
 * 统一维护前后端和推荐逻辑共用的关系类型，避免中文枚举和英文枚举混用。
 */
public final class KnowledgeRelationTypes {

    public static final String TECH_BASE = "TECH_BASE";
    public static final String VALUE_SHOW = "VALUE_SHOW";
    public static final String THEORY_SUPPORT = "THEORY_SUPPORT";
    public static final String PRACTICE_APPLY = "PRACTICE_APPLY";

    private static final Map<String, String> LEGACY_MAPPING = new LinkedHashMap<>();

    static {
        LEGACY_MAPPING.put("技术基础", TECH_BASE);
        LEGACY_MAPPING.put("价值体现", VALUE_SHOW);
        LEGACY_MAPPING.put("理论支撑", THEORY_SUPPORT);
        LEGACY_MAPPING.put("实践应用", PRACTICE_APPLY);
    }

    private KnowledgeRelationTypes() {
    }

    /**
     * 兼容旧数据中的中文关系类型，并对未知值回退到默认技术基础关系。
     */
    public static String normalize(String relationType) {
        if (relationType == null || relationType.isBlank()) {
            return TECH_BASE;
        }

        String trimmed = relationType.trim();
        if (LEGACY_MAPPING.containsKey(trimmed)) {
            return LEGACY_MAPPING.get(trimmed);
        }

        if (supportedTypes().contains(trimmed)) {
            return trimmed;
        }

        return TECH_BASE;
    }

    /**
     * 不同关系的连线样式需要固定，避免前端新增关系时出现展示风格不一致。
     */
    public static String defaultLineStyle(String relationType) {
        return VALUE_SHOW.equals(normalize(relationType)) ? "DASHED" : "SOLID";
    }

    public static Set<String> supportedTypes() {
        return Set.of(TECH_BASE, VALUE_SHOW, THEORY_SUPPORT, PRACTICE_APPLY);
    }

    public static boolean canNormalize(String relationType) {
        if (relationType == null || relationType.isBlank()) {
            return false;
        }
        String trimmed = relationType.trim();
        return LEGACY_MAPPING.containsKey(trimmed) || supportedTypes().contains(trimmed);
    }
}
