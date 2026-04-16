import React, { useState } from 'react';
import {
  Menu, Avatar, Button, Tooltip, Typography, Upload,
  type MenuProps,
} from 'antd';
import {
  DashboardOutlined, HomeOutlined, ShareAltOutlined, RobotOutlined,
  CloudUploadOutlined, BookOutlined, LogoutOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { View, NavItem, CurrentUser } from '../types';
import { userApi } from '../services/api';
import { useAuth } from '../contexts/AuthContext';

const { Text } = Typography;

interface SidebarProps {
  currentView: View;
  onChangeView: (view: View, options?: { highlightNodeIds?: number[] }) => void;
  onLogout: () => void;
  user?: CurrentUser | null;
  onAvatarChange?: (avatarUrl: string) => void;
  /** 是否折叠状态，用于控制用户信息区的显示 */
  collapsed?: boolean;
}

/** 导航配置：菜单项 key 与 View 枚举保持一致 */
const ALL_NAV_ITEMS: (NavItem & { antIcon: React.ReactNode })[] = [
  { id: View.DASHBOARD,       label: '仪表盘',   icon: 'dashboard',    antIcon: <DashboardOutlined /> },
  { id: View.STUDENT_HOME,    label: '学习首页', icon: 'home',         antIcon: <HomeOutlined /> },
  { id: View.KNOWLEDGE_GRAPH, label: '知识图谱', icon: 'hub',          antIcon: <ShareAltOutlined /> },
  { id: View.AI_ASSISTANT,    label: 'AI 助教',  icon: 'smart_toy',    antIcon: <RobotOutlined />, isBeta: true },
  { id: View.RESOURCE_UPLOAD, label: '资源上传', icon: 'cloud_upload', antIcon: <CloudUploadOutlined /> },
  { id: View.COURSE_LIBRARY,  label: '课程库',   icon: 'library_books',antIcon: <BookOutlined /> },
];

/**
 * 应用侧边栏
 *
 * NOTE: 使用 Ant Design Menu（dark 主题）替换手写 button 列表。
 * 头像上传保留原有 input ref 方案，Upload 组件 beforeUpload 返回 false 阻止自动上传，
 * 改为手动调用 userApi.uploadAvatar。
 */
export const AppSidebar: React.FC<SidebarProps> = ({
  currentView,
  onChangeView,
  onLogout,
  user,
  onAvatarChange,
  collapsed = false,
}) => {
  const { roleUi } = useAuth();
  const [uploading, setUploading] = useState(false);
  const [avatarUrl, setAvatarUrl] = useState(user?.avatar || '');

  const displayName = user?.realName || user?.username || '用户';
  const roleText = user?.department
    ? `${user.department} · ${roleUi?.roleLabel || user.role || '教师'}`
    : roleUi?.roleLabel || user?.role || '教师';

  /** 基于角色白名单过滤菜单项 */
  const navItems = ALL_NAV_ITEMS.filter(item =>
    roleUi?.allowedViews?.includes(item.id)
  );

  /** 构造 Ant Design Menu items 格式 */
  const menuItems: MenuProps['items'] = navItems.map(item => ({
    key: item.id,
    icon: item.antIcon,
    label: item.isBeta ? (
      <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        {item.label}
        <span style={{
          fontSize: 9, background: 'var(--color-error)', color: '#fff',
          padding: '1px 5px', borderRadius: 4, lineHeight: '14px', fontWeight: 700,
        }}>
          BETA
        </span>
      </span>
    ) : item.label,
  }));

  /** 头像上传处理：手动调用 API，不走 antd Upload 自动上传 */
  const handleAvatarUpload = async (file: File) => {
    setUploading(true);
    try {
      const result = await userApi.uploadAvatar(file);
      setAvatarUrl(result.avatarUrl);
      onAvatarChange?.(result.avatarUrl);
    } catch {
      // NOTE: 上传失败静默处理，不阻断用户流程
    } finally {
      setUploading(false);
    }
    // 返回 false 阻止 antd Upload 自动上传
    return false;
  };

  return (
    <div style={{
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      background: 'var(--sidebar-bg)',
    }}>
      {/* ───── Logo 区 ───── */}
      <div style={{
        padding: collapsed ? '20px 0' : '20px 20px',
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        borderBottom: '1px solid rgba(255,255,255,0.06)',
        justifyContent: collapsed ? 'center' : 'flex-start',
        minHeight: 64,
      }}>
        <div style={{
          width: 36, height: 36,
          background: 'var(--color-primary)',
          borderRadius: 'var(--radius-sm)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          boxShadow: '0 4px 12px rgba(22,119,255,0.35)',
          flexShrink: 0,
        }}>
          <span className="material-symbols-outlined" style={{ color: '#fff', fontSize: 20 }}>auto_awesome</span>
        </div>
        {!collapsed && (
          <div style={{ overflow: 'hidden' }}>
            <div style={{ color: '#fff', fontWeight: 700, fontSize: 16, lineHeight: 1.2, fontFamily: "'Lexend', sans-serif" }}>
              智教思政
            </div>
            <div style={{ color: 'var(--color-text-secondary)', fontSize: 11, marginTop: 2 }}>智慧教学辅助系统</div>
          </div>
        )}
      </div>

      {/* ───── 导航菜单 ───── */}
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

      {/* ───── 用户信息与退出 ───── */}
      <div style={{
        borderTop: '1px solid rgba(255,255,255,0.06)',
        padding: collapsed ? '12px 0' : '12px 16px',
      }}>
        {!collapsed ? (
          <>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '4px 4px 12px' }}>
              {/* 点击头像触发文件选择 */}
              <Upload
                accept="image/*"
                showUploadList={false}
                beforeUpload={handleAvatarUpload}
              >
                <Tooltip title="点击更换头像" placement="right">
                  <Avatar
                    size={40}
                    src={avatarUrl || undefined}
                    icon={!avatarUrl ? <UserOutlined /> : undefined}
                    style={{
                      cursor: 'pointer',
                      border: '1px solid rgba(255,255,255,0.15)',
                      background: avatarUrl ? 'transparent' : 'rgba(22,119,255,0.2)',
                      flexShrink: 0,
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
              退出登录
            </Button>
          </>
        ) : (
          /* 折叠状态下只显示退出图标 */
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}>
            <Upload accept="image/*" showUploadList={false} beforeUpload={handleAvatarUpload}>
              <Tooltip title={displayName} placement="right">
                <Avatar
                  size={36}
                  src={avatarUrl || undefined}
                  icon={!avatarUrl ? <UserOutlined /> : undefined}
                  style={{ cursor: 'pointer', border: '1px solid rgba(255,255,255,0.15)', background: avatarUrl ? 'transparent' : 'rgba(22,119,255,0.2)' }}
                />
              </Tooltip>
            </Upload>
            <Tooltip title="退出登录" placement="right">
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
