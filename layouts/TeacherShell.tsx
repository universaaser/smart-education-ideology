import React, { useState, useEffect, useCallback } from 'react';
import { View } from '../types';
import { Sidebar } from '../components/Sidebar';
import { Header } from '../components/Header';
import { Dashboard } from '../views/Dashboard';
import { KnowledgeGraph } from '../views/KnowledgeGraph';
import { AIAssistant } from '../views/AIAssistant';
import { ResourceUpload } from '../views/ResourceUpload';
import { ResourceLibrary } from '../views/ResourceLibrary';
import { useAuth } from '../contexts/AuthContext';
import { resourceApi, CrawlTaskStatusInfo } from '../services/api';

export const TeacherShell: React.FC = () => {
  const { currentUser, roleUi, logout, updateAvatar } = useAuth();
  const [currentView, setCurrentView] = useState<View>(roleUi?.defaultView || View.DASHBOARD);
  const [crawlStatus, setCrawlStatus] = useState<CrawlTaskStatusInfo | null>(null);

  // 教师特有：知识图谱爬虫状态轮询
  const fetchCrawlStatus = useCallback(async () => {
    try {
      const status = await resourceApi.getCrawlStatus();
      setCrawlStatus(status);
    } catch {
      // 忽略轮询错误
    }
  }, []);

  useEffect(() => {
    fetchCrawlStatus();
    const timer = window.setInterval(fetchCrawlStatus, 2000);
    return () => window.clearInterval(timer);
  }, [fetchCrawlStatus]);

  // 视图守卫：如果切换到不被允许的视图，回退到默认
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
    <div className="flex h-screen w-full bg-background-light">
      <Sidebar 
        currentView={currentView} 
        onChangeView={setCurrentView} 
        onLogout={logout} 
        user={currentUser} 
        onAvatarChange={updateAvatar} 
      />
      <main className="flex-1 flex flex-col relative h-full overflow-hidden">
        <Header title={getHeaderTitle()} user={currentUser} />
        <div className="flex-1 relative overflow-hidden">
          {renderView()}
        </div>
      </main>
    </div>
  );
};
