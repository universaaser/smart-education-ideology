import React, { Suspense, lazy, useEffect, useState } from 'react';
import { Layout } from 'antd';
import { View, ViewChangeHandler } from '../types';
import { AppSidebar } from '../components/Sidebar';
import { AppHeader } from '../components/Header';
import { useAuth } from '../contexts/AuthContext';
import { TrackingProvider, useTracking } from '../contexts/TrackingContext';

const { Sider, Content } = Layout;
const KnowledgeGraph = lazy(() => import('../views/KnowledgeGraph').then((module) => ({ default: module.KnowledgeGraph })));
const AIAssistant = lazy(() => import('../views/AIAssistant').then((module) => ({ default: module.AIAssistant })));
const ResourceLibrary = lazy(() => import('../views/ResourceLibrary').then((module) => ({ default: module.ResourceLibrary })));
const StudentHome = lazy(() => import('../views/StudentHome').then((module) => ({ default: module.StudentHome })));

const StudentShellContent: React.FC = () => {
  const { currentUser, roleUi, logout, updateAvatar } = useAuth();
  const { track } = useTracking();
  const [collapsed, setCollapsed] = useState(false);
  const [currentView, setCurrentView] = useState<View>(roleUi?.defaultView || View.STUDENT_HOME);
  const [highlightNodeIds, setHighlightNodeIds] = useState<number[]>([]);

  useEffect(() => {
    if (roleUi && !roleUi.allowedViews.includes(currentView)) {
      console.warn(`Blocked unauthorized view ${currentView}, redirecting to the default student view.`);
      setCurrentView(roleUi.defaultView || View.STUDENT_HOME);
    }
  }, [currentView, roleUi]);

  useEffect(() => {
    const startedAt = Date.now();
    track({ eventType: 'page_stay', payload: { view: currentView } });
    return () => {
      track({
        eventType: 'page_stay',
        durationSeconds: Math.max(1, Math.round((Date.now() - startedAt) / 1000)),
        payload: { view: currentView },
      });
    };
  }, [currentView, track]);

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
            onNodeView={(node) => track({ eventType: 'knowledge_view', knowledgePointId: node.id, payload: { nodeName: node.name } })}
          />
        );
      case View.AI_ASSISTANT:
        return <AIAssistant onQuestionSubmit={(question) => track({ eventType: 'ai_ask', payload: { questionLength: question.length } })} />;
      case View.COURSE_LIBRARY:
        return (
          <ResourceLibrary
            onChangeView={handleChangeView}
            onMaterialOpen={(materialId, courseId) => track({ eventType: 'material_open', courseId, payload: { materialId } })}
            onCourseOpen={(courseId) => track({ eventType: 'material_open', courseId, payload: { scope: 'course' } })}
          />
        );
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

export const StudentShell: React.FC = () => (
  <TrackingProvider>
    <StudentShellContent />
  </TrackingProvider>
);
