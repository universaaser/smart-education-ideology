import React, { useState, useEffect } from 'react';
import { Layout } from 'antd';
import { View } from '../types';
import { AppSidebar } from '../components/Sidebar';
import { AppHeader } from '../components/Header';
import { KnowledgeGraph } from '../views/KnowledgeGraph';
import { AIAssistant } from '../views/AIAssistant';
import { ResourceLibrary } from '../views/ResourceLibrary';
import { StudentHome } from '../views/StudentHome';
import { useAuth } from '../contexts/AuthContext';

const { Sider, Content } = Layout;

/**
 * 学生端布局 Shell
 *
 * NOTE: 路由守卫拦截越权视图访问，自动回退到学生端默认视图。
 * onChangeView 支持携带参数（highlightNodeIds），用于学习路径推荐跳转。
 */
export const StudentShell: React.FC = () => {
  const { currentUser, roleUi, logout, updateAvatar } = useAuth();
  const [collapsed, setCollapsed] = useState(false);

  /** 学生默认首页为 STUDENT_HOME */
  const [currentView, setCurrentView] = useState<View>(
    roleUi?.defaultView || View.STUDENT_HOME
  );

  /** 知识图谱高亮节点 ID（来自学习路径推荐跳转） */
  const [highlightNodeIds, setHighlightNodeIds] = useState<number[]>([]);

  /** 路由守卫：拦截越权访问 */
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
    <Layout style={{ height: '100vh', overflow: 'hidden' }}>
      <Sider
        theme="dark"
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        width={220}
        collapsedWidth={64}
        style={{ background: 'var(--sidebar-bg)', overflow: 'hidden' }}
      >
        <AppSidebar
          currentView={currentView}
          onChangeView={handleChangeView}
          onLogout={logout}
          user={currentUser}
          onAvatarChange={updateAvatar}
          collapsed={collapsed}
        />
      </Sider>
      <Layout style={{ background: 'var(--bg-light)', overflow: 'hidden' }}>
        <AppHeader title={getHeaderTitle()} user={currentUser} />
        <Content style={{ overflow: 'hidden', position: 'relative', flex: 1, display: 'flex', flexDirection: 'column' }}>
          {renderView()}
        </Content>
      </Layout>
    </Layout>
  );
};
