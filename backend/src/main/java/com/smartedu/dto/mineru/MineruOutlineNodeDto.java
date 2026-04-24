package com.smartedu.dto.mineru;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * MinerU 解析结果派生出的文档大纲节点。
 *
 * <p>
 * 由 heading 级别（text_level=1..6）的 content block 折叠而来，形成树状层级。
 * 前端可渲染为大纲树，并用 {@link #blockIndex} 跳转到正文。
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MineruOutlineNodeDto {

    /** 标题文本。 */
    private String title;

    /** 标题级别 1-6。 */
    private int level;

    /** 该标题对应的 content block 下标，便于前端锚点跳转。 */
    private int blockIndex;

    /** 标题所在页码，从 0 开始；未知时为 -1。 */
    private int pageIdx = -1;

    /** 子节点。 */
    private List<MineruOutlineNodeDto> children = new ArrayList<>();
}
