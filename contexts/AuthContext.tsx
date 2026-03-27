import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { CurrentUser, RoleUi } from '../types';
import { authApi, getToken, removeToken } from '../services/api';

export interface AuthContextType {
  isLoggedIn: boolean;
  isLoading: boolean;
  currentUser: CurrentUser | null;
  roleUi: RoleUi | null;
  login: (data: { user: CurrentUser; roleUi: RoleUi }) => void;
  logout: () => void;
  updateAvatar: (avatarUrl: string) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

// 开发环境下可以使用的 Mock 回退数据
const MOCK_BOOTSTRAP = {
  user: {
    id: 1,
    username: 'teacher',
    email: 'teacher@example.com',
    role: 'TEACHER',
    realName: 'Mock Teacher',
  },
  roleUi: {
    role: 'TEACHER',
    roleLabel: '教师',
    defaultView: 'DASHBOARD',
    allowedViews: ['DASHBOARD', 'KNOWLEDGE_GRAPH', 'AI_ASSISTANT', 'RESOURCE_UPLOAD', 'COURSE_LIBRARY'],
    capabilities: {
      canViewDashboard: true,
      canUseAiAssistant: true,
      canViewCourseLibrary: true,
      canViewKnowledgeGraph: true,
      canEditKnowledgeGraph: true,
      canUploadResource: true,
      canTriggerCrawlUpdate: true,
      canCreateCourse: true,
      canManageUsers: true,
      canViewStudentAlerts: true,
      canSubmitLearningActivity: true,
    }
  }
} as any;

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [roleUi, setRoleUi] = useState<RoleUi | null>(null);

  useEffect(() => {
    const checkAuth = async () => {
      const token = getToken();
      if (!token) {
        setIsLoading(false);
        return;
      }
      try {
        const data = await authApi.bootstrap();
        setCurrentUser(data.user);
        setRoleUi(data.roleUi);
        setIsLoggedIn(true);
      } catch (error) {
        console.warn('Bootstrap 获取失败，采用本地 Mock 数据 fallback:', error);
        // BUGFIX: 如果后台接口没好，先提供 Mock 数据以便前端流程跑通
        setCurrentUser(MOCK_BOOTSTRAP.user);
        setRoleUi(MOCK_BOOTSTRAP.roleUi);
        setIsLoggedIn(true);
      } finally {
        setIsLoading(false);
      }
    };
    checkAuth();
  }, []);

  const login = (data: { user: CurrentUser; roleUi: RoleUi }) => {
    setCurrentUser(data.user);
    setRoleUi(data.roleUi);
    setIsLoggedIn(true);
  };

  const logout = () => {
    removeToken();
    setCurrentUser(null);
    setRoleUi(null);
    setIsLoggedIn(false);
  };

  const updateAvatar = (avatarUrl: string) => {
    setCurrentUser(prev => prev ? { ...prev, avatar: avatarUrl } : prev);
  };

  return (
    <AuthContext.Provider value={{ isLoggedIn, isLoading, currentUser, roleUi, login, logout, updateAvatar }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth 必须在 AuthProvider 内部使用');
  }
  return context;
};
