import React, { Suspense, lazy } from 'react';
import { Spin } from 'antd';
import { useAuth } from './contexts/AuthContext';

const Auth = lazy(() => import('./views/Auth').then((module) => ({ default: module.Auth })));
const TeacherShell = lazy(() => import('./layouts/TeacherShell').then((module) => ({ default: module.TeacherShell })));
const StudentShell = lazy(() => import('./layouts/StudentShell').then((module) => ({ default: module.StudentShell })));

const AppLoadingFallback = () => (
  <div style={{
    height: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    background: 'var(--bg-light)',
    flexDirection: 'column',
    gap: 16,
  }}>
    <Spin size="large" />
    <span style={{ color: '#94a3b8', fontSize: 13 }}>Loading...</span>
  </div>
);

export default function App() {
  const { isLoading, isLoggedIn, roleUi } = useAuth();

  if (isLoading) {
    return <AppLoadingFallback />;
  }

  if (!isLoggedIn) {
    return (
      <Suspense fallback={<AppLoadingFallback />}>
        <Auth />
      </Suspense>
    );
  }

  if (roleUi?.role === 'STUDENT') {
    return (
      <Suspense fallback={<AppLoadingFallback />}>
        <StudentShell />
      </Suspense>
    );
  }

  return (
    <Suspense fallback={<AppLoadingFallback />}>
      <TeacherShell />
    </Suspense>
  );
}
