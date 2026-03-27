import React, { useState, useEffect } from 'react';
import {
  dashboardApi,
  courseApi,
  CourseInfo,
  KnowledgeNodeInfo,
} from '../services/api';

/**
 * 课程库
 *
 * 展示课程列表，展开后显示关联的知识点及其思政映射。
 * 顶部提供"新建课程"功能。
 */
export const ResourceLibrary: React.FC = () => {
  const [courses, setCourses] = useState<CourseInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [knowledgePoints, setKnowledgePoints] = useState<KnowledgeNodeInfo[]>([]);
  const [kpLoading, setKpLoading] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState('');

  // 新建课程弹窗状态
  const [showNewCourse, setShowNewCourse] = useState(false);
  const [newCourseForm, setNewCourseForm] = useState({
    name: '',
    code: '',
    description: '',
    semester: '',
  });
  const [creating, setCreating] = useState(false);

  /** 加载课程列表 */
  useEffect(() => {
    const loadCourses = async () => {
      try {
        const data = await dashboardApi.getCourses();
        setCourses(data);
      } catch {
        // NOTE: 后端未连接保持空列表
      } finally {
        setLoading(false);
      }
    };
    loadCourses();
  }, []);

  /** 展开课程时加载关联知识点 */
  const handleExpand = async (courseId: number) => {
    if (expandedId === courseId) {
      setExpandedId(null);
      return;
    }
    setExpandedId(courseId);
    setKpLoading(true);
    try {
      const points = await courseApi.getKnowledgePoints(courseId);
      setKnowledgePoints(points);
    } catch {
      setKnowledgePoints([]);
    } finally {
      setKpLoading(false);
    }
  };

  /** 创建新课程 */
  const handleCreateCourse = async () => {
    if (!newCourseForm.name.trim() || creating) return;
    setCreating(true);
    try {
      const newCourse = await courseApi.create(newCourseForm);
      // 将新课程添加到列表
      setCourses(prev => [{
        ...newCourse,
        gradeColor: newCourse.gradeColor || 'orange',
        gradeLabel: newCourse.gradeLabel || '一般',
      }, ...prev]);
      setShowNewCourse(false);
      setNewCourseForm({ name: '', code: '', description: '', semester: '' });
    } catch {
      // NOTE: 静默处理
    } finally {
      setCreating(false);
    }
  };

  /** 过滤课程 */
  const filteredCourses = courses.filter(c =>
    !searchKeyword.trim() || c.name.toLowerCase().includes(searchKeyword.toLowerCase())
  );

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center bg-background-light">
        <div className="flex flex-col items-center gap-4">
          <div className="size-10 border-4 border-primary/20 border-t-primary rounded-full animate-spin"></div>
          <p className="text-sm text-slate-400">加载课程库...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 overflow-y-auto p-8 bg-background-light">
      <div className="max-w-5xl mx-auto space-y-6">
        {/* 头部区域 */}
        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
          <div>
            <h2 className="text-2xl font-display font-bold text-slate-900">课程库</h2>
            <p className="text-slate-500 text-sm mt-1">管理课程并查看知识点思政映射</p>
          </div>
          <div className="flex items-center gap-3">
            <div className="relative">
              <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 text-lg">search</span>
              <input
                type="text"
                placeholder="搜索课程..."
                value={searchKeyword}
                onChange={(e) => setSearchKeyword(e.target.value)}
                className="h-10 pl-10 pr-4 rounded-lg border border-slate-200 bg-white text-sm outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary w-48"
              />
            </div>
            <button
              onClick={() => setShowNewCourse(true)}
              className="flex items-center gap-2 px-4 py-2.5 bg-primary text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors shadow-sm"
            >
              <span className="material-symbols-outlined text-[18px]">add</span>
              新建课程
            </button>
          </div>
        </div>

        {/* 课程列表 */}
        <div className="space-y-4">
          {filteredCourses.length > 0 ? filteredCourses.map((course) => (
            <div key={course.id} className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden hover:shadow-md transition-shadow">
              <button
                onClick={() => handleExpand(course.id)}
                className="w-full px-6 py-5 flex items-center gap-4 text-left hover:bg-slate-50 transition-colors"
              >
                <div className={`size-12 rounded-xl bg-${course.gradeColor}-100 flex items-center justify-center text-${course.gradeColor}-600 font-bold text-lg shrink-0`}>
                  {course.name?.charAt(0) || '课'}
                </div>
                <div className="flex-1 min-w-0">
                  <h3 className="font-bold text-slate-900 truncate">{course.name}</h3>
                  <div className="flex items-center gap-3 mt-1">
                    <span className={`px-2 py-0.5 rounded text-[11px] font-medium bg-${course.gradeColor}-100 text-${course.gradeColor}-700`}>
                      {course.gradeLabel}
                    </span>
                    <div className="flex items-center gap-1 text-xs text-slate-400">
                      <span className="material-symbols-outlined text-[14px]">trending_up</span>
                      进度 {course.progress}%
                    </div>
                  </div>
                </div>
                <div className="w-24 mr-4">
                  <div className="h-1.5 bg-slate-100 rounded-full overflow-hidden">
                    <div className="h-full bg-primary rounded-full transition-all" style={{ width: `${course.progress}%` }}></div>
                  </div>
                </div>
                <span className={`material-symbols-outlined text-slate-400 transition-transform ${expandedId === course.id ? 'rotate-180' : ''}`}>
                  expand_more
                </span>
              </button>

              {/* 展开区域：关联知识点 */}
              {expandedId === course.id && (
                <div className="px-6 py-5 border-t border-slate-100 bg-slate-50/50">
                  <h4 className="text-sm font-bold text-slate-700 mb-4 flex items-center gap-2">
                    <span className="material-symbols-outlined text-[16px] text-primary">hub</span>
                    关联知识点 · 思政映射
                  </h4>
                  {kpLoading ? (
                    <div className="flex items-center justify-center py-8">
                      <div className="size-6 border-2 border-primary/20 border-t-primary rounded-full animate-spin"></div>
                    </div>
                  ) : knowledgePoints.length > 0 ? (
                    <div className="space-y-3">
                      {knowledgePoints.map((kp) => (
                        <div key={kp.id} className="bg-white rounded-lg border border-slate-100 p-4 hover:shadow-sm transition-shadow">
                          <div className="flex items-start gap-3">
                            <div className={`size-8 rounded-lg flex items-center justify-center shrink-0 ${kp.nodeType === 'IDEO'
                                ? 'bg-red-50 text-red-600'
                                : 'bg-blue-50 text-blue-600'
                              }`}>
                              <span className="material-symbols-outlined text-[16px]">
                                {kp.nodeType === 'IDEO' ? 'psychology' : 'memory'}
                              </span>
                            </div>
                            <div className="flex-1 min-w-0">
                              <div className="flex items-center gap-2 mb-1">
                                <h5 className="font-bold text-sm text-slate-800">{kp.name}</h5>
                                <span className={`text-[10px] px-1.5 py-0.5 rounded font-medium ${kp.nodeType === 'IDEO'
                                    ? 'bg-red-50 text-red-600'
                                    : 'bg-blue-50 text-blue-600'
                                  }`}>
                                  {kp.nodeType === 'TECH' ? '技术' : '思政'}
                                </span>
                              </div>
                              {kp.technicalDefinition && (
                                <p className="text-xs text-slate-500 leading-relaxed">{kp.technicalDefinition}</p>
                              )}
                              {kp.ideologicalValue && (
                                <div className="mt-2 px-3 py-2 bg-red-50/60 rounded-lg border border-red-100/50">
                                  <p className="text-xs text-red-700 flex items-start gap-1.5">
                                    <span className="material-symbols-outlined text-[13px] shrink-0 mt-0.5">favorite</span>
                                    {kp.ideologicalValue}
                                  </p>
                                </div>
                              )}
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="py-8 text-center text-slate-400 text-sm">暂无关联知识点</div>
                  )}
                </div>
              )}
            </div>
          )) : (
            <div className="py-16 text-center text-slate-400">
              <span className="material-symbols-outlined text-4xl mb-2">school</span>
              <p className="text-sm">暂无课程数据</p>
            </div>
          )}
        </div>
      </div>

      {/* 新建课程弹窗 */}
      {showNewCourse && (
        <>
          <div className="fixed inset-0 bg-black/30 z-40" onClick={() => setShowNewCourse(false)}></div>
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden">
              <div className="px-6 py-5 border-b border-slate-100 flex justify-between items-center">
                <h3 className="font-display font-bold text-lg text-slate-900">新建课程</h3>
                <button onClick={() => setShowNewCourse(false)} className="text-slate-300 hover:text-slate-600">
                  <span className="material-symbols-outlined">close</span>
                </button>
              </div>
              <div className="p-6 space-y-4">
                <div>
                  <label className="text-sm font-medium text-slate-700 mb-1.5 block">
                    课程名称 <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    value={newCourseForm.name}
                    onChange={(e) => setNewCourseForm(prev => ({ ...prev, name: e.target.value }))}
                    placeholder="如：物联网技术与应用"
                    className="w-full h-10 px-3 rounded-lg border border-slate-200 text-sm outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                  />
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="text-sm font-medium text-slate-700 mb-1.5 block">课程编码</label>
                    <input
                      type="text"
                      value={newCourseForm.code}
                      onChange={(e) => setNewCourseForm(prev => ({ ...prev, code: e.target.value }))}
                      placeholder="如：CS101"
                      className="w-full h-10 px-3 rounded-lg border border-slate-200 text-sm outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium text-slate-700 mb-1.5 block">学期</label>
                    <input
                      type="text"
                      value={newCourseForm.semester}
                      onChange={(e) => setNewCourseForm(prev => ({ ...prev, semester: e.target.value }))}
                      placeholder="如：2026春"
                      className="w-full h-10 px-3 rounded-lg border border-slate-200 text-sm outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                    />
                  </div>
                </div>
                <div>
                  <label className="text-sm font-medium text-slate-700 mb-1.5 block">课程描述</label>
                  <textarea
                    value={newCourseForm.description}
                    onChange={(e) => setNewCourseForm(prev => ({ ...prev, description: e.target.value }))}
                    placeholder="课程简介与教学目标..."
                    rows={3}
                    className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary resize-none"
                  />
                </div>
              </div>
              <div className="px-6 py-4 border-t border-slate-100 flex justify-end gap-3">
                <button
                  onClick={() => setShowNewCourse(false)}
                  className="px-4 py-2 text-sm text-slate-500 border border-slate-200 rounded-lg hover:bg-slate-50 transition-colors"
                >
                  取消
                </button>
                <button
                  onClick={handleCreateCourse}
                  disabled={!newCourseForm.name.trim() || creating}
                  className="px-6 py-2 text-sm text-white bg-primary rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                  {creating ? '创建中...' : '创建课程'}
                </button>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
};