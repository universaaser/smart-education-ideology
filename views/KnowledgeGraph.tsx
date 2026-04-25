import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  knowledgeApi,
  knowledgeExcelApi,
  KnowledgeNodeCreateRequest,
  KnowledgeNodeInfo,
  KnowledgeRelationInfo,
  resourceApi,
  CrawlTaskStatusInfo,
} from '../services/api';
import {
  Input,
  Button,
  Drawer,
  Space,
  Typography,
  Tag,
  Select,
  Spin,
  message,
  Tooltip,
  Divider,
  Form,
  Popconfirm,
  Modal,
} from 'antd';
import {
  PlusOutlined,
  MinusOutlined,
  SyncOutlined,
  StopOutlined,
  DownloadOutlined,
  UploadOutlined,
  LinkOutlined,
  ShareAltOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import { useAuth } from '../contexts/AuthContext';

const { Text, Title, Paragraph } = Typography;
const { TextArea } = Input;

const NODE_TYPE_COLORS: Record<string, { bg: string; border: string; text: string; glow: string }> = {
  TECH: { bg: '#e6f4ff', border: '#1677ff', text: '#0958d9', glow: 'rgba(22,119,255,0.15)' },
  IDEO: { bg: '#fff1f0', border: '#f5222d', text: '#cf1322', glow: 'rgba(245,34,45,0.15)' },
};

const RELATION_COLORS: Record<string, string> = {
  TECH_BASE: '#1677ff',
  VALUE_SHOW: '#f5222d',
  THEORY_SUPPORT: '#722ed1',
  PRACTICE_APPLY: '#52c41a',
};

const RELATION_TYPES = ['TECH_BASE', 'VALUE_SHOW', 'THEORY_SUPPORT', 'PRACTICE_APPLY'];

// 每个尺寸决定字号、换行宽度上限和最小盒子尺寸；盒子宽高会根据标题自适应扩展，保证标题完整显示在框内。
const NODE_SIZE_PRESETS: Record<string, { fontSize: number; maxCharsPerLine: number; minWidth: number; minHeight: number; paddingX: number; paddingY: number; radius: number }> = {
  SM: { fontSize: 11, maxCharsPerLine: 6, minWidth: 56, minHeight: 40, paddingX: 10, paddingY: 8, radius: 10 },
  MD: { fontSize: 13, maxCharsPerLine: 8, minWidth: 72, minHeight: 52, paddingX: 12, paddingY: 10, radius: 12 },
  LG: { fontSize: 15, maxCharsPerLine: 10, minWidth: 96, minHeight: 68, paddingX: 16, paddingY: 12, radius: 16 },
};

const NODE_SIZE_OPTIONS = [
  { label: 'Small', value: 'SM' },
  { label: 'Medium', value: 'MD' },
  { label: 'Large', value: 'LG' },
];

const getNodeColor = (nodeType: string) => NODE_TYPE_COLORS[nodeType] || NODE_TYPE_COLORS.TECH;
const getRelationColor = (relationType: string) => RELATION_COLORS[relationType] || '#8c8c8c';

const normalizeNodeSize = (nodeSize?: string) => {
  const normalized = nodeSize?.trim().toUpperCase();
  if (normalized === 'SM' || normalized === 'LG') {
    return normalized;
  }
  return 'MD';
};

// 估算字符显示宽度：中文/全角按 1.0 字号，其他按 0.6 字号，避免英文标题把盒子撑得过宽。
const estimateTextWidth = (text: string, fontSize: number) => {
  let width = 0;
  for (const ch of text) {
    const code = ch.charCodeAt(0);
    const isWide = code > 0x2e80 || code === 0x3000; // CJK 及全角符号
    width += fontSize * (isWide ? 1.0 : 0.6);
  }
  return width;
};

// 按最大字符数换行，同时尊重英文单词边界；输入为空时返回空数组。
const wrapNodeLabel = (label: string, maxChars: number) => {
  const normalized = label?.trim() || '';
  if (!normalized) {
    return [];
  }
  const lines: string[] = [];
  let current = '';
  const flush = () => {
    if (current) {
      lines.push(current);
      current = '';
    }
  };
  for (const ch of normalized) {
    if (ch === '\n') {
      flush();
      continue;
    }
    if (current.length >= maxChars) {
      // 英文长词：若当前行尾仍在单词中，尝试回退到上一个空格处换行。
      if (/[A-Za-z0-9]/.test(ch) && /[A-Za-z0-9]/.test(current[current.length - 1])) {
        const lastSpace = current.lastIndexOf(' ');
        if (lastSpace > 0) {
          lines.push(current.slice(0, lastSpace));
          current = current.slice(lastSpace + 1);
        } else {
          flush();
        }
      } else {
        flush();
      }
    }
    current += ch;
  }
  flush();
  return lines;
};

interface NodeBox {
  lines: string[];
  width: number;
  height: number;
  fontSize: number;
  lineHeight: number;
  radius: number;
}

const computeNodeBox = (name: string, nodeSize?: string): NodeBox => {
  const preset = NODE_SIZE_PRESETS[normalizeNodeSize(nodeSize)] || NODE_SIZE_PRESETS.MD;
  const lines = wrapNodeLabel(name || '', preset.maxCharsPerLine);
  const displayLines = lines.length > 0 ? lines : [''];
  const longestWidth = displayLines.reduce((acc, line) => Math.max(acc, estimateTextWidth(line, preset.fontSize)), 0);
  const lineHeight = preset.fontSize + 4;
  const width = Math.max(preset.minWidth, Math.ceil(longestWidth + preset.paddingX * 2));
  const height = Math.max(preset.minHeight, Math.ceil(displayLines.length * lineHeight + preset.paddingY * 2));
  return {
    lines: displayLines,
    width,
    height,
    fontSize: preset.fontSize,
    lineHeight,
    radius: preset.radius,
  };
};

interface KnowledgeGraphProps {
  crawlStatus?: CrawlTaskStatusInfo | null;
  refreshCrawlStatus?: () => Promise<void> | void;
  highlightNodeIds?: number[];
  onNodeView?: (node: KnowledgeNodeInfo) => void;
}

export const KnowledgeGraph: React.FC<KnowledgeGraphProps> = ({
  crawlStatus,
  refreshCrawlStatus,
  highlightNodeIds = [],
  onNodeView,
}) => {
  const { roleUi } = useAuth();
  const [nodes, setNodes] = useState<KnowledgeNodeInfo[]>([]);
  const [connections, setConnections] = useState<KnowledgeRelationInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedNode, setSelectedNode] = useState<KnowledgeNodeInfo | null>(null);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [scale, setScale] = useState(1);
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const [isPanning, setIsPanning] = useState(false);
  const [panStart, setPanStart] = useState({ x: 0, y: 0 });
  const [draggingNodeId, setDraggingNodeId] = useState<number | null>(null);
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 });
  const [showAddRelation, setShowAddRelation] = useState(false);
  const [relationForm] = Form.useForm();
  const [showCreateNode, setShowCreateNode] = useState(false);
  const [createNodeForm] = Form.useForm<KnowledgeNodeCreateRequest>();
  const [addingRelation, setAddingRelation] = useState(false);
  const [editingRelationId, setEditingRelationId] = useState<number | null>(null);
  const [deletingRelationId, setDeletingRelationId] = useState<number | null>(null);
  const [undoingRelationChange, setUndoingRelationChange] = useState(false);
  const [creatingNode, setCreatingNode] = useState(false);
  const [actionPending, setActionPending] = useState(false);
  const [excelImporting, setExcelImporting] = useState(false);
  const [deletingNodeId, setDeletingNodeId] = useState<number | null>(null);
  const excelInputRef = useRef<HTMLInputElement>(null);

  const svgRef = useRef<SVGSVGElement>(null);
  const lastCrawlStateRef = useRef<string>('IDLE');
  const canEditKnowledgeGraph = !!roleUi?.capabilities?.canEditKnowledgeGraph;

  const loadGraph = useCallback(async (showLoading = false) => {
    if (showLoading) {
      setLoading(true);
    }
    try {
      const data = await knowledgeApi.getGraph();
      setNodes(data.nodes || []);
      setConnections(data.relations || []);
    } catch {
      // Keep an empty graph when the backend is unavailable.
    } finally {
      if (showLoading) {
        setLoading(false);
      }
    }
  }, []);

  const focusNode = useCallback((node: KnowledgeNodeInfo) => {
    setSelectedNode(node);
    const svgWidth = svgRef.current?.clientWidth || 0;
    const svgHeight = svgRef.current?.clientHeight || 0;
    setOffset({
      x: -node.positionX * scale + svgWidth / 2,
      y: -node.positionY * scale + svgHeight / 2,
    });
  }, [scale]);

  useEffect(() => {
    loadGraph(true);
  }, [loadGraph]);

  const crawlState = crawlStatus?.state ?? 'IDLE';
  const isCrawling = crawlState === 'RUNNING' || crawlState === 'STOP_REQUESTED';

  useEffect(() => {
    const previous = lastCrawlStateRef.current;
    const current = crawlState;
    const wasRunning = previous === 'RUNNING' || previous === 'STOP_REQUESTED';
    const ended = current === 'COMPLETED' || current === 'STOPPED' || current === 'FAILED';

    if (wasRunning && ended) {
      const result = crawlStatus?.lastResult;
      if (current === 'COMPLETED' && result) {
        message.success(`Update complete: +${result.totalCreated}, deduplicated ${result.totalDeduplicated}, failed ${result.totalFailed}`);
      } else if (current === 'FAILED') {
        message.error('Update failed. Please try again.');
      }
      void loadGraph();
    }

    if (current === 'STOP_REQUESTED' && previous !== 'STOP_REQUESTED') {
      message.info('Stop requested. The crawler will stop after the current article.');
    }

    lastCrawlStateRef.current = current;
  }, [crawlState, crawlStatus, loadGraph]);

  const handleSearch = async (value: string) => {
    if (!value.trim()) {
      await loadGraph();
      return;
    }

    try {
      const results = await knowledgeApi.searchNodes(value);
      if (results.length === 0) {
        message.warning('No matching node was found.');
        return;
      }

      const found = nodes.find(node => results.some(result => result.id === node.id));
      if (!found) {
        message.warning('No matching node was found.');
        return;
      }
      focusNode(found);
    } catch {
      // Keep the current graph state on search failures.
    }
  };

  const handleManualUpdate = async () => {
    if (actionPending) {
      return;
    }

    setActionPending(true);
    try {
      if (isCrawling) {
        await resourceApi.stopCrawlUpdate();
      } else {
        const status = await resourceApi.startCrawlUpdate();
        if (status.state === 'RUNNING' || status.state === 'STOP_REQUESTED') {
          message.info('Crawler update started...');
        } else {
          message.info(status.message || 'An update task is already running.');
        }
      }
      await refreshCrawlStatus?.();
    } catch {
      message.error(isCrawling ? 'Failed to stop the crawler update.' : 'Failed to start the crawler update.');
    } finally {
      setActionPending(false);
    }
  };

  const handleDownloadTemplate = async () => {
    try {
      await knowledgeExcelApi.downloadTemplate();
    } catch {
      message.error('Failed to download the template. Please try again.');
    }
  };

  const handleExcelImport = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }

    event.target.value = '';
    setExcelImporting(true);
    const hide = message.loading('Importing Excel...', 0);
    try {
      const result = await knowledgeExcelApi.importFromExcel(file);
      hide();
      message.success(`Import complete: ${result.createdNodeCount} nodes created, ${result.createdRelationCount} relations created.`);
      await loadGraph();
    } catch {
      hide();
      message.error('Excel import failed. Please check the file format.');
    } finally {
      setExcelImporting(false);
    }
  };

  const handleWheel = useCallback((event: React.WheelEvent) => {
    event.preventDefault();
    const delta = event.deltaY > 0 ? 0.9 : 1.1;
    setScale(previous => Math.min(Math.max(previous * delta, 0.3), 3));
  }, []);

  const handleMouseDown = useCallback((event: React.MouseEvent) => {
    if (event.target === svgRef.current || (event.target as Element).tagName === 'line') {
      setIsPanning(true);
      setPanStart({ x: event.clientX - offset.x, y: event.clientY - offset.y });
    }
  }, [offset]);

  const handleMouseMove = useCallback((event: React.MouseEvent) => {
    if (isPanning) {
      setOffset({ x: event.clientX - panStart.x, y: event.clientY - panStart.y });
    }

    if (draggingNodeId !== null) {
      const dx = (event.clientX - dragStart.x) / scale;
      const dy = (event.clientY - dragStart.y) / scale;
      setDragStart({ x: event.clientX, y: event.clientY });
      setNodes(previous =>
        previous.map(node =>
          node.id === draggingNodeId
            ? { ...node, positionX: node.positionX + dx, positionY: node.positionY + dy }
            : node
        )
      );
    }
  }, [dragStart, draggingNodeId, isPanning, panStart, scale]);

  const handleMouseUp = useCallback(() => {
    if (draggingNodeId !== null) {
      const node = nodes.find(item => item.id === draggingNodeId);
      if (node) {
        knowledgeApi.updateNodePosition(node.id, node.positionX, node.positionY).catch(() => {});
      }
    }
    setIsPanning(false);
    setDraggingNodeId(null);
  }, [draggingNodeId, nodes]);

  const handleNodeMouseDown = useCallback((event: React.MouseEvent, nodeId: number) => {
    event.stopPropagation();
    setDraggingNodeId(nodeId);
    setDragStart({ x: event.clientX, y: event.clientY });
  }, []);

  const getNodePosition = (nodeId: number) => {
    const node = nodes.find(item => item.id === nodeId);
    return node ? { x: node.positionX, y: node.positionY } : null;
  };

  const findNodeName = (nodeId: number) => nodes.find(node => node.id === nodeId)?.name || `Node #${nodeId}`;

  const selectedRelations = selectedNode
    ? connections.filter(connection => connection.fromNodeId === selectedNode.id || connection.toNodeId === selectedNode.id)
    : [];

  const handleAddRelation = async () => {
    if (!selectedNode || addingRelation) {
      return;
    }

    try {
      const values = await relationForm.validateFields();
      setAddingRelation(true);
      const newRelation = await knowledgeApi.createRelation(
        selectedNode.id,
        values.targetNodeId,
        values.relationType
      );
      setConnections(previous => [...previous, newRelation]);
      setShowAddRelation(false);
      relationForm.resetFields();
      message.success('Relation added successfully.');
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Failed to add relation.');
    } finally {
      setAddingRelation(false);
    }
  };

  const handleUpdateRelation = async (connection: KnowledgeRelationInfo, relationType: string) => {
    setEditingRelationId(connection.id);
    try {
      const updated = await knowledgeApi.updateRelation(connection.id, {
        relationType,
        description: connection.description,
        weight: connection.weight,
      });
      setConnections(previous => previous.map(item => item.id === updated.id ? updated : item));
      message.success('Relation updated successfully.');
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Failed to update relation.');
    } finally {
      setEditingRelationId(null);
    }
  };

  const handleDeleteRelation = async (connection: KnowledgeRelationInfo) => {
    setDeletingRelationId(connection.id);
    try {
      await knowledgeApi.deleteRelation(connection.id);
      setConnections(previous => previous.filter(item => item.id !== connection.id));
      message.success('Relation deleted successfully.');
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Failed to delete relation.');
    } finally {
      setDeletingRelationId(null);
    }
  };

  const handleUndoLatestRelationChange = async () => {
    if (undoingRelationChange) {
      return;
    }

    setUndoingRelationChange(true);
    try {
      await knowledgeApi.undoLatestRelationChange();
      await loadGraph();
      message.success('Latest relation change undone.');
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Failed to undo relation change.');
    } finally {
      setUndoingRelationChange(false);
    }
  };

  const handleCreateNode = async () => {
    if (creatingNode) {
      return;
    }

    try {
      const values = await createNodeForm.validateFields();
      setCreatingNode(true);
      const created = await knowledgeApi.createNode({
        ...values,
        nodeSize: normalizeNodeSize(values.nodeSize),
      });
      setShowCreateNode(false);
      createNodeForm.resetFields();
      message.success(`Node "${created.name}" created successfully.`);
      await loadGraph();
      focusNode(created);
    } catch (error) {
      if (error instanceof Error && error.message.startsWith('Request failed')) {
        message.error(error.message);
      }
    } finally {
      setCreatingNode(false);
    }
  };

  const handleDeleteSelectedNode = async () => {
    if (!selectedNode || deletingNodeId !== null) {
      return;
    }

    const deletingNode = selectedNode;
    setDeletingNodeId(deletingNode.id);
    try {
      await knowledgeApi.deleteNode(deletingNode.id);
      message.success(`Node "${deletingNode.name}" deleted successfully.`);
      setSelectedNode(null);
      setShowAddRelation(false);
      relationForm.resetFields();
      await loadGraph();
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Failed to delete the selected node.');
    } finally {
      setDeletingNodeId(null);
    }
  };

  const openCreateNodeModal = () => {
    createNodeForm.setFieldsValue({ nodeSize: 'MD' });
    setShowCreateNode(true);
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', flex: 1, alignItems: 'center', justifyContent: 'center', height: '100%' }}>
        <Space direction="vertical" align="center">
          <Spin size="large" />
          <Text type="secondary">Loading knowledge graph...</Text>
        </Space>
      </div>
    );
  }

  return (
    <div style={{ flex: 1, height: '100%', position: 'relative', overflow: 'hidden', display: 'flex' }}>
      <div style={{ position: 'absolute', top: 16, left: 16, zIndex: 10, display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>
        <Input.Search
          placeholder="Search nodes..."
          allowClear
          onSearch={handleSearch}
          value={searchKeyword}
          onChange={event => setSearchKeyword(event.target.value)}
          style={{ width: 220, borderRadius: 8, boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}
        />

        {canEditKnowledgeGraph && (
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={openCreateNodeModal}
            style={{ boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}
          >
            Add Node
          </Button>
        )}

        <Space.Compact style={{ boxShadow: '0 2px 8px rgba(0,0,0,0.05)', borderRadius: 8 }}>
          <Button icon={<PlusOutlined />} onClick={() => setScale(value => Math.min(value * 1.2, 3))} />
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              background: '#fff',
              borderTop: '1px solid #d9d9d9',
              borderBottom: '1px solid #d9d9d9',
              padding: '0 8px',
              fontSize: 13,
              minWidth: 50,
              color: '#595959',
            }}
          >
            {Math.round(scale * 100)}%
          </span>
          <Button icon={<MinusOutlined />} onClick={() => setScale(value => Math.max(value * 0.8, 0.3))} />
        </Space.Compact>

        <Button
          icon={isCrawling ? <StopOutlined /> : <SyncOutlined spin={actionPending} />}
          onClick={handleManualUpdate}
          loading={actionPending}
          style={{ boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}
        >
          {isCrawling ? 'Stop Update' : 'Run Crawler Update'}
        </Button>

        {crawlStatus && isCrawling && (
          <Tag color="processing" style={{ margin: 0 }}>
            Status: {crawlState}{crawlStatus.currentSite ? ` | ${crawlStatus.currentSite}` : ''}
          </Tag>
        )}

        <Space.Compact style={{ boxShadow: '0 2px 8px rgba(0,0,0,0.05)', borderRadius: 8 }}>
          <Tooltip title="Download Excel template">
            <Button icon={<DownloadOutlined style={{ color: '#52c41a' }} />} onClick={handleDownloadTemplate}>Template</Button>
          </Tooltip>
          <Tooltip title="Import from Excel">
            <Button icon={<UploadOutlined style={{ color: '#1677ff' }} />} onClick={() => excelInputRef.current?.click()} loading={excelImporting}>Import</Button>
          </Tooltip>
          <input ref={excelInputRef} type="file" accept=".xlsx,.xls" onChange={handleExcelImport} style={{ display: 'none' }} />
        </Space.Compact>

        {canEditKnowledgeGraph && (
          <Button loading={undoingRelationChange} onClick={() => void handleUndoLatestRelationChange()}>
            Undo Relation Change
          </Button>
        )}

        <div style={{ display: 'flex', gap: 12, background: '#fff', padding: '6px 12px', borderRadius: 8, border: '1px solid #d9d9d9', boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <div style={{ width: 12, height: 12, borderRadius: 2, background: NODE_TYPE_COLORS.TECH.border }} />
            <Text type="secondary" style={{ fontSize: 13 }}>Technology</Text>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <div style={{ width: 12, height: 12, borderRadius: 2, background: NODE_TYPE_COLORS.IDEO.border }} />
            <Text type="secondary" style={{ fontSize: 13 }}>Ideology</Text>
          </div>
        </div>
      </div>

      <svg
        ref={svgRef}
        className="graph-pattern"
        style={{
          flex: 1,
          height: '100%',
          width: '100%',
          backgroundColor: 'var(--bg-light)',
          cursor: isPanning ? 'grabbing' : 'grab',
        }}
        onWheel={handleWheel}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
      >
        <g transform={`translate(${offset.x}, ${offset.y}) scale(${scale})`}>
          {connections.map(connection => {
            const from = getNodePosition(connection.fromNodeId);
            const to = getNodePosition(connection.toNodeId);
            if (!from || !to) {
              return null;
            }

            const lineColor = getRelationColor(connection.relationType);
            const isDashed = connection.lineStyle === 'DASHED';

            return (
              <line
                key={connection.id}
                x1={from.x}
                y1={from.y}
                x2={to.x}
                y2={to.y}
                stroke={lineColor}
                strokeWidth={1.5}
                strokeDasharray={isDashed ? '6,4' : undefined}
                strokeOpacity={0.7}
              />
            );
          })}

          {nodes.map(node => {
            const color = getNodeColor(node.nodeType);
            const isSelected = selectedNode?.id === node.id;
            const isHighlighted = highlightNodeIds.includes(node.id);
            const box = computeNodeBox(node.name, node.nodeSize);
            const hasIcon = !!node.icon;
            const iconSize = Math.round(box.fontSize * 1.4);
            // 有图标时，图标占据首行上方的一块空间；整体布局按"图标 + 文本块"上下居中。
            const iconGap = hasIcon ? 4 : 0;
            const textBlockHeight = box.lines.length * box.lineHeight;
            const contentHeight = (hasIcon ? iconSize + iconGap : 0) + textBlockHeight;
            const contentTop = (box.height - contentHeight) / 2;
            const iconY = hasIcon ? contentTop + iconSize * 0.85 : 0;
            const textTop = contentTop + (hasIcon ? iconSize + iconGap : 0);

            return (
              <g
                key={node.id}
                transform={`translate(${node.positionX - box.width / 2}, ${node.positionY - box.height / 2})`}
                onClick={event => {
                  event.stopPropagation();
                  setSelectedNode(node);
                  onNodeView?.(node);
                }}
                onMouseDown={event => handleNodeMouseDown(event, node.id)}
                style={{ cursor: 'pointer' }}
              >
                {isHighlighted && (
                  <rect
                    x={-6}
                    y={-6}
                    width={box.width + 12}
                    height={box.height + 12}
                    rx={box.radius + 6}
                    fill="rgba(250,173,20,0.15)"
                    stroke="#faad14"
                    strokeWidth={2}
                    strokeDasharray="5,3"
                  />
                )}
                {isSelected && (
                  <rect
                    x={-4}
                    y={-4}
                    width={box.width + 8}
                    height={box.height + 8}
                    rx={box.radius + 4}
                    fill={color.glow}
                  />
                )}
                <rect
                  width={box.width}
                  height={box.height}
                  rx={box.radius}
                  fill={color.bg}
                  stroke={color.border}
                  strokeWidth={isSelected ? 3 : 1.5}
                />
                {hasIcon && (
                  <text
                    x={box.width / 2}
                    y={iconY}
                    textAnchor="middle"
                    fill={color.text}
                    fontSize={iconSize}
                    fontFamily="Material Symbols Outlined"
                    style={{ userSelect: 'none' }}
                  >
                    {node.icon}
                  </text>
                )}
                <text
                  textAnchor="middle"
                  fill={color.text}
                  fontSize={box.fontSize}
                  fontWeight={600}
                  style={{ userSelect: 'none' }}
                >
                  {box.lines.map((line, index) => (
                    <tspan
                      key={`${node.id}-${index}`}
                      x={box.width / 2}
                      y={textTop + (index + 1) * box.lineHeight - 4}
                    >
                      {line}
                    </tspan>
                  ))}
                </text>
              </g>
            );
          })}
        </g>
        {nodes.length === 0 && (
          <text x="50%" y="50%" textAnchor="middle" fill="#bfbfbf" fontSize={14}>
            No knowledge graph data
          </text>
        )}
      </svg>

      <Drawer
        title={(
          <div>
            <Title level={4} style={{ margin: 0, paddingRight: 24, fontSize: 18 }}>{selectedNode?.name}</Title>
            <Space style={{ marginTop: 8 }}>
              <Tag color={selectedNode?.nodeType === 'IDEO' ? 'error' : 'processing'}>
                {selectedNode?.nodeType === 'TECH' ? 'TECH' : 'IDEO'}
              </Tag>
              {selectedNode?.subject && <Text type="secondary" style={{ fontSize: 12 }}>{selectedNode.subject}</Text>}
              {selectedNode?.nodeSize && <Tag style={{ margin: 0 }}>{normalizeNodeSize(selectedNode.nodeSize)}</Tag>}
            </Space>
          </div>
        )}
        placement="right"
        closable
        onClose={() => {
          setSelectedNode(null);
          setShowAddRelation(false);
        }}
        open={!!selectedNode}
        getContainer={false}
        mask={false}
        width={360}
        styles={{ body: { padding: '16px 24px' } }}
        style={{ position: 'absolute' }}
      >
        {selectedNode && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
            {selectedNode.technicalDefinition && (
              <div>
                <Text type="secondary" style={{ fontSize: 12, fontWeight: 700, textTransform: 'uppercase', marginBottom: 8, display: 'flex', alignItems: 'center', gap: 4 }}>
                  <ShareAltOutlined style={{ color: '#1677ff' }} /> Technical Definition
                </Text>
                <Paragraph style={{ margin: 0, fontSize: 14 }}>{selectedNode.technicalDefinition}</Paragraph>
              </div>
            )}

            {selectedNode.ideologicalValue && (
              <div>
                <Text type="secondary" style={{ fontSize: 12, fontWeight: 700, textTransform: 'uppercase', marginBottom: 8, display: 'flex', alignItems: 'center', gap: 4 }}>
                  <ShareAltOutlined style={{ color: '#f5222d' }} /> Ideological Value
                </Text>
                <Paragraph style={{ margin: 0, fontSize: 14 }}>{selectedNode.ideologicalValue}</Paragraph>
              </div>
            )}

            {(selectedNode.subTitle || selectedNode.category) && (
              <div>
                <Text type="secondary" style={{ fontSize: 12, fontWeight: 700, textTransform: 'uppercase', marginBottom: 8 }}>Meta</Text>
                {selectedNode.subTitle && <Paragraph style={{ margin: 0, fontSize: 14 }}>{selectedNode.subTitle}</Paragraph>}
                {selectedNode.category && <Text type="secondary" style={{ fontSize: 13, marginTop: 4, display: 'block' }}>Category: {selectedNode.category}</Text>}
              </div>
            )}

            <div>
              <Text type="secondary" style={{ fontSize: 12, fontWeight: 700, textTransform: 'uppercase', marginBottom: 8, display: 'block' }}>Relations</Text>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {selectedRelations.map(connection => {
                  const linkedId = connection.fromNodeId === selectedNode.id ? connection.toNodeId : connection.fromNodeId;
                  const linkedNode = nodes.find(node => node.id === linkedId);
                  if (!linkedNode) {
                    return null;
                  }
                  const isSynthetic = connection.id < 0;
                  const direction = connection.fromNodeId === selectedNode.id ? 'Outgoing' : 'Incoming';
                  return (
                    <div
                      key={connection.id}
                      style={{
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 8,
                        padding: '10px 12px',
                        border: '1px solid #f0f0f0',
                        borderRadius: 8,
                        background: '#fafafa',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <div style={{ width: 10, height: 10, borderRadius: '50%', background: getNodeColor(linkedNode.nodeType).border }} />
                        <Text style={{ fontSize: 14, flex: 1, cursor: 'pointer' }} ellipsis onClick={() => setSelectedNode(linkedNode)}>
                          {findNodeName(linkedId)}
                        </Text>
                        <Tag style={{ margin: 0 }}>{direction}</Tag>
                        {isSynthetic && <Tag color="blue" style={{ margin: 0 }}>Readonly</Tag>}
                      </div>
                      {canEditKnowledgeGraph && !isSynthetic ? (
                        <Space.Compact style={{ width: '100%' }}>
                          <Select
                            value={connection.relationType}
                            options={RELATION_TYPES.map(type => ({ label: type, value: type }))}
                            loading={editingRelationId === connection.id}
                            onChange={value => void handleUpdateRelation(connection, value)}
                            style={{ flex: 1 }}
                          />
                          <Popconfirm
                            title="Delete this relation?"
                            okText="Delete"
                            cancelText="Cancel"
                            okButtonProps={{ danger: true, loading: deletingRelationId === connection.id }}
                            onConfirm={() => void handleDeleteRelation(connection)}
                          >
                            <Button danger icon={<DeleteOutlined />} loading={deletingRelationId === connection.id} />
                          </Popconfirm>
                        </Space.Compact>
                      ) : (
                        <Tag color="default" style={{ alignSelf: 'flex-start', margin: 0, border: 'none', background: `${getRelationColor(connection.relationType)}20`, color: getRelationColor(connection.relationType) }}>
                          {connection.relationType}
                        </Tag>
                      )}
                      {connection.description && <Text type="secondary" style={{ fontSize: 12 }}>{connection.description}</Text>}
                    </div>
                  );
                })}
                {selectedRelations.length === 0 && (
                  <Text type="secondary" style={{ fontSize: 13 }}>No linked nodes</Text>
                )}
              </div>
            </div>

            <Divider style={{ margin: '8px 0' }} />

            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {canEditKnowledgeGraph && (!showAddRelation ? (
                <Button type="dashed" block icon={<LinkOutlined />} onClick={() => setShowAddRelation(true)}>
                  Add Relation
                </Button>
              ) : (
                <Form
                  form={relationForm}
                  layout="vertical"
                  onFinish={handleAddRelation}
                  autoComplete="off"
                >
                  <Text type="secondary" style={{ fontSize: 12, fontWeight: 700, marginBottom: 12, display: 'block' }}>New Relation</Text>
                  <Form.Item name="relationType" label="Relation Type" rules={[{ required: true }]} initialValue={RELATION_TYPES[0]}>
                    <Select options={RELATION_TYPES.map(type => ({ label: type, value: type }))} />
                  </Form.Item>
                  <Form.Item name="targetNodeId" label="Target Node" rules={[{ required: true }]}>
                    <Select
                      showSearch
                      placeholder="Select node..."
                      optionFilterProp="children"
                      filterOption={(input, option) =>
                        (option?.label ?? '').toString().toLowerCase().includes(input.toLowerCase())
                      }
                      options={nodes.filter(node => node.id !== selectedNode.id).map(node => ({
                        value: node.id,
                        label: node.name,
                      }))}
                    />
                  </Form.Item>
                  <Space style={{ width: '100%', justifyContent: 'flex-end', marginTop: 16 }}>
                    <Button onClick={() => setShowAddRelation(false)}>Cancel</Button>
                    <Button type="primary" htmlType="submit" loading={addingRelation}>Confirm</Button>
                  </Space>
                </Form>
              ))}

              {canEditKnowledgeGraph && selectedNode.nodeType === 'TECH' && (
                <Popconfirm
                  title="Delete this node?"
                  description="The node and its direct relations will be removed."
                  okText="Delete"
                  cancelText="Cancel"
                  okButtonProps={{ danger: true, loading: deletingNodeId === selectedNode.id }}
                  onConfirm={() => void handleDeleteSelectedNode()}
                >
                  <Button danger block icon={<DeleteOutlined />}>
                    Delete Node
                  </Button>
                </Popconfirm>
              )}
            </div>
          </div>
        )}
      </Drawer>

      <Modal
        title="Create Node"
        open={showCreateNode}
        onCancel={() => {
          setShowCreateNode(false);
          createNodeForm.resetFields();
        }}
        onOk={() => void handleCreateNode()}
        okText="Create"
        confirmLoading={creatingNode}
        destroyOnClose
      >
        <Form
          form={createNodeForm}
          layout="vertical"
          preserve={false}
          initialValues={{ nodeSize: 'MD' }}
        >
          <Form.Item name="name" label="Title" rules={[{ required: true, message: 'Please enter the node title.' }]}>
            <Input placeholder="Enter node title" maxLength={100} />
          </Form.Item>
          <Form.Item name="subject" label="Subject">
            <Input placeholder="Optional subject" maxLength={100} />
          </Form.Item>
          <Form.Item name="category" label="Category">
            <Input placeholder="Optional category" maxLength={100} />
          </Form.Item>
          <Form.Item name="nodeSize" label="Node Size" rules={[{ required: true }]}>
            <Select options={NODE_SIZE_OPTIONS} />
          </Form.Item>
          <Form.Item name="technicalDefinition" label="Technical Definition">
            <TextArea rows={3} placeholder="Optional technical summary" maxLength={1000} />
          </Form.Item>
          <Form.Item name="ideologicalValue" label="Ideological Value">
            <TextArea rows={3} placeholder="Optional ideological summary" maxLength={1000} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};
