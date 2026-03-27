import React, { useState, useEffect } from 'react';
import { View } from '../types';
import { dashboardApi, CourseInfo, ActivityInfo, TrendItem } from '../services/api';

interface DashboardProps {
  onChangeView: (view: View) => void;
}

/** 统计卡片的图标和颜色映射 */
const STAT_CONFIG: Record<string, { icon: string; color: string }> = {
  ideologyRate: { icon: 'psychology', color: 'blue' },
  ideologyCount: { icon: 'auto_awesome', color: 'red' },
  studentActivity: { icon: 'forum', color: 'purple' },
  alertCount: { icon: 'warning', color: 'orange' },
};

export const Dashboard: React.FC<DashboardProps> = ({ onChangeView }) => {
  const [stats, setStats] = useState<Record<string, unknown>>({});
  const [courses, setCourses] = useState<CourseInfo[]>([]);
  const [activities, setActivities] = useState<ActivityInfo[]>([]);
  const [trendData, setTrendData] = useState<TrendItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadData = async () => {
      try {
        const [statsData, coursesData, activitiesData, trendResult] = await Promise.all([
          dashboardApi.getStats(),
          dashboardApi.getCourses(),
          dashboardApi.getActivities(4),
          dashboardApi.getTrend(),
        ]);
        setStats(statsData || {});
        setCourses(coursesData || []);
        setActivities(activitiesData || []);
        setTrendData(trendResult || []);
      } catch {
        // NOTE: 后端未连接时保持空状态，页面照常渲染
      } finally {
        setLoading(false);
      }
    };
    loadData();
  }, []);

  /** 根据活动类型返回对应的显示样式 */
  const getActivityStyle = (type: string) => {
    switch (type) {
      case 'AI_ANALYSIS': return { icon: 'smart_toy', bg: 'blue-100', color: 'primary' };
      case 'UPLOAD': return { icon: 'upload_file', bg: 'emerald-100', color: 'emerald-600' };
      case 'ALERT': return { icon: 'priority_high', bg: 'red-100', color: 'accent-red' };
      case 'KNOWLEDGE_UPDATE': return { icon: 'share', bg: 'purple-100', color: 'purple-600' };
      case 'GRAPH_UPDATE': return { icon: 'share', bg: 'purple-100', color: 'purple-600' };
      default: return { icon: 'info', bg: 'slate-100', color: 'slate-500' };
    }
  };

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center bg-background-light">
        <div className="flex flex-col items-center gap-4">
          <div className="size-10 border-4 border-primary/20 border-t-primary rounded-full animate-spin"></div>
          <p className="text-sm text-slate-400">加载仪表盘数据...</p>
        </div>
      </div>
    );
  }

  interface StatCard {
    label: string;
    value: string;
    icon: string;
    color: string;
    change: string;
    sub: string;
    trend: 'up' | 'neutral' | 'down';
    onClick?: () => void;
  }

  // 从后端返回的统计数据中提取各项指标
  const statCards: StatCard[] = [
    {
      label: '思政融入率',
      value: stats.ideologyRate != null ? `${stats.ideologyRate}%` : '--',
      ...STAT_CONFIG.ideologyRate,
      change: '',
      sub: '知识点思政映射',
      trend: 'up',
    },
    {
      label: '思政元素挖掘数',
      value: stats.ideologyCount != null ? String(stats.ideologyCount) : '--',
      ...STAT_CONFIG.ideologyCount,
      change: '',
      sub: '已关联知识点',
      trend: 'up',
    },
    {
      label: '学生互动活跃度',
      value: stats.studentActivity != null ? String(stats.studentActivity) : '--',
      ...STAT_CONFIG.studentActivity,
      change: '',
      sub: '学习行为记录',
      trend: 'up',
    },
    {
      label: '待处理预警',
      value: stats.alertCount != null ? String(stats.alertCount) : '0',
      ...STAT_CONFIG.alertCount,
      change: '需关注',
      sub: '课程内容审核',
      trend: 'neutral',
      onClick: () => onChangeView(View.KNOWLEDGE_GRAPH),
    },
  ];

  // 计算折线图的最大值，用于归一化
  const maxTrend = Math.max(...trendData.map(t => t.value), 1);

  /** 生成折线图 SVG 路径 */
  const chartWidth = 600;
  const chartHeight = 250;
  const chartPadding = { top: 20, right: 20, bottom: 40, left: 50 };
  const innerWidth = chartWidth - chartPadding.left - chartPadding.right;
  const innerHeight = chartHeight - chartPadding.top - chartPadding.bottom;

  const getPoint = (index: number, value: number) => {
    const x = chartPadding.left + (trendData.length > 1 ? (index / (trendData.length - 1)) * innerWidth : innerWidth / 2);
    const y = chartPadding.top + innerHeight - (value / Math.max(maxTrend, 100)) * innerHeight;
    return { x, y };
  };

  const linePath = trendData.length > 0
    ? trendData.map((item, i) => {
      const { x, y } = getPoint(i, item.value);
      return `${i === 0 ? 'M' : 'L'} ${x} ${y}`;
    }).join(' ')
    : '';

  // 渐变填充区域路径
  const areaPath = trendData.length > 0
    ? linePath +
    ` L ${getPoint(trendData.length - 1, 0).x} ${chartPadding.top + innerHeight}` +
    ` L ${getPoint(0, 0).x} ${chartPadding.top + innerHeight} Z`
    : '';

  return (
    <div className="flex-1 overflow-y-auto p-8 scroll-smooth bg-background-light">
      <div className="max-w-7xl mx-auto space-y-8">
        {/* 欢迎区 */}
        <div className="flex flex-col md:flex-row justify-between items-start md:items-end gap-4">
          <div>
            <h2 className="text-2xl font-display font-bold text-slate-900">欢迎回来 👋</h2>
            <p className="text-slate-500 mt-1">这里是今日的教学概览与思政融合分析数据。</p>
          </div>
          <div className="flex gap-3">
            <button className="flex items-center gap-2 px-4 py-2 bg-white border border-slate-200 rounded-lg text-sm font-medium hover:bg-slate-50 transition-colors">
              <span className="material-symbols-outlined text-[18px]">auto_awesome</span>
              生成总结
            </button>
          </div>
        </div>

        {/* 统计卡片 */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          {statCards.map((stat, idx) => (
            <div
              key={idx}
              onClick={stat.onClick}
              className={`bg-white p-6 rounded-xl border border-slate-100 shadow-sm relative overflow-hidden group hover:shadow-md transition-all ${stat.onClick ? 'cursor-pointer hover:border-orange-300 hover:bg-orange-50/10' : ''}`}
            >
              <div className="flex justify-between items-start mb-4">
                <div>
                  <p className="text-sm font-medium text-slate-500">{stat.label}</p>
                  <h3 className="text-3xl font-display font-bold text-slate-900 mt-1">{stat.value}</h3>
                </div>
                <div className={`p-2 bg-${stat.color}-50 rounded-lg text-${stat.color === 'blue' ? 'primary' : stat.color + '-500'}`}>
                  <span className="material-symbols-outlined">{stat.icon}</span>
                </div>
              </div>
              <div className="flex items-center gap-2 text-sm">
                <span className={`${stat.trend === 'neutral' ? 'text-orange-500' : 'text-emerald-600'} font-medium flex items-center`}>
                  {stat.trend === 'up' && <span className="material-symbols-outlined text-[16px]">trending_up</span>}
                  {stat.change}
                </span>
                <span className="text-slate-400">{stat.sub}</span>
              </div>
              {stat.onClick && (
                <div className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity">
                  <span className="material-symbols-outlined text-slate-300 text-sm">open_in_new</span>
                </div>
              )}
            </div>
          ))}
        </div>

        {/* 趋势图 + 动态 */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* 折线图 */}
          <div className="lg:col-span-2 bg-white rounded-xl border border-slate-100 shadow-sm p-6 flex flex-col">
            <div className="flex justify-between items-center mb-6">
              <div>
                <h3 className="font-display font-bold text-lg text-slate-900">思政融入趋势分析</h3>
                <p className="text-sm text-slate-500">已融入思政元素的知识点百分比变化</p>
              </div>
            </div>
            <div className="flex-1 min-h-[300px] w-full">
              {trendData.length > 0 ? (
                <svg viewBox={`0 0 ${chartWidth} ${chartHeight}`} className="w-full h-full">
                  {/* Y轴刻度线 + 标签 */}
                  {[0, 25, 50, 75, 100].map(tick => {
                    const y = chartPadding.top + innerHeight - (tick / 100) * innerHeight;
                    return (
                      <g key={tick}>
                        <line
                          x1={chartPadding.left} y1={y}
                          x2={chartWidth - chartPadding.right} y2={y}
                          stroke="#F1F5F9" strokeWidth={1}
                        />
                        <text x={chartPadding.left - 8} y={y + 4} textAnchor="end" fill="#94A3B8" fontSize={11}>
                          {tick}%
                        </text>
                      </g>
                    );
                  })}

                  {/* 渐变填充区域 */}
                  <defs>
                    <linearGradient id="areaGradient" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#3B82F6" stopOpacity="0.15" />
                      <stop offset="100%" stopColor="#3B82F6" stopOpacity="0" />
                    </linearGradient>
                  </defs>
                  <path d={areaPath} fill="url(#areaGradient)" />

                  {/* 折线 */}
                  <path
                    d={linePath}
                    fill="none"
                    stroke="#3B82F6"
                    strokeWidth={2.5}
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />

                  {/* 数据点 + X轴标签 */}
                  {trendData.map((item, i) => {
                    const { x, y } = getPoint(i, item.value);
                    return (
                      <g key={i}>
                        <circle cx={x} cy={y} r={4} fill="#3B82F6" stroke="white" strokeWidth={2} />
                        <text
                          x={x} y={chartHeight - 10}
                          textAnchor="middle" fill="#94A3B8" fontSize={11}
                        >
                          {item.week}
                        </text>
                        {/* 悬浮数值 */}
                        <text x={x} y={y - 12} textAnchor="middle" fill="#3B82F6" fontSize={11} fontWeight="600">
                          {item.value}%
                        </text>
                      </g>
                    );
                  })}
                </svg>
              ) : (
                <div className="flex items-center justify-center w-full h-full text-slate-300">
                  <p className="text-sm">暂无趋势数据</p>
                </div>
              )}
            </div>
          </div>

          {/* 动态列表 */}
          <div className="bg-white rounded-xl border border-slate-100 shadow-sm p-6 flex flex-col h-full">
            <div className="flex justify-between items-center mb-6">
              <h3 className="font-display font-bold text-lg text-slate-900">最新动态</h3>
            </div>
            <div className="flex flex-col gap-4 flex-1 overflow-y-auto pr-1">
              {activities.length > 0 ? activities.map((item) => {
                const style = getActivityStyle(item.type);
                return (
                  <div key={item.id} className="flex gap-3 items-start pb-4 border-b border-slate-50 last:border-0">
                    <div className={`w-8 h-8 rounded-full bg-${style.bg} flex items-center justify-center text-${style.color} flex-shrink-0`}>
                      <span className="material-symbols-outlined text-[16px]">{style.icon}</span>
                    </div>
                    <div>
                      <p className="text-sm text-slate-800 font-medium">{item.title}</p>
                      <p className="text-xs text-slate-500 mt-0.5">{item.description}</p>
                      <p className="text-[10px] text-slate-400 mt-2">{item.createdAt}</p>
                    </div>
                  </div>
                );
              }) : (
                <div className="flex-1 flex items-center justify-center text-slate-300">
                  <p className="text-sm">暂无动态</p>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* 课程列表 */}
        <div className="bg-white rounded-xl border border-slate-100 shadow-sm p-6 mb-8">
          <div className="flex justify-between items-center mb-6">
            <h3 className="font-display font-bold text-lg text-slate-900">我的课程</h3>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="text-xs text-slate-400 border-b border-slate-100">
                  <th className="py-3 px-2 font-medium">课程名称</th>
                  <th className="py-3 px-2 font-medium">进度</th>
                  <th className="py-3 px-2 font-medium">思政融合度</th>
                  <th className="py-3 px-2 font-medium text-right">操作</th>
                </tr>
              </thead>
              <tbody className="text-sm">
                {courses.length > 0 ? courses.map((course) => (
                  <tr key={course.id} className="border-b border-slate-50 hover:bg-slate-50 transition-colors">
                    <td className="py-3 px-2 font-medium text-slate-800">
                      <div className="flex items-center gap-3">
                        <div className={`w-8 h-8 rounded bg-${course.gradeColor}-100 flex items-center justify-center text-${course.gradeColor}-600 font-bold text-xs`}>
                          {course.name?.charAt(0) || '课'}
                        </div>
                        {course.name}
                      </div>
                    </td>
                    <td className="py-3 px-2 text-slate-500">
                      <div className="flex items-center gap-2">
                        <div className="w-16 h-1.5 bg-slate-100 rounded-full overflow-hidden">
                          <div className="h-full bg-primary" style={{ width: `${course.progress}%` }}></div>
                        </div>
                        <span className="text-xs">{course.progress}%</span>
                      </div>
                    </td>
                    <td className="py-3 px-2">
                      <span className={`px-2 py-1 rounded bg-${course.gradeColor}-100 text-${course.gradeColor}-700 text-xs font-medium`}>
                        {course.gradeLabel}
                      </span>
                    </td>
                    <td className="py-3 px-2 text-right">
                      <button className="text-slate-400 hover:text-primary transition-colors">
                        <span className="material-symbols-outlined text-[18px]">edit</span>
                      </button>
                    </td>
                  </tr>
                )) : (
                  <tr>
                    <td colSpan={4} className="py-12 text-center text-slate-400">暂无课程数据</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
};