import React, { Suspense, lazy, useEffect, useState } from 'react';
import { Layout } from 'antd';
import { View, ViewChangeHandler } from '../types';
import { AppSidebar } from '../components/Sidebar';
import { AppHeader } from '../components/Header';
import { useAuth } from '../contexts/AuthContext';

const { Sider, Content } = Layout;
const KnowledgeGraph = lazy(() => import('../views/KnowledgeGraph').then((module) => ({ default: module.KnowledgeGraph })));
const AIAssistant = lazy(() => import('../views/AIAssistant').then((module) => ({ default: module.AIAssistant })));
const ResourceLibrary = lazy(() => import('../views/ResourceLibrary').then((module) => ({ default: module.ResourceLibrary })));
const StudentHome = lazy(() => import('../views/StudentHome').then((module) => ({ default: module.StudentHome })));

export const StudentShell: React.FC = () => {
  const { currentUser, roleUi, logout, updateAvatar } = useAuth();
  const [collapsed, setCollapsed] = useState(false);
  const [currentView, setCurrentView] = useState<View>(roleUi?.defaultView || View.STUDENT_HOME);
  const [highlightNodeIds, setHighlightNodeIds] = useState<number[]>([]);

  useEffect(() => {
    if (roleUi && !roleUi.allowedViews.includes(currentView)) {
      console.warn(`Blocked unauthorized view ${currentView}, redirecting to the default student view.`);
      setCurrentView(roleUi.defaultView || View.STUDENT_HOME);
    }
  }, [currentView, roleUi]);

  const handleChangeView: ViewChangeHandler = (view, options) => {
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
        return <ResourceLibrary onChangeView={handleChangeView} />;
      default:
        return <StudentHome onChangeView={handleChangeView} />;
    }
  };

  const getHeaderTitle = () => {
    switch (currentView) {
      case View.STUDENT_HOME: return 'Learning Space';
      case View.KNOWLEDGE_GRAPH: return 'Knowledge Graph';
      case View.AI_ASSISTANT: return 'AI Assistant';
      case View.COURSE_LIBRARY: return 'Course Library';
      default: return 'Learning Space';
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
          <Suspense fallback={renderContentFallback()}>
            {renderView()}
          </Suspense>
        </Content>
      </Layout>
    </Layout>
  );
};
