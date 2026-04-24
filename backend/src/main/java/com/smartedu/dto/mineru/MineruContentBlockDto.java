package com.smartedu.dto.mineru;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * MinerU content_list.json 中的单个内容块。
 *
 * <p>
 * 字段尽量忠实 MinerU 原始命名，仅把图片路径替换为后端可访问的 URL，便于前端直接渲染。
 * 以 {@link #type} 字段区分 text / heading / image / table / equation / list / code 等；不属于
 * 展示集合的类型（page_number、footer 等）在装配阶段会被过滤掉。
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MineruContentBlockDto {

    /** 块在整个 contentBlocks 数组内的自增序号，前端用作定位锚点。 */
    private int index;

    /**
     * MinerU 原始类型：text、image、table、equation、chart、list、code 等。
     * 上层不应假设枚举完备，未知类型按 text 处理。
     */
    private String type;

    /** 文本内容；image/table 仅可能含 caption。 */
    private String text;

    /**
     * 标题级别：1-6；0 或空表示正文。
     * MinerU text_level 字段的直传。
     */
    private Integer textLevel;

    /** 图片 / 表格 / 公式对应的资源 URL（已转换为后端静态资源 URL）。 */
    private String imageUrl;

    /** 相对 zip 内部的原始图片路径，保留便于追溯。 */
    private String imagePath;

    /** 图片/表格的说明文字列表。 */
    private List<String> imageCaption;
    private List<String> imageFootnote;
    private List<String> tableCaption;
    private List<String> tableFootnote;

    /** 表格 HTML，来自 MinerU table_body。 */
    private String tableBody;

    /** 公式格式，例如 latex。 */
    private String textFormat;

    /** 页码，从 0 开始。 */
    private Integer pageIdx;

    /** bbox [x0,y0,x1,y1]，坐标已归一化到 0-1000。 */
    private List<Number> bbox;

    /** 其他扩展字段（code sub_type、list sub_type 等）。 */
    private String subType;
}
