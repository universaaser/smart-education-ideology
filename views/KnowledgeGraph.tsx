import React, { useCallback, useEffect, useRef, useState } from 'react';
import { knowledgeApi, knowledgeExcelApi, KnowledgeNodeInfo, KnowledgeRelationInfo, resourceApi, CrawlTaskStatusInfo } from '../services/api';
import {
  Input, Button, Drawer, Space, Typography, Tag, Select,
  Spin, message, Tooltip, Divider, Form
} from 'antd';
import {
  SearchOutlined, PlusOutlined, MinusOutlined, SyncOutlined,
  StopOutlined, DownloadOutlined, UploadOutlined,
  LinkOutlined, ShareAltOutlined
} from '@ant-design/icons';

const { Text, Title, Paragraph } = Typography;

const NODE_SIZE = 60;

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

const getNodeColor = (nodeType: string) => NODE_TYPE_COLORS[nodeType] || NODE_TYPE_COLORS.TECH;
const getRelationColor = (relationType: string) => RELATION_COLORS[relationType] || '#8c8c8c';

interface KnowledgeGraphProps {
  crawlStatus?: CrawlTaskStatusInfo | null;
  refreshCrawlStatus?: () => Promise<void> | void;
  highlightNodeIds?: number[];
}

export const KnowledgeGraph: React.FC<KnowledgeGraphProps> = ({ crawlStatus, refreshCrawlStatus, highlightNodeIds = [] }) => {
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
  const [addingRelation, setAddingRelation] = useState(false);
  const [actionPending, setActionPending] = useState(false);
  const [excelImporting, setExcelImporting] = useState(false);
  const excelInputRef = useRef<HTMLInputElement>(null);

  const svgRef = useRef<SVGSVGElement>(null);
  const lastCrawlStateRef = useRef<string>('IDLE');

  const loadGraph = useCallback(async (showLoading = false) => {
    if (showLoading) setLoading(true);
    try {
      const data = await knowledgeApi.getGraph();
      setNodes(data.nodes || []);
      setConnections(data.relations || []);
    } catch {
      // Keep an empty graph when the backend is unavailable.
    } finally {
      if (showLoading) setLoading(false);
    }
  }, []);

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
      if (results.length > 0) {
        const found = nodes.find(node => results.some(result => result.id === node.id));
        if (found) {
          setSelectedNode(found);
          setOffset({
            x: -found.positionX * scale + (svgRef.current?.clientWidth || 0) / 2,
            y: -found.positionY * scale + (svgRef.current?.clientHeight || 0) / 2
          });
        } else {
          message.warning('No matching node was found.');
        }
      } else {
        message.warning('No matching node was found.');
      }
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
    } finally {
      setAddingRelation(false);
    }
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
              color: '#595959'
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
          cursor: isPanning ? 'grabbing' : 'grab'
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
              <g key={connection.id}>
                <line
                  x1={from.x} y1={from.y} x2={to.x} y2={to.y}
                  stroke={lineColor} strokeWidth={1.5}
                  strokeDasharray={isDashed ? '6,4' : undefined}
                  strokeOpacity={0.7}
                />
                <text
                  x={(from.x + to.x) / 2} y={(from.y + to.y) / 2 - 6}
                  textAnchor="middle" fill={lineColor} fontSize={10} fontWeight="500" opacity={0.8}
                >
                  {connection.relationType}
                </text>
              </g>
            );
          })}

          {nodes.map(node => {
            const color = getNodeColor(node.nodeType);
            const isSelected = selectedNode?.id === node.id;
            const isHighlighted = highlightNodeIds.includes(node.id);
            const size = node.nodeSize === 'LG' ? NODE_SIZE * 1.3 : node.nodeSize === 'SM' ? NODE_SIZE * 0.8 : NODE_SIZE;

            return (
              <g
                key={node.id}
                transform={`translate(${node.positionX - size / 2}, ${node.positionY - size / 2})`}
                onClick={event => { event.stopPropagation(); setSelectedNode(node); }}
                onMouseDown={event => handleNodeMouseDown(event, node.id)}
                style={{ cursor: 'pointer' }}
              >
                {isHighlighted && <rect x={-6} y={-6} width={size + 12} height={size + 12} rx={20} fill="rgba(250,173,20,0.15)" stroke="#faad14" strokeWidth={2} strokeDasharray="5,3" />}
                {isSelected && <rect x={-4} y={-4} width={size + 8} height={size + 8} rx={18} fill={color.glow} />}
                <rect
                  width={size} height={size} rx={14}
                  fill={color.bg}
                  stroke={color.border}
                  strokeWidth={isSelected ? 3 : 1.5}
                />
                {node.icon && (
                  <text
                    x={size / 2} y={size / 2 - 4} textAnchor="middle" fill={color.text}
                    fontSize={20} fontFamily="Material Symbols Outlined" style={{ userSelect: 'none' }}
                  >
                    {node.icon}
                  </text>
                )}
                <text
                  x={size / 2} y={node.icon ? size / 2 + 14 : size / 2 + 5}
                  textAnchor="middle" fill={color.text} fontSize={11} fontWeight="600"
                  style={{ userSelect: 'none' }}
                >
                  {node.name?.length > 4 ? `${node.name.slice(0, 4)}...` : node.name}
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
        title={
          <div>
            <Title level={4} style={{ margin: 0, paddingRight: 24, fontSize: 18 }}>{selectedNode?.name}</Title>
            <Space style={{ marginTop: 8 }}>
              <Tag color={selectedNode?.nodeType === 'IDEO' ? 'error' : 'processing'}>
                {selectedNode?.nodeType === 'TECH' ? 'TECH' : 'IDEO'}
              </Tag>
              {selectedNode?.subject && <Text type="secondary" style={{ fontSize: 12 }}>{selectedNode.subject}</Text>}
            </Space>
          </div>
        }
        placement="right"
        closable
        onClose={() => { setSelectedNode(null); setShowAddRelation(false); }}
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
              <Text type="secondary" style={{ fontSize: 12, fontWeight: 700, textTransform: 'uppercase', marginBottom: 8, display: 'block' }}>Linked Nodes</Text>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {connections
                  .filter(connection => connection.fromNodeId === selectedNode.id || connection.toNodeId === selectedNode.id)
                  .map(connection => {
                    const linkedId = connection.fromNodeId === selectedNode.id ? connection.toNodeId : connection.fromNodeId;
                    const linkedNode = nodes.find(node => node.id === linkedId);
                    if (!linkedNode) {
                      return null;
                    }
                    return (
                      <div
                        key={connection.id}
                        onClick={() => setSelectedNode(linkedNode)}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 8,
                          padding: '8px 12px',
                          border: '1px solid #f0f0f0',
                          borderRadius: 8,
                          cursor: 'pointer',
                          background: '#fafafa',
                          transition: 'all 0.2s'
                        }}
                      >
                        <div style={{ width: 10, height: 10, borderRadius: '50%', background: getNodeColor(linkedNode.nodeType).border }} />
                        <Text style={{ fontSize: 14, flex: 1 }} ellipsis>{linkedNode.name}</Text>
                        <Tag color="default" style={{ margin: 0, border: 'none', background: `${getRelationColor(connection.relationType)}20`, color: getRelationColor(connection.relationType) }}>
                          {connection.relationType}
                        </Tag>
                      </div>
                    );
                  })}
                {connections.filter(connection => connection.fromNodeId === selectedNode.id || connection.toNodeId === selectedNode.id).length === 0 && (
                  <Text type="secondary" style={{ fontSize: 13 }}>No linked nodes</Text>
                )}
              </div>
            </div>

            <Divider style={{ margin: '8px 0' }} />

            {!showAddRelation ? (
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
                  <Select
                    options={RELATION_TYPES.map(type => ({ label: type, value: type }))}
                  />
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
                      label: node.name
                    }))}
                  />
                </Form.Item>
                <Space style={{ width: '100%', justifyContent: 'flex-end', marginTop: 16 }}>
                  <Button onClick={() => setShowAddRelation(false)}>Cancel</Button>
                  <Button type="primary" htmlType="submit" loading={addingRelation}>Confirm</Button>
                </Space>
              </Form>
            )}
          </div>
        )}
      </Drawer>
    </div>
  );
};
