import React, { useMemo, useState } from 'react';
import { Avatar, Button, Menu, Tooltip, Typography, Upload, type MenuProps } from 'antd';
import {
  BookOutlined,
  CloudUploadOutlined,
  DashboardOutlined,
  HomeOutlined,
  LogoutOutlined,
  RobotOutlined,
  ShareAltOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { CurrentUser, NavItem, View, ViewChangeHandler } from '../types';
import { userApi } from '../services/api';
import { useAuth } from '../contexts/AuthContext';

const { Text } = Typography;

interface SidebarProps {
  currentView: View;
  onChangeView: ViewChangeHandler;
  onLogout: () => void;
  user?: CurrentUser | null;
  onAvatarChange?: (avatarUrl: string) => void;
  collapsed?: boolean;
}

const ALL_NAV_ITEMS: (NavItem & { antIcon: React.ReactNode })[] = [
  { id: View.DASHBOARD, label: 'Dashboard', icon: 'dashboard', antIcon: <DashboardOutlined /> },
  { id: View.STUDENT_HOME, label: 'Student Home', icon: 'home', antIcon: <HomeOutlined /> },
  { id: View.KNOWLEDGE_GRAPH, label: 'Knowledge Graph', icon: 'hub', antIcon: <ShareAltOutlined /> },
  { id: View.AI_ASSISTANT, label: 'AI Assistant', icon: 'smart_toy', antIcon: <RobotOutlined />, isBeta: true },
  { id: View.RESOURCE_UPLOAD, label: 'Resource Upload', icon: 'cloud_upload', antIcon: <CloudUploadOutlined /> },
  { id: View.COURSE_LIBRARY, label: 'Course Library', icon: 'library_books', antIcon: <BookOutlined /> },
];

export const AppSidebar: React.FC<SidebarProps> = ({
  currentView,
  onChangeView,
  onLogout,
  user,
  onAvatarChange,
  collapsed = false,
}) => {
  const { roleUi } = useAuth();
  const [avatarUploading, setAvatarUploading] = useState(false);
  const [avatarUrl, setAvatarUrl] = useState(user?.avatar || '');

  const displayName = user?.realName || user?.username || 'User';
  const roleText = user?.department
    ? `${user.department} | ${roleUi?.roleLabel || user.role || 'Member'}`
    : roleUi?.roleLabel || user?.role || 'Member';

  const navItems = useMemo(
    () => ALL_NAV_ITEMS.filter((item) => roleUi?.allowedViews?.includes(item.id)),
    [roleUi?.allowedViews],
  );

  const menuItems: MenuProps['items'] = navItems.map((item) => ({
    key: item.id,
    icon: item.antIcon,
    label: item.isBeta ? (
      <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        {item.label}
        <span
          style={{
            fontSize: 9,
            background: 'var(--color-error)',
            color: '#fff',
            padding: '1px 5px',
            borderRadius: 4,
            lineHeight: '14px',
            fontWeight: 700,
          }}
        >
          BETA
        </span>
      </span>
    ) : (
      item.label
    ),
  }));

  const handleAvatarUpload = async (file: File) => {
    setAvatarUploading(true);
    try {
      const result = await userApi.uploadAvatar(file);
      setAvatarUrl(result.avatarUrl);
      onAvatarChange?.(result.avatarUrl);
    } finally {
      setAvatarUploading(false);
    }
    return false;
  };

  return (
    <div
      style={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        background: 'var(--sidebar-bg)',
      }}
    >
      <div
        style={{
          padding: collapsed ? '20px 0' : '20px 20px',
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          borderBottom: '1px solid rgba(255,255,255,0.06)',
          justifyContent: collapsed ? 'center' : 'flex-start',
          minHeight: 64,
        }}
      >
        <div
          style={{
            width: 36,
            height: 36,
            background: 'var(--color-primary)',
            borderRadius: 'var(--radius-sm)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 4px 12px rgba(22,119,255,0.35)',
            flexShrink: 0,
          }}
        >
          <span className="material-symbols-outlined" style={{ color: '#fff', fontSize: 20 }}>
            auto_awesome
          </span>
        </div>
        {!collapsed && (
          <div style={{ overflow: 'hidden' }}>
            <div
              style={{
                color: '#fff',
                fontWeight: 700,
                fontSize: 16,
                lineHeight: 1.2,
                fontFamily: "'Lexend', sans-serif",
              }}
            >
              Smart Education
            </div>
            <div style={{ color: 'var(--color-text-secondary)', fontSize: 11, marginTop: 2 }}>
              Teaching and learning workspace
            </div>
          </div>
        )}
      </div>

      <div style={{ flex: 1, overflow: 'hidden auto', paddingTop: 8 }}>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[currentView]}
          items={menuItems}
          onClick={({ key }) => onChangeView(key as View)}
          inlineCollapsed={collapsed}
          style={{ background: 'transparent', border: 'none' }}
        />
      </div>

      <div
        style={{
          borderTop: '1px solid rgba(255,255,255,0.06)',
          padding: collapsed ? '12px 0' : '12px 16px',
        }}
      >
        {!collapsed ? (
          <>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '4px 4px 12px' }}>
              <Upload accept="image/*" showUploadList={false} beforeUpload={handleAvatarUpload}>
                <Tooltip title="Change avatar" placement="right">
                  <Avatar
                    size={40}
                    src={avatarUrl || undefined}
                    icon={!avatarUrl ? <UserOutlined /> : undefined}
                    style={{
                      cursor: 'pointer',
                      border: '1px solid rgba(255,255,255,0.15)',
                      background: avatarUrl ? 'transparent' : 'rgba(22,119,255,0.2)',
                      flexShrink: 0,
                      opacity: avatarUploading ? 0.7 : 1,
                    }}
                  />
                </Tooltip>
              </Upload>
              <div style={{ overflow: 'hidden' }}>
                <Text style={{ color: '#fff', fontSize: 13, fontWeight: 600, display: 'block' }} ellipsis>
                  {displayName}
                </Text>
                <Text style={{ color: 'var(--color-text-secondary)', fontSize: 11 }} ellipsis>
                  {roleText}
                </Text>
              </div>
            </div>
            <Button
              onClick={onLogout}
              icon={<LogoutOutlined />}
              block
              size="small"
              style={{
                background: 'rgba(255,255,255,0.05)',
                border: '1px solid rgba(255,255,255,0.08)',
                color: 'var(--color-text-tertiary)',
              }}
            >
              Logout
            </Button>
          </>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}>
            <Upload accept="image/*" showUploadList={false} beforeUpload={handleAvatarUpload}>
              <Tooltip title={displayName} placement="right">
                <Avatar
                  size={36}
                  src={avatarUrl || undefined}
                  icon={!avatarUrl ? <UserOutlined /> : undefined}
                  style={{
                    cursor: 'pointer',
                    border: '1px solid rgba(255,255,255,0.15)',
                    background: avatarUrl ? 'transparent' : 'rgba(22,119,255,0.2)',
                    opacity: avatarUploading ? 0.7 : 1,
                  }}
                />
              </Tooltip>
            </Upload>
            <Tooltip title="Logout" placement="right">
              <Button
                onClick={onLogout}
                icon={<LogoutOutlined />}
                size="small"
                type="text"
                style={{ color: 'var(--color-text-secondary)' }}
              />
            </Tooltip>
          </div>
        )}
      </div>
    </div>
  );
};
