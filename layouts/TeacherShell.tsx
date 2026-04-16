import React, { useState, useEffect, useCallback } from 'react';
import { Layout, Spin } from 'antd';
import { View } from '../types';
import { AppSidebar } from '../components/Sidebar';
import { AppHeader } from '../components/Header';
import { Dashboard } from '../views/Dashboard';
import { KnowledgeGraph } from '../views/KnowledgeGraph';
import { AIAssistant } from '../views/AIAssistant';
import { ResourceUpload } from '../views/ResourceUpload';
import { ResourceLibrary } from '../views/ResourceLibrary';
import { useAuth } from '../contexts/AuthContext';
import { resourceApi, CrawlTaskStatusInfo } from '../services/api';

const { Sider, Content } = Layout;

/**
 * 教师端布局 Shell
 *
 * NOTE: 使用 Ant Design Layout 替换手写 flex 布局。
 * 知识图谱爬虫状态轮询保留在此层，便于跨视图共享状态。
 */
export const TeacherShell: React.FC = () => {
  const { currentUser, roleUi, logout, updateAvatar } = useAuth();
  const [currentView, setCurrentView] = useState<View>(roleUi?.defaultView || View.DASHBOARD);
  const [crawlStatus, setCrawlStatus] = useState<CrawlTaskStatusInfo | null>(null);
  const [collapsed, setCollapsed] = useState(false);

  /** 教师特有：知识图谱爬虫状态轮询 */
  const fetchCrawlStatus = useCallback(async () => {
    try {
      const status = await resourceApi.getCrawlStatus();
      setCrawlStatus(status);
    } catch {
      // 忽略轮询错误，不影响页面正常使用
    }
  }, []);

  useEffect(() => {
    fetchCrawlStatus();
    const timer = window.setInterval(fetchCrawlStatus, 2000);
    return () => window.clearInterval(timer);
  }, [fetchCrawlStatus]);

  /** 视图守卫：切换到不被允许的视图时回退到默认 */
  useEffect(() => {
    if (roleUi && !roleUi.allowedViews.includes(currentView)) {
      setCurrentView(roleUi.defaultView);
    }
  }, [currentView, roleUi]);

  const renderView = () => {
    switch (currentView) {
      case View.DASHBOARD:
        return <Dashboard onChangeView={setCurrentView} />;
      case View.KNOWLEDGE_GRAPH:
        return <KnowledgeGraph crawlStatus={crawlStatus} refreshCrawlStatus={fetchCrawlStatus} />;
      case View.AI_ASSISTANT:
        return <AIAssistant />;
      case View.RESOURCE_UPLOAD:
        return <ResourceUpload userId={currentUser?.id} />;
      case View.COURSE_LIBRARY:
        return <ResourceLibrary />;
      default:
        return <Dashboard onChangeView={setCurrentView} />;
    }
  };

  const getHeaderTitle = () => {
    switch (currentView) {
      case View.DASHBOARD: return '教师控制台';
      case View.KNOWLEDGE_GRAPH: return '知识图谱交互分析';
      case View.AI_ASSISTANT: return 'AI 课程思政教学助手';
      case View.RESOURCE_UPLOAD: return '资源管理';
      case View.COURSE_LIBRARY: return '课程资源库';
      default: return '智教思政';
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
        style={{
          background: 'var(--sidebar-bg)',
          overflow: 'hidden',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <AppSidebar
          currentView={currentView}
          onChangeView={setCurrentView}
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
