import React, { Suspense, lazy, useState, useEffect, useCallback } from 'react';
import { Layout } from 'antd';
import { ResourceUploadTarget, View, ViewChangeHandler } from '../types';
import { AppSidebar } from '../components/Sidebar';
import { AppHeader } from '../components/Header';
import { useAuth } from '../contexts/AuthContext';
import { resourceApi, CrawlTaskStatusInfo } from '../services/api';

const { Sider, Content } = Layout;
const Dashboard = lazy(() => import('../views/Dashboard').then((module) => ({ default: module.Dashboard })));
const KnowledgeGraph = lazy(() => import('../views/KnowledgeGraph').then((module) => ({ default: module.KnowledgeGraph })));
const AIAssistant = lazy(() => import('../views/AIAssistant').then((module) => ({ default: module.AIAssistant })));
const ResourceUpload = lazy(() => import('../views/ResourceUpload').then((module) => ({ default: module.ResourceUpload })));
const ResourceLibrary = lazy(() => import('../views/ResourceLibrary').then((module) => ({ default: module.ResourceLibrary })));

export const TeacherShell: React.FC = () => {
  const { currentUser, roleUi, logout, updateAvatar } = useAuth();
  const [currentView, setCurrentView] = useState<View>(roleUi?.defaultView || View.DASHBOARD);
  const [crawlStatus, setCrawlStatus] = useState<CrawlTaskStatusInfo | null>(null);
  const [collapsed, setCollapsed] = useState(false);
  const [resourceUploadTarget, setResourceUploadTarget] = useState<ResourceUploadTarget | null>(null);

  const fetchCrawlStatus = useCallback(async () => {
    try {
      const status = await resourceApi.getCrawlStatus();
      setCrawlStatus(status);
    } catch {
      // Keep the shell usable even if polling fails.
    }
  }, []);

  useEffect(() => {
    fetchCrawlStatus();
    const timer = window.setInterval(fetchCrawlStatus, 2000);
    return () => window.clearInterval(timer);
  }, [fetchCrawlStatus]);

  useEffect(() => {
    if (roleUi && !roleUi.allowedViews.includes(currentView)) {
      setCurrentView(roleUi.defaultView);
      if (roleUi.defaultView !== View.RESOURCE_UPLOAD) {
        setResourceUploadTarget(null);
      }
    }
  }, [currentView, roleUi]);

  const handleChangeView: ViewChangeHandler = (view, options) => {
    setCurrentView(view);
    if (view === View.RESOURCE_UPLOAD) {
      setResourceUploadTarget(options?.resourceUploadTarget ?? null);
    }
  };

  const renderView = () => {
    switch (currentView) {
      case View.DASHBOARD:
        return <Dashboard onChangeView={view => handleChangeView(view)} />;
      case View.KNOWLEDGE_GRAPH:
        return <KnowledgeGraph crawlStatus={crawlStatus} refreshCrawlStatus={fetchCrawlStatus} />;
      case View.AI_ASSISTANT:
        return <AIAssistant />;
      case View.RESOURCE_UPLOAD:
        return <ResourceUpload userId={currentUser?.id} resourceUploadTarget={resourceUploadTarget || undefined} />;
      case View.COURSE_LIBRARY:
        return <ResourceLibrary onChangeView={handleChangeView} />;
      default:
        return <Dashboard onChangeView={view => handleChangeView(view)} />;
    }
  };

  const getHeaderTitle = () => {
    switch (currentView) {
      case View.DASHBOARD: return 'Teacher Dashboard';
      case View.KNOWLEDGE_GRAPH: return 'Knowledge Graph';
      case View.AI_ASSISTANT: return 'AI Teaching Assistant';
      case View.RESOURCE_UPLOAD: return 'Resource Upload';
      case View.COURSE_LIBRARY: return 'Course Library';
      default: return 'Smart Ideology Education';
    }
  };

  const renderContentFallback = () => (
    <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      Loading...
    </div>
  );

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
          <Suspense fallback={renderContentFallback()}>
            {renderView()}
          </Suspense>
        </Content>
      </Layout>
    </Layout>
  );
};
