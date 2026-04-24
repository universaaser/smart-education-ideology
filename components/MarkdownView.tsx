import React from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

/**
 * 统一的 Markdown 渲染组件。
 * 说明：
 * - 用于解析结果、AI 分析、Selection Explanation 等由 LLM 返回的 Markdown 文本。
 * - 以 CSS class `md-view` 作为样式锚点，具体样式定义在 index.css。
 * - 使用 remark-gfm 以支持表格、任务列表、删除线等 GFM 语法。
 */
interface MarkdownViewProps {
  content?: string | null;
  /** 空内容时的占位文案，未提供则不渲染。 */
  emptyText?: string;
  /** 额外样式。 */
  style?: React.CSSProperties;
  /** 额外 className，会与 md-view 合并。 */
  className?: string;
  /** 是否以紧凑模式渲染（减少段落上下间距）。 */
  compact?: boolean;
}

export const MarkdownView: React.FC<MarkdownViewProps> = ({
  content,
  emptyText,
  style,
  className,
  compact,
}) => {
  const text = (content ?? '').trim();
  if (!text) {
    if (!emptyText) {
      return null;
    }
    return (
      <div className="md-view md-view-empty" style={style}>
        {emptyText}
      </div>
    );
  }

  const mergedClass = ['md-view', compact ? 'md-view-compact' : '', className || '']
    .filter(Boolean)
    .join(' ');

  return (
    <div className={mergedClass} style={style}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          // 外链默认在新窗口打开，避免中断当前解析流程。
          a: ({ node, ...props }) => (
            <a {...props} target="_blank" rel="noopener noreferrer" />
          ),
        }}
      >
        {text}
      </ReactMarkdown>
    </div>
  );
};
