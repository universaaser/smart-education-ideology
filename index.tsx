import React from 'react';
import ReactDOM from 'react-dom/client';
import { ConfigProvider, App as AntApp } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import App from './App';
import { AuthProvider } from './contexts/AuthContext';

const rootElement = document.getElementById('root');
if (!rootElement) {
  throw new Error('Could not find root element to mount to');
}

const root = ReactDOM.createRoot(rootElement);
root.render(
  <React.StrictMode>
    {/*
     * ConfigProvider：全局 Ant Design 配置
     * - locale 设置中文语言包（日期选择器、表单校验等）
     * - theme.token 复用 Ant Design 默认蓝色主题，无需自定义
     */}
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          /**
           * 主色与圆角由全局主题统一管理。
           * 与 index.css 的语义化变量保持一致，避免页面内重复硬编码。
           */
          colorPrimary: '#1677ff',
          borderRadius: 8,
          fontFamily: "'Noto Sans SC', -apple-system, BlinkMacSystemFont, sans-serif",
        },
        components: {
          Layout: {
            /* 侧边栏使用深色背景，覆盖 Layout.Sider 默认色 */
            siderBg: 'var(--sidebar-bg)',
            triggerBg: 'var(--sidebar-trigger-bg)',
          },
          Menu: {
            /* 深色侧边栏菜单配色 */
            darkItemBg: 'var(--sidebar-bg)',
            darkSubMenuItemBg: '#162035',
            darkItemSelectedBg: 'var(--color-primary)',
            darkItemHoverBg: 'rgba(255,255,255,0.05)',
            darkItemColor: 'var(--color-text-tertiary)',
            darkItemSelectedColor: '#ffffff',
          },
        },
      }}
    >
      {/*
       * AntApp：启用 Ant Design 的 message / notification / modal 静态方法
       * 必须在 ConfigProvider 内部使用
       */}
      <AntApp>
        <AuthProvider>
          <App />
        </AuthProvider>
      </AntApp>
    </ConfigProvider>
  </React.StrictMode>
);
