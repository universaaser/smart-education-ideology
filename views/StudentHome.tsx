import React, { useState, useEffect, useCallback } from 'react';
import { View } from '../types';
import { dashboardApi, knowledgeApi, pathApi, LearningPathResult, ActivityInfo } from '../services/api';
import { useAuth } from '../contexts/AuthContext';

interface StudentHomeProps {
  /** 切换视图的回调（供跳转到知识图谱时使用） */
  onChangeView: (view: View, options?: { highlightNodeIds?: number[] }) => void;
}

/**
 * 学生端专属首页
 *
 * <p>
 * 与教师 Dashboard 完全独立设计，面向学生的学习场景：
 * - 学习统计概览（简洁数字卡片）
 * - 个性化学习路径推荐（基于知识图谱 DAG 推荐服务）
 * - 近期系统动态快览
 * - 快捷功能入口
 */
export const StudentHome: React.FC<StudentHomeProps> = ({ onChangeView }) => {
  const { currentUser } = useAuth();
  const [totalNodes, setTotalNodes] = useState<number>(0);
  const [activities, setActivities] = useState<ActivityInfo[]>([]);
  const [recommendedPath, setRecommendedPath] = useState<LearningPathResult | null>(null);
  const [loadingPath, setLoadingPath] = useState(false);
  const [loading, setLoading] = useState(true);

  /** 加载基础统计数据 */
  const loadStats = useCallback(async () => {
    try {
      const [graphData, acts] = await Promise.all([
        knowledgeApi.getGraph(),
        dashboardApi.getActivities(5),
      ]);
      setTotalNodes(graphData.nodes?.length ?? 0);
      setActivities(acts ?? []);
    } catch {
      // NOTE: 后端不可用时静默降级
    } finally {
      setLoading(false);
    }
  }, []);

  /** 加载个性化学习路径推荐 */
  const loadPathRecommendation = useCallback(async () => {
    if (!currentUser?.id) return;
    setLoadingPath(true);
    try {
      const result = await pathApi.getRecommendedPath(currentUser.id, [], [], 6);
      setRecommendedPath(result);
    } catch {
      // NOTE: 推荐失败时静默处理，不阻断页面渲染
    } finally {
      setLoadingPath(false);
    }
  }, [currentUser?.id]);

  useEffect(() => {
    loadStats();
    loadPathRecommendation();
  }, [loadStats, loadPathRecommendation]);

  /** 跳转到知识图谱并高亮推荐路径 */
  const handleViewPath = () => {
    if (recommendedPath?.nodeIds && recommendedPath.nodeIds.length > 0) {
      onChangeView(View.KNOWLEDGE_GRAPH, { highlightNodeIds: recommendedPath.nodeIds });
    } else {
      onChangeView(View.KNOWLEDGE_GRAPH);
    }
  };

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center bg-background-light">
        <div className="flex flex-col items-center gap-4">
          <div className="size-10 border-4 border-primary/20 border-t-primary rounded-full animate-spin"></div>
          <p className="text-sm text-slate-400">加载学习空间...</p>
        </div>
      </div>
    );
  }

  const greeting = (() => {
    const hour = new Date().getHours();
    if (hour < 12) return '早上好';
    if (hour < 18) return '下午好';
    return '晚上好';
  })();

  const displayName = currentUser?.realName || currentUser?.username || '同学';

  return (
    <div className="flex-1 overflow-y-auto bg-background-light">
      <div className="max-w-5xl mx-auto px-8 py-8 space-y-8">

        {/* 欢迎横幅 */}
        <div className="relative rounded-2xl overflow-hidden bg-gradient-to-br from-blue-600 via-blue-700 to-purple-700 p-8 text-white shadow-lg">
          {/* 背景装饰圆 */}
          <div className="absolute -top-8 -right-8 size-40 rounded-full bg-white/5"></div>
          <div className="absolute -bottom-4 -right-16 size-32 rounded-full bg-white/5"></div>

          <div className="relative z-10">
            <p className="text-blue-200 text-sm font-medium mb-1">{greeting}！</p>
            <h1 className="text-2xl font-display font-bold mb-2">欢迎回来，{displayName}</h1>
            <p className="text-blue-200 text-sm max-w-md">
              智教思政平台已为您准备了今日的学习路径和思政内容，继续探索吧 🚀
            </p>
          </div>

          {/* 快捷按钮 */}
          <div className="relative z-10 flex gap-3 mt-6">
            <button
              onClick={() => onChangeView(View.AI_ASSISTANT)}
              className="flex items-center gap-2 px-4 py-2 bg-white/20 hover:bg-white/30 rounded-lg text-sm font-medium transition-all backdrop-blur-sm"
            >
              <span className="material-symbols-outlined text-[16px]">smart_toy</span>
              AI 助教
            </button>
            <button
              onClick={() => onChangeView(View.KNOWLEDGE_GRAPH)}
              className="flex items-center gap-2 px-4 py-2 bg-white/20 hover:bg-white/30 rounded-lg text-sm font-medium transition-all backdrop-blur-sm"
            >
              <span className="material-symbols-outlined text-[16px]">hub</span>
              知识图谱
            </button>
            <button
              onClick={() => onChangeView(View.COURSE_LIBRARY)}
              className="flex items-center gap-2 px-4 py-2 bg-white/20 hover:bg-white/30 rounded-lg text-sm font-medium transition-all backdrop-blur-sm"
            >
              <span className="material-symbols-outlined text-[16px]">menu_book</span>
              课程资源
            </button>
          </div>
        </div>

        {/* 统计数字卡片 */}
        <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
          {[
            {
              label: '知识图谱节点',
              value: totalNodes,
              suffix: '个',
              icon: 'hub',
              color: 'blue',
              sub: '可供探索的知识点',
            },
            {
              label: '已推荐路径节点',
              value: recommendedPath?.nodeIds?.length ?? 0,
              suffix: '个',
              icon: 'route',
              color: 'purple',
              sub: '个性化学习推荐',
            },
            {
              label: '近期系统动态',
              value: activities.length,
              suffix: '条',
              icon: 'notifications',
              color: 'amber',
              sub: '资源与图谱更新',
            },
          ].map((card) => (
            <div key={card.label} className="bg-white rounded-xl border border-slate-100 shadow-sm p-5">
              <div className="flex justify-between items-start">
                <div>
                  <p className="text-xs text-slate-500 font-medium">{card.label}</p>
                  <div className="flex items-end gap-1 mt-1.5">
                    <span className="text-3xl font-display font-bold text-slate-900">{card.value}</span>
                    <span className="text-sm text-slate-400 mb-0.5">{card.suffix}</span>
                  </div>
                </div>
                <div className={`p-2 bg-${card.color}-50 rounded-lg`}>
                  <span className={`material-symbols-outlined text-${card.color}-500`}>{card.icon}</span>
                </div>
              </div>
              <p className="text-xs text-slate-400 mt-2">{card.sub}</p>
            </div>
          ))}
        </div>

        {/* 主内容区：推荐路径 + 近期动态 */}
        <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">

          {/* 个性化学习路径推荐（占 3 份） */}
          <div className="lg:col-span-3 bg-white rounded-xl border border-slate-100 shadow-sm p-6">
            <div className="flex justify-between items-center mb-5">
              <div>
                <h2 className="font-display font-bold text-slate-900 text-base">
                  个性化学习路径推荐
                </h2>
                <p className="text-xs text-slate-400 mt-0.5">基于知识图谱 DAG 结构智能生成</p>
              </div>
              <button
                onClick={loadPathRecommendation}
                disabled={loadingPath}
                className="p-1.5 rounded-lg hover:bg-slate-100 transition-colors disabled:opacity-50"
                title="重新生成推荐路径"
              >
                <span className={`material-symbols-outlined text-slate-400 text-[18px] ${loadingPath ? 'animate-spin' : ''}`}>
                  refresh
                </span>
              </button>
            </div>

            {loadingPath ? (
              <div className="flex items-center justify-center py-8">
                <div className="size-6 border-2 border-primary/20 border-t-primary rounded-full animate-spin"></div>
                <span className="text-sm text-slate-400 ml-3">正在生成推荐路径...</span>
              </div>
            ) : recommendedPath && recommendedPath.nodeNames.length > 0 ? (
              <>
                {/* 路径节点列表 */}
                <div className="space-y-2 mb-5">
                  {recommendedPath.nodeNames.map((name, idx) => (
                    <div
                      key={recommendedPath.nodeIds[idx] ?? idx}
                      className="flex items-center gap-3 p-3 rounded-lg bg-slate-50 hover:bg-blue-50 transition-colors group"
                    >
                      {/* 序号圆圈 */}
                      <div className="size-7 rounded-full bg-primary/10 text-primary text-xs font-bold flex items-center justify-center shrink-0 group-hover:bg-primary group-hover:text-white transition-colors">
                        {idx + 1}
                      </div>
                      <span className="text-sm text-slate-700 font-medium">{name}</span>
                      {idx < recommendedPath.nodeNames.length - 1 && (
                        <span className="material-symbols-outlined text-slate-300 text-[14px] ml-auto">arrow_forward</span>
                      )}
                    </div>
                  ))}
                </div>

                {/* 跳转按钮 */}
                <button
                  onClick={handleViewPath}
                  className="w-full flex items-center justify-center gap-2 py-3 bg-primary text-white rounded-xl text-sm font-medium hover:bg-blue-700 transition-colors shadow-sm"
                >
                  <span className="material-symbols-outlined text-[18px]">route</span>
                  在知识图谱中查看路径
                </button>
              </>
            ) : (
              <div className="flex flex-col items-center justify-center py-8 text-center">
                <span className="material-symbols-outlined text-slate-300 text-4xl mb-3">route</span>
                <p className="text-sm text-slate-500 font-medium">暂无推荐路径</p>
                <p className="text-xs text-slate-400 mt-1">知识图谱中暂无可推荐的节点</p>
                <button
                  onClick={() => onChangeView(View.KNOWLEDGE_GRAPH)}
                  className="mt-4 px-4 py-2 bg-slate-100 text-slate-600 rounded-lg text-sm hover:bg-slate-200 transition-colors"
                >
                  前往知识图谱探索
                </button>
              </div>
            )}
          </div>

          {/* 近期系统动态（占 2 份） */}
          <div className="lg:col-span-2 bg-white rounded-xl border border-slate-100 shadow-sm p-6">
            <h2 className="font-display font-bold text-slate-900 text-base mb-5">近期动态</h2>
            <div className="space-y-4">
              {activities.length > 0 ? activities.map((activity) => (
                <div key={activity.id} className="flex gap-3">
                  <div className="size-8 rounded-full bg-blue-100 flex items-center justify-center shrink-0">
                    <span className="material-symbols-outlined text-primary text-[14px]">
                      {activity.type === 'UPLOAD' ? 'upload_file' :
                        activity.type === 'AI_ANALYSIS' ? 'smart_toy' :
                          activity.type === 'GRAPH_UPDATE' ? 'hub' : 'info'}
                    </span>
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm text-slate-800 font-medium truncate">{activity.title}</p>
                    <p className="text-xs text-slate-400 mt-0.5 truncate">{activity.description}</p>
                    <p className="text-[10px] text-slate-300 mt-1">{activity.createdAt}</p>
                  </div>
                </div>
              )) : (
                <div className="flex items-center justify-center py-6 text-slate-300">
                  <p className="text-sm">暂无近期动态</p>
                </div>
              )}
            </div>

            {/* 底部操作 */}
            <div className="mt-5 pt-4 border-t border-slate-50">
              <button
                onClick={() => onChangeView(View.COURSE_LIBRARY)}
                className="w-full flex items-center justify-center gap-2 py-2.5 border border-slate-200 rounded-xl text-sm text-slate-500 hover:bg-slate-50 hover:text-primary hover:border-primary/30 transition-all"
              >
                <span className="material-symbols-outlined text-[16px]">menu_book</span>
                浏览课程资源库
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
