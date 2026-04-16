import React from 'react';
import { Spin } from 'antd';
import { Auth } from './views/Auth';
import { TeacherShell } from './layouts/TeacherShell';
import { StudentShell } from './layouts/StudentShell';
import { useAuth } from './contexts/AuthContext';

export default function App() {
  const { isLoading, isLoggedIn, roleUi } = useAuth();

  if (isLoading) {
    return (
      <div style={{
        height: '100vh', display: 'flex',
        alignItems: 'center', justifyContent: 'center',
        background: 'var(--bg-light)',
        flexDirection: 'column', gap: 16,
      }}>
        <Spin size="large" />
        <span style={{ color: '#94a3b8', fontSize: 13 }}>加载中...</span>
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

  // 默认使用教师外壳（包括 ADMIN）
  return <TeacherShell />;
}


