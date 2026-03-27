import React, { useState, useEffect, useRef, useCallback } from 'react';
import { knowledgeApi, knowledgeExcelApi, KnowledgeNodeInfo, KnowledgeRelationInfo, resourceApi, CrawlTaskStatusInfo } from '../services/api';

const NODE_SIZE = 60;

const NODE_TYPE_COLORS: Record<string, { bg: string; border: string; text: string; glow: string }> = {
  TECH: { bg: '#EFF6FF', border: '#3B82F6', text: '#1E40AF', glow: 'rgba(59,130,246,0.15)' },
  IDEO: { bg: '#FEF2F2', border: '#EF4444', text: '#991B1B', glow: 'rgba(239,68,68,0.15)' },
};

const RELATION_COLORS: Record<string, string> = {
  TECH_BASE: '#3B82F6',
  VALUE_SHOW: '#EF4444',
  THEORY_SUPPORT: '#8B5CF6',
  PRACTICE_APPLY: '#10B981',
};

const RELATION_TYPES = ['TECH_BASE', 'VALUE_SHOW', 'THEORY_SUPPORT', 'PRACTICE_APPLY'];

const getNodeColor = (nodeType: string) => NODE_TYPE_COLORS[nodeType] || NODE_TYPE_COLORS.TECH;
const getRelationColor = (relationType: string) => RELATION_COLORS[relationType] || '#94A3B8';

interface KnowledgeGraphProps {
  crawlStatus?: CrawlTaskStatusInfo | null;
  refreshCrawlStatus?: () => Promise<void> | void;
  /**
   * 需要高亮显示的知识节点 ID 列表（来自学习路径推荐跳转）
   * 高亮节点会以明亮的金色发光边框区分
   */
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
  const [newRelationType, setNewRelationType] = useState(RELATION_TYPES[0]);
  const [targetNodeSearch, setTargetNodeSearch] = useState('');
  const [selectedTargetNode, setSelectedTargetNode] = useState<KnowledgeNodeInfo | null>(null);
  const [addingRelation, setAddingRelation] = useState(false);
  const [actionPending, setActionPending] = useState(false);
  const [syncMessage, setSyncMessage] = useState('');
  // Excel 导入相关状态
  const [excelImporting, setExcelImporting] = useState(false);
  const [excelMessage, setExcelMessage] = useState('');
  const excelInputRef = useRef<HTMLInputElement>(null);

  const svgRef = useRef<SVGSVGElement>(null);
  const lastCrawlStateRef = useRef<string>('IDLE');

  const loadGraph = useCallback(async (showLoading = false) => {
    if (showLoading) {
      setLoading(true);
    }
    try {
      const data = await knowledgeApi.getGraph();
      setNodes(data.nodes || []);
      setConnections(data.relations || []);
    } catch {
      // NOTE: keep empty graph when backend unavailable
    } finally {
      if (showLoading) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    loadGraph(true);
  }, [loadGraph]);

  const crawlState = crawlStatus?.state ?? 'IDLE';
  const isCrawling = crawlState === 'RUNNING' || crawlState === 'STOP_REQUESTED';

  useEffect(() => {
    const prev = lastCrawlStateRef.current;
    const current = crawlState;
    const wasRunning = prev === 'RUNNING' || prev === 'STOP_REQUESTED';
    const ended = current === 'COMPLETED' || current === 'STOPPED' || current === 'FAILED';

    if (wasRunning && ended) {
      const result = crawlStatus?.lastResult;
      if (current === 'COMPLETED' && result) {
        setSyncMessage(`Update done: +${result.totalCreated}, dedup ${result.totalDeduplicated}, failed ${result.totalFailed}`);
      } else if (current === 'STOPPED') {
        setSyncMessage('');
      } else if (current === 'FAILED') {
        setSyncMessage('Update failed, please retry');
      }
      void loadGraph();
    }

    if (current === 'STOP_REQUESTED') {
      setSyncMessage('Stop requested, it will stop after current article');
    }

    lastCrawlStateRef.current = current;
  }, [crawlState, crawlStatus, loadGraph]);

  const handleSearch = async () => {
    if (!searchKeyword.trim()) {
      await loadGraph();
      return;
    }
    try {
      const results = await knowledgeApi.searchNodes(searchKeyword);
      if (results.length > 0) {
        const found = nodes.find(n => results.some(r => r.id === n.id));
        if (found) {
          setSelectedNode(found);
        }
      }
    } catch {
      // NOTE: silent failure
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
        setSyncMessage('Stop requested, it will stop after current article');
      } else {
        const status = await resourceApi.startCrawlUpdate();
        if (status.state === 'RUNNING' || status.state === 'STOP_REQUESTED') {
          setSyncMessage('Updating database from websites...');
        } else {
          setSyncMessage(status.message || 'Update task already running');
        }
      }
      await refreshCrawlStatus?.();
    } catch {
      setSyncMessage(isCrawling ? 'Stop request failed' : 'Start update failed');
    } finally {
      setActionPending(false);
    }
  };

  /** 下载 Excel 模板 */
  const handleDownloadTemplate = async () => {
    try {
      await knowledgeExcelApi.downloadTemplate();
    } catch {
      setExcelMessage('模板下载失败，请重试');
      setTimeout(() => setExcelMessage(''), 3000);
    }
  };

  /** 处理 Excel 文件导入 */
  const handleExcelImport = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    // 清空 input，允许再次选择同一文件
    e.target.value = '';

    setExcelImporting(true);
    setExcelMessage('正在导入 Excel...');
    try {
      const result = await knowledgeExcelApi.importFromExcel(file);
      setExcelMessage(`导入完成：创建 ${result.createdNodeCount} 个节点、${result.createdRelationCount} 条关系`);
      // 导入成功后刷新图谱
      await loadGraph();
      setTimeout(() => setExcelMessage(''), 5000);
    } catch {
      setExcelMessage('Excel 导入失败，请检查文件格式');
      setTimeout(() => setExcelMessage(''), 4000);
    } finally {
      setExcelImporting(false);
    }
  };

  const handleWheel = useCallback((e: React.WheelEvent) => {
    e.preventDefault();
    const delta = e.deltaY > 0 ? 0.9 : 1.1;
    setScale(prev => Math.min(Math.max(prev * delta, 0.3), 3));
  }, []);

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    if (e.target === svgRef.current || (e.target as Element).tagName === 'line') {
      setIsPanning(true);
      setPanStart({ x: e.clientX - offset.x, y: e.clientY - offset.y });
    }
  }, [offset]);

  const handleMouseMove = useCallback((e: React.MouseEvent) => {
    if (isPanning) {
      setOffset({ x: e.clientX - panStart.x, y: e.clientY - panStart.y });
    }

    if (draggingNodeId !== null) {
      const dx = (e.clientX - dragStart.x) / scale;
      const dy = (e.clientY - dragStart.y) / scale;
      setDragStart({ x: e.clientX, y: e.clientY });
      setNodes(prev =>
        prev.map(n =>
          n.id === draggingNodeId
            ? { ...n, positionX: n.positionX + dx, positionY: n.positionY + dy }
            : n
        )
      );
    }
  }, [dragStart, draggingNodeId, isPanning, panStart, scale]);

  const handleMouseUp = useCallback(() => {
    if (draggingNodeId !== null) {
      const node = nodes.find(n => n.id === draggingNodeId);
      if (node) {
        knowledgeApi.updateNodePosition(node.id, node.positionX, node.positionY).catch(() => { });
      }
    }
    setIsPanning(false);
    setDraggingNodeId(null);
  }, [draggingNodeId, nodes]);

  const handleNodeMouseDown = useCallback((e: React.MouseEvent, nodeId: number) => {
    e.stopPropagation();
    setDraggingNodeId(nodeId);
    setDragStart({ x: e.clientX, y: e.clientY });
  }, []);

  const getNodePosition = (nodeId: number) => {
    const node = nodes.find(n => n.id === nodeId);
    return node ? { x: node.positionX, y: node.positionY } : null;
  };

  const filteredTargetNodes = nodes.filter(n => {
    if (selectedNode && n.id === selectedNode.id) {
      return false;
    }
    if (!targetNodeSearch.trim()) {
      return true;
    }
    return n.name.toLowerCase().includes(targetNodeSearch.toLowerCase());
  });

  const handleAddRelation = async () => {
    if (!selectedNode || !selectedTargetNode || addingRelation) {
      return;
    }

    setAddingRelation(true);
    try {
      const newRelation = await knowledgeApi.createRelation(
        selectedNode.id,
        selectedTargetNode.id,
        newRelationType
      );
      setConnections(prev => [...prev, newRelation]);
      setShowAddRelation(false);
      setTargetNodeSearch('');
      setSelectedTargetNode(null);
    } catch {
      // NOTE: silent failure
    } finally {
      setAddingRelation(false);
    }
  };

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center bg-background-light">
        <div className="flex flex-col items-center gap-4">
          <div className="size-10 border-4 border-primary/20 border-t-primary rounded-full animate-spin"></div>
          <p className="text-sm text-slate-400">Loading graph...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-full overflow-hidden relative">
      <div className="absolute top-4 left-4 z-10 flex items-center gap-2">
        <div className="relative">
          <span className="material-symbols-outlined absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400 text-lg">search</span>
          <input
            type="text"
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            placeholder="Search node..."
            className="h-9 pl-9 pr-3 rounded-lg border border-slate-200 bg-white/90 backdrop-blur text-sm focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none w-48"
          />
        </div>

        <div className="flex items-center gap-1 bg-white/90 backdrop-blur rounded-lg border border-slate-200 overflow-hidden">
          <button onClick={() => setScale(s => Math.min(s * 1.2, 3))} className="p-1.5 hover:bg-slate-100 transition-colors">
            <span className="material-symbols-outlined text-sm text-slate-500">add</span>
          </button>
          <span className="text-xs text-slate-400 px-1">{Math.round(scale * 100)}%</span>
          <button onClick={() => setScale(s => Math.max(s * 0.8, 0.3))} className="p-1.5 hover:bg-slate-100 transition-colors">
            <span className="material-symbols-outlined text-sm text-slate-500">remove</span>
          </button>
        </div>

        <button
          onClick={handleManualUpdate}
          disabled={actionPending}
          className="h-9 px-3 rounded-lg border border-slate-200 bg-white/90 backdrop-blur text-sm text-slate-600 hover:bg-slate-50 disabled:opacity-60 disabled:cursor-not-allowed transition-colors flex items-center gap-1.5"
        >
          {actionPending ? (
            <>
              <span className="size-3 border-2 border-primary/30 border-t-primary rounded-full animate-spin"></span>
              Processing
            </>
          ) : isCrawling ? (
            <>
              <span className="material-symbols-outlined text-[16px]">stop_circle</span>
              {'\u505C\u6B62\u66F4\u65B0\u6570\u636E\u5E93'}
            </>
          ) : (
            <>
              <span className="material-symbols-outlined text-[16px]">sync</span>
              {'\u66F4\u65B0\u6570\u636E\u5E93'}
            </>
          )}
        </button>

        {syncMessage && (
          <div className="hidden lg:flex items-center h-9 px-3 rounded-lg border border-emerald-100 bg-emerald-50 text-[11px] text-emerald-700 max-w-[420px] truncate" title={syncMessage}>
            {syncMessage}
          </div>
        )}

        {/* Excel 导入状态提示 */}
        {excelMessage && (
          <div className="hidden lg:flex items-center h-9 px-3 rounded-lg border border-blue-100 bg-blue-50 text-[11px] text-blue-700 max-w-[320px] truncate" title={excelMessage}>
            {excelMessage}
          </div>
        )}

        {crawlStatus && isCrawling && (
          <div className="hidden xl:flex items-center h-9 px-3 rounded-lg border border-slate-200 bg-white/90 text-[11px] text-slate-600 max-w-[420px] truncate" title={crawlStatus.currentUrl || crawlStatus.message}>
            Status: {crawlState}{crawlStatus.currentSite ? ` | ${crawlStatus.currentSite}` : ''}
          </div>
        )}

        {/* Excel 导入导出按钮组 */}
        <div className="flex items-center gap-1 bg-white/90 backdrop-blur rounded-lg border border-slate-200 overflow-hidden">
          <button
            onClick={handleDownloadTemplate}
            className="h-9 px-3 text-[11px] text-slate-600 hover:bg-slate-50 transition-colors flex items-center gap-1.5"
            title="下载 Excel 模板"
          >
            <span className="material-symbols-outlined text-[15px] text-emerald-500">download</span>
            模板
          </button>
          <div className="w-px h-5 bg-slate-200"></div>
          <button
            onClick={() => excelInputRef.current?.click()}
            disabled={excelImporting}
            className="h-9 px-3 text-[11px] text-slate-600 hover:bg-slate-50 disabled:opacity-50 transition-colors flex items-center gap-1.5"
            title="从 Excel 导入知识图谱"
          >
            {excelImporting ? (
              <span className="size-3 border-2 border-primary/30 border-t-primary rounded-full animate-spin"></span>
            ) : (
              <span className="material-symbols-outlined text-[15px] text-blue-500">upload</span>
            )}
            导入
          </button>
          <input
            ref={excelInputRef}
            type="file"
            accept=".xlsx,.xls"
            onChange={handleExcelImport}
            className="hidden"
          />
        </div>

        <div className="flex items-center gap-3 bg-white/90 backdrop-blur rounded-lg border border-slate-200 px-3 py-1.5">
          <div className="flex items-center gap-1.5">
            <div className="w-3 h-3 rounded-sm" style={{ backgroundColor: NODE_TYPE_COLORS.TECH.border }}></div>
            <span className="text-[11px] text-slate-500">TECH</span>
          </div>
          <div className="flex items-center gap-1.5">
            <div className="w-3 h-3 rounded-sm" style={{ backgroundColor: NODE_TYPE_COLORS.IDEO.border }}></div>
            <span className="text-[11px] text-slate-500">IDEO</span>
          </div>
        </div>
      </div>

      <svg
        ref={svgRef}
        className="flex-1 bg-background-light cursor-grab active:cursor-grabbing"
        onWheel={handleWheel}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
      >
        <g transform={`translate(${offset.x}, ${offset.y}) scale(${scale})`}>
          {connections.map((conn) => {
            const from = getNodePosition(conn.fromNodeId);
            const to = getNodePosition(conn.toNodeId);
            if (!from || !to) {
              return null;
            }

            const lineColor = getRelationColor(conn.relationType);
            const isDashed = conn.lineStyle === 'DASHED';

            return (
              <g key={conn.id}>
                <line
                  x1={from.x}
                  y1={from.y}
                  x2={to.x}
                  y2={to.y}
                  stroke={lineColor}
                  strokeWidth={1.5}
                  strokeDasharray={isDashed ? '6,4' : undefined}
                  strokeOpacity={0.7}
                />
                <text
                  x={(from.x + to.x) / 2}
                  y={(from.y + to.y) / 2 - 6}
                  textAnchor="middle"
                  fill={lineColor}
                  fontSize={9}
                  fontWeight="500"
                  opacity={0.8}
                >
                  {conn.relationType}
                </text>
              </g>
            );
          })}

          {nodes.map((node) => {
            const color = getNodeColor(node.nodeType);
            const isSelected = selectedNode?.id === node.id;
            // 高亮节点（来自学习路径推荐）使用金色发光外框
            const isHighlighted = highlightNodeIds.includes(node.id);
            const size = node.nodeSize === 'LG' ? NODE_SIZE * 1.3 : node.nodeSize === 'SM' ? NODE_SIZE * 0.8 : NODE_SIZE;
            return (
              <g
                key={node.id}
                transform={`translate(${node.positionX - size / 2}, ${node.positionY - size / 2})`}
                onClick={() => setSelectedNode(node)}
                onMouseDown={(e) => handleNodeMouseDown(e, node.id)}
                className="cursor-pointer"
              >
                {/* 推荐路径高亮发光框 */}
                {isHighlighted && <rect x={-6} y={-6} width={size + 12} height={size + 12} rx={20} fill="rgba(251,191,36,0.25)" stroke="#FBBF24" strokeWidth={2} strokeDasharray="5,3" />}
                {isSelected && <rect x={-4} y={-4} width={size + 8} height={size + 8} rx={18} fill={color.glow} />}
                <rect
                  width={size}
                  height={size}
                  rx={14}
                  fill={color.bg}
                  stroke={isSelected ? color.border : color.border + '80'}
                  strokeWidth={isSelected ? 3 : 1.5}
                />
                {node.icon && (
                  <text
                    x={size / 2}
                    y={size / 2 - 4}
                    textAnchor="middle"
                    fill={color.text}
                    fontSize={16}
                    fontFamily="Material Symbols Outlined"
                  >
                    {node.icon}
                  </text>
                )}
                <text
                  x={size / 2}
                  y={node.icon ? size / 2 + 14 : size / 2 + 5}
                  textAnchor="middle"
                  fill={color.text}
                  fontSize={10}
                  fontWeight="600"
                >
                  {node.name?.length > 4 ? node.name.slice(0, 4) + '...' : node.name}
                </text>
              </g>
            );
          })}
        </g>

        {nodes.length === 0 && (
          <text x="50%" y="50%" textAnchor="middle" fill="#94A3B8" fontSize={14}>
            No graph data
          </text>
        )}
      </svg>

      {selectedNode && (
        <div className="absolute right-0 top-0 h-full w-80 bg-white border-l border-slate-200 shadow-xl overflow-y-auto z-20">
          <div className="p-6 space-y-5">
            <div className="flex justify-between items-start">
              <div>
                <h3 className="text-lg font-display font-bold text-slate-900">{selectedNode.name}</h3>
                <div className="flex items-center gap-2 mt-2">
                  <span
                    className="inline-block px-2 py-0.5 rounded text-[10px] font-bold"
                    style={{ backgroundColor: getNodeColor(selectedNode.nodeType).bg, color: getNodeColor(selectedNode.nodeType).text }}
                  >
                    {selectedNode.nodeType === 'TECH' ? 'TECH' : 'IDEO'}
                  </span>
                  {selectedNode.subject && <span className="text-[10px] text-slate-400">{selectedNode.subject}</span>}
                </div>
              </div>
              <button onClick={() => setSelectedNode(null)} className="text-slate-300 hover:text-slate-600">
                <span className="material-symbols-outlined">close</span>
              </button>
            </div>

            {selectedNode.technicalDefinition && (
              <div>
                <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-2 flex items-center gap-1">
                  <span className="material-symbols-outlined text-[14px] text-blue-500">description</span>
                  Technical Definition
                </h4>
                <p className="text-sm text-slate-600 leading-relaxed">{selectedNode.technicalDefinition}</p>
              </div>
            )}

            {selectedNode.ideologicalValue && (
              <div>
                <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-2 flex items-center gap-1">
                  <span className="material-symbols-outlined text-[14px] text-red-500">psychology</span>
                  Ideological Value
                </h4>
                <p className="text-sm text-slate-600 leading-relaxed">{selectedNode.ideologicalValue}</p>
              </div>
            )}

            {(selectedNode.subTitle || selectedNode.category) && (
              <div>
                <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-2">Meta</h4>
                {selectedNode.subTitle && <p className="text-sm text-slate-600">{selectedNode.subTitle}</p>}
                {selectedNode.category && <p className="text-xs text-slate-400 mt-1">Category: {selectedNode.category}</p>}
              </div>
            )}

            <div>
              <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-2">Linked Nodes</h4>
              <div className="space-y-2">
                {connections
                  .filter(c => c.fromNodeId === selectedNode.id || c.toNodeId === selectedNode.id)
                  .map(c => {
                    const linkedId = c.fromNodeId === selectedNode.id ? c.toNodeId : c.fromNodeId;
                    const linkedNode = nodes.find(n => n.id === linkedId);
                    if (!linkedNode) {
                      return null;
                    }
                    return (
                      <button
                        key={c.id}
                        onClick={() => setSelectedNode(linkedNode)}
                        className="w-full text-left flex items-center gap-3 p-2 rounded-lg hover:bg-slate-50 transition-colors"
                      >
                        <div className="size-3 rounded-full shrink-0" style={{ backgroundColor: getNodeColor(linkedNode.nodeType).border }}></div>
                        <span className="text-sm text-slate-700">{linkedNode.name}</span>
                        <span className="text-[10px] ml-auto px-1.5 py-0.5 rounded" style={{ color: getRelationColor(c.relationType), backgroundColor: getRelationColor(c.relationType) + '15' }}>
                          {c.relationType}
                        </span>
                      </button>
                    );
                  })}
                {connections.filter(c => c.fromNodeId === selectedNode.id || c.toNodeId === selectedNode.id).length === 0 && (
                  <p className="text-xs text-slate-400">No linked nodes</p>
                )}
              </div>
            </div>

            <div className="border-t border-slate-100 pt-4">
              {!showAddRelation ? (
                <button
                  onClick={() => setShowAddRelation(true)}
                  className="w-full flex items-center justify-center gap-2 px-4 py-2.5 border border-dashed border-slate-300 rounded-xl text-sm text-slate-500 hover:bg-slate-50 hover:border-primary hover:text-primary transition-all"
                >
                  <span className="material-symbols-outlined text-[18px]">add_link</span>
                  Add Relation
                </button>
              ) : (
                <div className="space-y-3">
                  <h4 className="text-xs font-bold text-slate-500">New Relation</h4>

                  <div>
                    <label className="text-[11px] text-slate-400 mb-1 block">Relation Type</label>
                    <select
                      value={newRelationType}
                      onChange={(e) => setNewRelationType(e.target.value)}
                      className="w-full h-9 px-3 rounded-lg border border-slate-200 bg-white text-sm focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none"
                    >
                      {RELATION_TYPES.map(t => (
                        <option key={t} value={t}>{t}</option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="text-[11px] text-slate-400 mb-1 block">Target Node</label>
                    <input
                      type="text"
                      value={targetNodeSearch}
                      onChange={(e) => { setTargetNodeSearch(e.target.value); setSelectedTargetNode(null); }}
                      placeholder="Search node..."
                      className="w-full h-9 px-3 rounded-lg border border-slate-200 bg-white text-sm focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none"
                    />
                    {targetNodeSearch && !selectedTargetNode && (
                      <div className="mt-1 max-h-32 overflow-y-auto border border-slate-200 rounded-lg bg-white">
                        {filteredTargetNodes.slice(0, 8).map(n => (
                          <button
                            key={n.id}
                            onClick={() => { setSelectedTargetNode(n); setTargetNodeSearch(n.name); }}
                            className="w-full text-left px-3 py-2 text-sm hover:bg-slate-50 flex items-center gap-2"
                          >
                            <div className="size-2 rounded-full" style={{ backgroundColor: getNodeColor(n.nodeType).border }}></div>
                            {n.name}
                          </button>
                        ))}
                        {filteredTargetNodes.length === 0 && <p className="px-3 py-2 text-xs text-slate-400">No match</p>}
                      </div>
                    )}
                    {selectedTargetNode && (
                      <div className="mt-1 flex items-center gap-2 px-2 py-1 bg-primary/5 rounded-lg">
                        <div className="size-2 rounded-full" style={{ backgroundColor: getNodeColor(selectedTargetNode.nodeType).border }}></div>
                        <span className="text-sm text-slate-700">{selectedTargetNode.name}</span>
                        <button onClick={() => { setSelectedTargetNode(null); setTargetNodeSearch(''); }} className="ml-auto text-slate-400 hover:text-slate-600">
                          <span className="material-symbols-outlined text-[14px]">close</span>
                        </button>
                      </div>
                    )}
                  </div>

                  <div className="flex gap-2">
                    <button
                      onClick={() => { setShowAddRelation(false); setTargetNodeSearch(''); setSelectedTargetNode(null); }}
                      className="flex-1 py-2 text-sm text-slate-500 border border-slate-200 rounded-lg hover:bg-slate-50 transition-colors"
                    >
                      Cancel
                    </button>
                    <button
                      onClick={handleAddRelation}
                      disabled={!selectedTargetNode || addingRelation}
                      className="flex-1 py-2 text-sm text-white bg-primary rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                    >
                      {addingRelation ? 'Adding...' : 'Confirm'}
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

