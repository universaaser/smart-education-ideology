import React, { useMemo, useState } from 'react';
import {
  Alert,
  Card,
  Empty,
  Image,
  Space,
  Statistic,
  Tabs,
  Tag,
  Tree,
  Typography,
} from 'antd';
import type { DataNode } from 'antd/es/tree';
import {
  FileTextOutlined,
  PictureOutlined,
  TableOutlined,
  FunctionOutlined,
  ReadOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import type {
  MineruContentBlockInfo,
  MineruOutlineNodeInfo,
  MineruStructuredContentInfo,
} from '../services/api';
import { MarkdownView } from './MarkdownView';

const { Text, Paragraph, Title } = Typography;

interface MineruStructuredViewProps {
  /** MinerU 全量结构化结果；为空时组件返回 null。 */
  content?: MineruStructuredContentInfo | null;
  /** 可选的原始 markdown；为空则由 blocks 合成预览。 */
  rawMarkdown?: string;
  /** 文件名/解析来源展示在 header。 */
  title?: string;
  /** 当前任务解析来源（MINERU / FALLBACK_LLM）用于顶部 Tag。 */
  parseMode?: string;
}

/**
 * MinerU 结构化结果展示组件。
 *
 * <p>
 * 统一用 Tabs 组织：概览（大纲+统计）/ 全文（markdown）/ 图片 / 表格 / 公式 / 分块原文。
 * 保留该组件内部所有计算，父组件只传入结构数据即可。
 */
export const MineruStructuredView: React.FC<MineruStructuredViewProps> = ({
  content,
  rawMarkdown,
  title,
  parseMode,
}) => {
  const [activeKey, setActiveKey] = useState<string>('overview');

  const outlineTree = useMemo<DataNode[]>(() => {
    if (!content?.outline) return [];
    const build = (nodes: MineruOutlineNodeInfo[]): DataNode[] =>
      nodes.map((n) => ({
        key: `outline-${n.blockIndex}`,
        title: (
          <Space size={6}>
            <Tag color="blue">H{n.level}</Tag>
            <span>{n.title || '(untitled)'}</span>
            {n.pageIdx >= 0 && <Text type="secondary">p.{n.pageIdx + 1}</Text>}
          </Space>
        ),
        children: n.children && n.children.length > 0 ? build(n.children) : undefined,
      }));
    return build(content.outline);
  }, [content?.outline]);

  if (!content) {
    return null;
  }

  const stats = content.stats;
  const hasMarkdown = Boolean(rawMarkdown && rawMarkdown.trim().length > 0);

  const parseModeTag = (() => {
    if (parseMode === 'MINERU') {
      return <Tag color="geekblue">MINERU</Tag>;
    }
    if (parseMode === 'FALLBACK_LLM') {
      return <Tag color="orange">FALLBACK</Tag>;
    }
    if (parseMode === 'REGENERATE') {
      return <Tag color="purple">REGENERATE</Tag>;
    }
    return null;
  })();

  return (
    <Card
      title={
        <Space size={8} wrap>
          <FileTextOutlined style={{ color: '#1677ff' }} />
          <span>{title || 'MinerU Structured View'}</span>
          {parseModeTag}
          {content.modelVersion && <Tag>{content.modelVersion}</Tag>}
        </Space>
      }
      bordered={false}
      style={{ borderRadius: 12, boxShadow: '0 1px 2px rgba(15, 23, 42, 0.04)' }}
      headStyle={{
        background: 'linear-gradient(90deg, #e6f4ff 0%, #ffffff 100%)',
        borderBottom: '1px solid #e2e8f0',
        borderRadius: '12px 12px 0 0',
      }}
      bodyStyle={{ padding: '18px 22px' }}
    >
      <Tabs
        activeKey={activeKey}
        onChange={setActiveKey}
        items={[
          {
            key: 'overview',
            label: (
              <Space size={4}>
                <UnorderedListOutlined />
                Overview
              </Space>
            ),
            children: (
              <Space direction="vertical" size="large" style={{ width: '100%' }}>
                {stats && <StatsRow stats={stats} />}
                <Card
                  size="small"
                  title="Document Outline"
                  bordered
                  style={{ borderRadius: 8 }}
                >
                  {outlineTree.length > 0 ? (
                    <Tree
                      showLine
                      defaultExpandAll
                      selectable={false}
                      treeData={outlineTree}
                    />
                  ) : (
                    <Empty description="No headings detected" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                  )}
                </Card>
              </Space>
            ),
          },
          {
            key: 'markdown',
            label: (
              <Space size={4}>
                <ReadOutlined />
                Full Markdown
              </Space>
            ),
            children: hasMarkdown ? (
              <MarkdownView content={rawMarkdown!} />
            ) : (
              <Empty description="No markdown content" />
            ),
          },
          {
            key: 'images',
            label: (
              <Space size={4}>
                <PictureOutlined />
                Images ({content.images?.length || 0})
              </Space>
            ),
            children: <ImagesGallery images={content.images || []} />,
          },
          {
            key: 'tables',
            label: (
              <Space size={4}>
                <TableOutlined />
                Tables ({content.tables?.length || 0})
              </Space>
            ),
            children: <TablesList tables={content.tables || []} />,
          },
          {
            key: 'equations',
            label: (
              <Space size={4}>
                <FunctionOutlined />
                Equations ({content.equations?.length || 0})
              </Space>
            ),
            children: <EquationsList equations={content.equations || []} />,
          },
          {
            key: 'blocks',
            label: (
              <Space size={4}>
                <FileTextOutlined />
                Blocks ({content.blocks?.length || 0})
              </Space>
            ),
            children: <BlocksList blocks={content.blocks || []} />,
          },
        ]}
      />
    </Card>
  );
};

const StatsRow: React.FC<{ stats: NonNullable<MineruStructuredContentInfo['stats']> }> = ({
  stats,
}) => (
  <div
    style={{
      display: 'grid',
      gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))',
      gap: 12,
    }}
  >
    <StatCard label="Pages" value={stats.pageCount} />
    <StatCard label="Headings" value={stats.headingCount} />
    <StatCard label="Paragraphs" value={stats.paragraphCount} />
    <StatCard label="Images" value={stats.imageCount} />
    <StatCard label="Tables" value={stats.tableCount} />
    <StatCard label="Equations" value={stats.equationCount} />
    <StatCard label="Lists" value={stats.listCount} />
    <StatCard label="Code" value={stats.codeCount} />
    <StatCard label="Chars" value={stats.wordCount} />
  </div>
);

const StatCard: React.FC<{ label: string; value: number }> = ({ label, value }) => (
  <Card size="small" bordered style={{ borderRadius: 8 }}>
    <Statistic title={label} value={value} />
  </Card>
);

const ImagesGallery: React.FC<{ images: MineruContentBlockInfo[] }> = ({ images }) => {
  if (!images.length) {
    return <Empty description="No images" />;
  }
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
        gap: 16,
      }}
    >
      {images.map((img) => (
        <Card
          key={img.index}
          size="small"
          bordered
          style={{ borderRadius: 8 }}
          cover={
            img.imageUrl ? (
              <Image
                src={img.imageUrl}
                alt={joinCaption(img.imageCaption) || `image-${img.index}`}
                style={{ objectFit: 'contain', maxHeight: 200, background: '#f8fafc' }}
                preview
              />
            ) : undefined
          }
        >
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            <Text strong ellipsis={{ tooltip: joinCaption(img.imageCaption) }}>
              {joinCaption(img.imageCaption) || `#${img.index}`}
            </Text>
            {typeof img.pageIdx === 'number' && img.pageIdx >= 0 && (
              <Text type="secondary">Page {img.pageIdx + 1}</Text>
            )}
            {img.imageFootnote && img.imageFootnote.length > 0 && (
              <Paragraph type="secondary" ellipsis={{ rows: 2, tooltip: joinCaption(img.imageFootnote) }}>
                {joinCaption(img.imageFootnote)}
              </Paragraph>
            )}
          </Space>
        </Card>
      ))}
    </div>
  );
};

const TablesList: React.FC<{ tables: MineruContentBlockInfo[] }> = ({ tables }) => {
  if (!tables.length) {
    return <Empty description="No tables" />;
  }
  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      {tables.map((t) => (
        <Card
          key={t.index}
          size="small"
          bordered
          style={{ borderRadius: 8 }}
          title={
            <Space size={8}>
              <TableOutlined />
              <Text strong ellipsis={{ tooltip: joinCaption(t.tableCaption) }}>
                {joinCaption(t.tableCaption) || `Table #${t.index}`}
              </Text>
              {typeof t.pageIdx === 'number' && t.pageIdx >= 0 && (
                <Tag>p.{t.pageIdx + 1}</Tag>
              )}
            </Space>
          }
        >
          {t.tableBody ? (
            // MinerU 返回 HTML 表格片段；外层 CSS 保留滚动，避免超宽撑开页面。
            <div
              style={{ overflowX: 'auto' }}
              dangerouslySetInnerHTML={{ __html: sanitizeTableHtml(t.tableBody) }}
            />
          ) : t.imageUrl ? (
            <Image src={t.imageUrl} alt={`table-${t.index}`} preview />
          ) : (
            <Empty description="No table body" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
          {t.tableFootnote && t.tableFootnote.length > 0 && (
            <Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
              {joinCaption(t.tableFootnote)}
            </Paragraph>
          )}
        </Card>
      ))}
    </Space>
  );
};

const EquationsList: React.FC<{ equations: MineruContentBlockInfo[] }> = ({ equations }) => {
  if (!equations.length) {
    return <Empty description="No equations" />;
  }
  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      {equations.map((eq) => (
        <Card
          key={eq.index}
          size="small"
          bordered
          style={{ borderRadius: 8 }}
          title={
            <Space size={8}>
              <FunctionOutlined />
              <Text strong>Equation #{eq.index}</Text>
              {eq.textFormat && <Tag color="purple">{eq.textFormat}</Tag>}
              {typeof eq.pageIdx === 'number' && eq.pageIdx >= 0 && (
                <Tag>p.{eq.pageIdx + 1}</Tag>
              )}
            </Space>
          }
        >
          <Space direction="vertical" size={8} style={{ width: '100%' }}>
            {eq.imageUrl && (
              <Image
                src={eq.imageUrl}
                alt={`equation-${eq.index}`}
                style={{ maxHeight: 120, objectFit: 'contain', background: '#f8fafc' }}
                preview
              />
            )}
            {eq.text && (
              <pre
                style={{
                  margin: 0,
                  padding: '8px 12px',
                  background: '#0f172a',
                  color: '#e2e8f0',
                  borderRadius: 6,
                  overflowX: 'auto',
                  fontSize: 13,
                }}
              >
                {eq.text}
              </pre>
            )}
          </Space>
        </Card>
      ))}
    </Space>
  );
};

const BlocksList: React.FC<{ blocks: MineruContentBlockInfo[] }> = ({ blocks }) => {
  if (!blocks.length) {
    return <Empty description="No structured blocks" />;
  }
  // 超长列表会拖慢 DOM，限制默认渲染 200 条并给出提示。
  const limit = 200;
  const visible = blocks.slice(0, limit);
  return (
    <Space direction="vertical" size={8} style={{ width: '100%' }}>
      {visible.map((b) => (
        <Card key={b.index} size="small" bordered style={{ borderRadius: 6 }}>
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            <Space size={6} wrap>
              <Tag color="blue">#{b.index}</Tag>
              <Tag>{b.type}</Tag>
              {typeof b.textLevel === 'number' && b.textLevel > 0 && (
                <Tag color="geekblue">H{b.textLevel}</Tag>
              )}
              {typeof b.pageIdx === 'number' && b.pageIdx >= 0 && (
                <Tag>p.{b.pageIdx + 1}</Tag>
              )}
              {b.subType && <Tag color="purple">{b.subType}</Tag>}
            </Space>
            {b.text && (
              <Paragraph style={{ margin: 0 }} ellipsis={{ rows: 3, expandable: true, symbol: 'more' }}>
                {b.text}
              </Paragraph>
            )}
            {b.imageUrl && b.type !== 'text' && (
              <Image
                src={b.imageUrl}
                alt={`block-${b.index}`}
                style={{ maxHeight: 160, objectFit: 'contain', background: '#f8fafc' }}
                preview
              />
            )}
          </Space>
        </Card>
      ))}
      {blocks.length > limit && (
        <Alert
          type="info"
          showIcon
          message={`Showing first ${limit} of ${blocks.length} blocks. Use other tabs for full aggregates.`}
        />
      )}
    </Space>
  );
};

function joinCaption(caption?: string[]): string {
  if (!caption || caption.length === 0) return '';
  return caption.join(' ').trim();
}

/**
 * 非常轻量的 HTML 清洗：只允许 table 相关标签，屏蔽 script/style/on* 事件。
 * 不是严格意义上的 XSS 防护，但后端输出来自 MinerU 受信任源，前端再兜底一次即可。
 */
function sanitizeTableHtml(html: string): string {
  return html
    .replace(/<\s*script[^>]*>[\s\S]*?<\s*\/\s*script\s*>/gi, '')
    .replace(/<\s*style[^>]*>[\s\S]*?<\s*\/\s*style\s*>/gi, '')
    .replace(/\son\w+\s*=\s*"[^"]*"/gi, '')
    .replace(/\son\w+\s*=\s*'[^']*'/gi, '');
}
