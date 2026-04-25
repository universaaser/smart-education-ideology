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
const AlertConsole = lazy(() => import('../views/AlertConsole').then((module) => ({ default: module.AlertConsole })));
const SourceManagement = lazy(() => import('../views/SourceManagement').then((module) => ({ default: module.SourceManagement })));
const KeywordTasks = lazy(() => import('../views/KeywordTasks').then((module) => ({ default: module.KeywordTasks })));
const MatchReview = lazy(() => import('../views/MatchReview').then((module) => ({ default: module.MatchReview })));
const ModelSettings = lazy(() => import('../views/ModelSettings').then((module) => ({ default: module.ModelSettings })));
const AdminConsole = lazy(() => import('../views/AdminConsole').then((module) => ({ default: module.AdminConsole })));
const CourseManagement = lazy(() => import('../views/CourseManagement').then((module) => ({ default: module.CourseManagement })));

export const TeacherShell: React.FC = () => {
  const { currentUser, roleUi, logout, updateAvatar } = useAuth();
  const [currentView, setCurrentView] = useState<View>(roleUi?.defaultView || View.DASHBOARD);
  const [crawlStatus, setCrawlStatus] = useState<CrawlTaskStatusInfo | null>(null);
  const [collapsed, setCollapsed] = useState(false);
  const [resourceUploadTarget, setResourceUploadTarget] = useState<ResourceUploadTarget | null>(null);
  const [courseManagementCourseId, setCourseManagementCourseId] = useState<number | null>(null);

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
    setResourceUploadTarget(view === View.RESOURCE_UPLOAD ? options?.resourceUploadTarget ?? null : null);
    setCourseManagementCourseId(view === View.COURSE_MANAGEMENT ? options?.courseId ?? null : null);
  };

  const renderView = () => {
    switch (currentView) {
      case View.DASHBOARD:
        return <Dashboard onChangeView={handleChangeView} />;
      case View.KNOWLEDGE_GRAPH:
        return <KnowledgeGraph crawlStatus={crawlStatus} refreshCrawlStatus={fetchCrawlStatus} />;
      case View.AI_ASSISTANT:
        return <AIAssistant />;
      case View.RESOURCE_UPLOAD:
        return <ResourceUpload userId={currentUser?.id} resourceUploadTarget={resourceUploadTarget || undefined} />;
      case View.COURSE_LIBRARY:
        return <ResourceLibrary onChangeView={handleChangeView} />;
      case View.COURSE_MANAGEMENT:
        return <CourseManagement courseId={courseManagementCourseId} onChangeView={handleChangeView} />;
      case View.ALERTS:
        return <AlertConsole />;
      case View.SOURCE_MANAGEMENT:
        return <SourceManagement />;
      case View.KEYWORD_TASKS:
        return <KeywordTasks />;
      case View.MATCH_REVIEW:
        return <MatchReview />;
      case View.MODEL_SETTINGS:
        return <ModelSettings />;
      case View.ADMIN_CONSOLE:
        return <AdminConsole />;
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
      case View.ALERTS: return 'Student Alert Console';
      case View.SOURCE_MANAGEMENT: return 'Source Management';
      case View.KEYWORD_TASKS: return 'Keyword Tasks';
      case View.MATCH_REVIEW: return 'Match Review';
      case View.COURSE_MANAGEMENT: return 'Course Management';
      case View.MODEL_SETTINGS: return 'Model Settings';
      case View.ADMIN_CONSOLE: return 'Admin Console';
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
