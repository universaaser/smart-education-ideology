import React, { useState, useEffect } from 'react';
import { View } from '../types';
import { Sidebar } from '../components/Sidebar';
import { Header } from '../components/Header';
import { KnowledgeGraph } from '../views/KnowledgeGraph';
import { AIAssistant } from '../views/AIAssistant';
import { ResourceLibrary } from '../views/ResourceLibrary';
import { StudentHome } from '../views/StudentHome';
import { useAuth } from '../contexts/AuthContext';

/**
 * 学生端布局 Shell
 *
 * <p>
 * 路由守卫：拦截越权视图访问，自动回退到学生端默认视图。
 * 学生默认首页为 STUDENT_HOME（独立设计，非教师 Dashboard 克隆）。
 *
 * NOTE: onChangeView 支持携带参数（highlightNodeIds），
 * 用于学习路径推荐跳转至知识图谱时高亮指定节点。
 */
export const StudentShell: React.FC = () => {
  const { currentUser, roleUi, logout, updateAvatar } = useAuth();

  // 学生默认首页为 STUDENT_HOME
  const [currentView, setCurrentView] = useState<View>(
    roleUi?.defaultView || View.STUDENT_HOME
  );

  // 知识图谱高亮节点 ID（来自学习路径推荐跳转）
  const [highlightNodeIds, setHighlightNodeIds] = useState<number[]>([]);

  // 路由监控与视图守卫：拦截越权访问
  useEffect(() => {
    if (roleUi && !roleUi.allowedViews.includes(currentView)) {
      console.warn(`检测到越权访问视图 ${currentView}，自动回退至默认首页`);
      setCurrentView(roleUi.defaultView || View.STUDENT_HOME);
    }
  }, [currentView, roleUi]);

  /**
   * 切换视图，支持携带可选参数
   * @param view 目标视图枚举值
   * @param options 携带参数（例如知识图谱高亮节点 ID 列表）
   */
  const handleChangeView = (view: View, options?: { highlightNodeIds?: number[] }) => {
    setCurrentView(view);
    if (options?.highlightNodeIds) {
      setHighlightNodeIds(options.highlightNodeIds);
    } else {
      setHighlightNodeIds([]);
    }
  };

  const renderView = () => {
    switch (currentView) {
      case View.STUDENT_HOME:
        return <StudentHome onChangeView={handleChangeView} />;
      case View.KNOWLEDGE_GRAPH:
        return (
          <KnowledgeGraph
            crawlStatus={null}
            refreshCrawlStatus={async () => {}}
            highlightNodeIds={highlightNodeIds}
          />
        );
      case View.AI_ASSISTANT:
        return <AIAssistant />;
      case View.COURSE_LIBRARY:
        return <ResourceLibrary />;
      default:
        // 后备渲染：回到学生首页
        return <StudentHome onChangeView={handleChangeView} />;
    }
  };

  const getHeaderTitle = () => {
    switch (currentView) {
      case View.STUDENT_HOME: return '学习空间';
      case View.KNOWLEDGE_GRAPH: return '知识图谱交互分析';
      case View.AI_ASSISTANT: return 'AI 导师解答';
      case View.COURSE_LIBRARY: return '发现课程';
      default: return '学习空间';
    }
  };

  return (
    <div className="flex h-screen w-full bg-background-light">
      <Sidebar
        currentView={currentView}
        onChangeView={handleChangeView}
        onLogout={logout}
        user={currentUser}
        onAvatarChange={updateAvatar}
      />
      <main className="flex-1 flex flex-col relative h-full overflow-hidden">
        <Header title={getHeaderTitle()} user={currentUser} />
        <div className="flex-1 relative overflow-hidden flex flex-col">
          {renderView()}
        </div>
      </main>
    </div>
  );
};
