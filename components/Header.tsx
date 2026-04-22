import React, { useState } from 'react';
import { Layout, Breadcrumb, Badge, Dropdown, List, Avatar, Typography, Space, Empty } from 'antd';
import { BellOutlined, UserOutlined, RobotOutlined, CloudUploadOutlined, WarningOutlined, ShareAltOutlined } from '@ant-design/icons';
import { CurrentUser } from '../types';
import { dashboardApi, ActivityInfo } from '../services/api';

const { Header } = Layout;
const { Text } = Typography;

interface AppHeaderProps {
  title: string;
  subtitle?: string;
  user?: CurrentUser | null;
}

const getActivityStyle = (type: string) => {
  switch (type) {
    case 'AI_ANALYSIS': return { icon: <RobotOutlined />, color: 'var(--color-primary)' };
    case 'UPLOAD': return { icon: <CloudUploadOutlined />, color: '#10b981' };
    case 'ALERT': return { icon: <WarningOutlined />, color: 'var(--color-error)' };
    case 'KNOWLEDGE_UPDATE': return { icon: <ShareAltOutlined />, color: '#8b5cf6' };
    default: return { icon: <BellOutlined />, color: 'var(--color-text-tertiary)' };
  }
};

export const AppHeader: React.FC<AppHeaderProps> = ({ title, subtitle, user }) => {
  const [notifications, setNotifications] = useState<ActivityInfo[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [loading, setLoading] = useState(false);

  const displayName = user?.realName || user?.username || 'User';
  const department = user?.department || user?.role || '';

  const handleOpenChange = async (open: boolean) => {
    if (open && !loaded) {
      setLoading(true);
      try {
        const data = await dashboardApi.getActivities(4);
        setNotifications(data);
        setLoaded(true);
      } catch {
        // Keep notifications silent when the backend is unavailable.
      } finally {
        setLoading(false);
      }
    }
  };

  const notificationPanel = (
    <div
      style={{
        width: 320,
        background: 'var(--color-bg-panel)',
        borderRadius: 'var(--radius-md)',
        boxShadow: '0 8px 32px rgba(0,0,0,0.12)',
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          padding: '12px 16px',
          borderBottom: '1px solid var(--color-border)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <Text strong style={{ fontSize: 14 }}>Latest Activity</Text>
        {notifications.length > 0 && (
          <Text
            style={{
              fontSize: 11,
              color: 'var(--color-primary)',
              background: 'var(--color-primary-soft)',
              padding: '2px 8px',
              borderRadius: 10,
              fontWeight: 600
            }}
          >
            {notifications.length} items
          </Text>
        )}
      </div>
      {loading ? (
        <div style={{ padding: 32, textAlign: 'center', color: 'var(--color-text-tertiary)', fontSize: 13 }}>Loading...</div>
      ) : notifications.length === 0 ? (
        <Empty description="No activity yet" style={{ padding: '24px 0' }} />
      ) : (
        <List<ActivityInfo>
          dataSource={notifications}
          renderItem={(item: ActivityInfo) => {
            const style = getActivityStyle(item.type);
            return (
              <List.Item style={{ padding: '12px 16px', borderBottom: '1px solid #fafafa' }}>
                <List.Item.Meta
                  avatar={
                    <Avatar
                      icon={style.icon}
                      style={{ background: `${style.color}18`, color: style.color, flexShrink: 0 }}
                    />
                  }
                  title={<Text strong style={{ fontSize: 12 }}>{item.title}</Text>}
                  description={
                    <Space direction="vertical" size={0}>
                      <Text type="secondary" style={{ fontSize: 11 }}>{item.description}</Text>
                      <Text type="secondary" style={{ fontSize: 10 }}>{item.createdAt}</Text>
                    </Space>
                  }
                />
              </List.Item>
            );
          }}
        />
      )}
    </div>
  );

  return (
    <Header
      style={{
        background: 'var(--color-bg-panel)',
        borderBottom: '1px solid var(--color-border)',
        padding: '0 32px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        height: 56,
        lineHeight: '56px',
        position: 'sticky',
        top: 0,
        zIndex: 10,
        flexShrink: 0,
      }}
    >
      <Breadcrumb
        items={[
          { title: <Text type="secondary" style={{ fontSize: 13 }}>Smart Ideology Education</Text> },
          { title: <Text strong style={{ fontSize: 14 }}>{title}</Text> },
          ...(subtitle ? [{ title: <Text type="secondary" style={{ fontSize: 13 }}>{subtitle}</Text> }] : []),
        ]}
        separator="/"
      />

      <Space size={16} align="center">
        <Dropdown
          dropdownRender={() => notificationPanel}
          trigger={['click']}
          onOpenChange={handleOpenChange}
          placement="bottomRight"
        >
          <Badge count={notifications.length} size="small" offset={[-2, 2]}>
            <BellOutlined
              style={{
                fontSize: 18,
                color: 'var(--color-text-secondary)',
                cursor: 'pointer',
                padding: 6,
                borderRadius: 'var(--radius-sm)',
                transition: 'background 0.2s',
              }}
              onMouseEnter={event => (event.currentTarget.style.background = '#f5f5f5')}
              onMouseLeave={event => (event.currentTarget.style.background = 'transparent')}
            />
          </Badge>
        </Dropdown>

        <div style={{ width: 1, height: 20, background: '#e2e8f0' }} />

        <Space size={8} align="center">
          <div style={{ textAlign: 'right' }}>
            <div style={{ fontSize: 13, fontWeight: 600, lineHeight: 1.3, color: 'var(--color-text-primary)' }}>{displayName}</div>
            <div style={{ fontSize: 11, color: 'var(--color-text-tertiary)', lineHeight: 1.3 }}>{department}</div>
          </div>
          <Avatar
            icon={<UserOutlined />}
            style={{ background: 'var(--color-primary-soft)', color: 'var(--color-primary)', border: '1px solid rgba(22,119,255,0.2)' }}
          />
        </Space>
      </Space>
    </Header>
  );
};
