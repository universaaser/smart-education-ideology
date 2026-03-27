import React from 'react';
import { Auth } from './views/Auth';
import { TeacherShell } from './layouts/TeacherShell';
import { StudentShell } from './layouts/StudentShell';
import { useAuth } from './contexts/AuthContext';

export default function App() {
  const { isLoading, isLoggedIn, roleUi } = useAuth();

  if (isLoading) {
    return (
      <div className="flex h-screen w-full items-center justify-center bg-background-light">
        <div className="flex flex-col items-center gap-4">
          <div className="size-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin"></div>
          <p className="text-sm text-slate-500 font-medium">加载中...</p>
        </div>
      </div>
    );
  }

  if (!isLoggedIn) {
    return <Auth />;
  }

  // 角色分流
  if (roleUi?.role === 'STUDENT') {
    return <StudentShell />;
  }

  // 默认使用教师外壳 (包括 ADMIN)
  return <TeacherShell />;
}

