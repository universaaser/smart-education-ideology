import React, { useState } from 'react';
import { CurrentUser } from '../types';
import { dashboardApi, ActivityInfo } from '../services/api';

interface HeaderProps {
  title: string;
  subtitle?: string;
  user?: CurrentUser | null;
}

export const Header: React.FC<HeaderProps> = ({ title, subtitle, user }) => {
  const [showNotifications, setShowNotifications] = useState(false);
  const [notifications, setNotifications] = useState<ActivityInfo[]>([]);
  const [loaded, setLoaded] = useState(false);

  /** 点击通知图标时按需加载最新动态 */
  const handleToggleNotifications = async () => {
    if (!showNotifications && !loaded) {
      try {
        const data = await dashboardApi.getActivities(4);
        setNotifications(data);
        setLoaded(true);
      } catch {
        // NOTE: 后端未连接时静默降级
      }
    }
    setShowNotifications(!showNotifications);
  };

  /** 根据活动类型返回对应的图标和颜色 */
  const getActivityStyle = (type: string) => {
    switch (type) {
      case 'AI_ANALYSIS': return { icon: 'smart_toy', bg: 'bg-blue-100', color: 'text-primary' };
      case 'UPLOAD': return { icon: 'upload_file', bg: 'bg-emerald-100', color: 'text-emerald-600' };
      case 'ALERT': return { icon: 'priority_high', bg: 'bg-red-100', color: 'text-accent-red' };
      case 'KNOWLEDGE_UPDATE': return { icon: 'share', bg: 'bg-purple-100', color: 'text-purple-600' };
      default: return { icon: 'notifications', bg: 'bg-slate-100', color: 'text-slate-500' };
    }
  };

  const displayName = user?.realName || user?.username || '用户';
  const department = user?.department || user?.role || '';

  return (
    <header className="h-16 bg-white border-b border-slate-200 flex items-center justify-between px-8 z-20 shrink-0 relative">
      <div className="flex items-center gap-4">
        <div className="flex items-center gap-2 text-sm text-slate-500">
          <span>智教思政</span>
          <span className="material-symbols-outlined text-xs">chevron_right</span>
          <span className="text-slate-900 font-medium text-base">{title}</span>
          {subtitle && (
            <>
              <span className="material-symbols-outlined text-xs">chevron_right</span>
              <span className="text-slate-500 text-sm">{subtitle}</span>
            </>
          )}
        </div>
      </div>
      <div className="flex items-center gap-6">
        <div className="flex items-center gap-1 relative">
          <button
            onClick={handleToggleNotifications}
            className={`p-2 rounded-full relative transition-colors ${showNotifications ? 'bg-slate-100 text-primary' : 'text-slate-500 hover:bg-slate-100'}`}
          >
            <span className="material-symbols-outlined">notifications</span>
            {notifications.length > 0 && (
              <span className="absolute top-2 right-2 size-2 bg-red-500 rounded-full border-2 border-white"></span>
            )}
          </button>

          {showNotifications && (
            <>
              <div
                className="fixed inset-0 z-10"
                onClick={() => setShowNotifications(false)}
              ></div>
              <div className="absolute top-full right-0 mt-2 w-80 bg-white border border-slate-200 rounded-xl shadow-xl z-20 overflow-hidden animate-in fade-in slide-in-from-top-2 duration-200">
                <div className="px-4 py-3 border-b border-slate-100 flex items-center justify-between">
                  <h3 className="text-sm font-bold text-slate-900">最新动态</h3>
                  {notifications.length > 0 && (
                    <span className="text-[10px] bg-primary/10 text-primary px-1.5 py-0.5 rounded font-bold">{notifications.length} 条</span>
                  )}
                </div>
                <div className="max-h-[400px] overflow-y-auto">
                  {notifications.length > 0 ? notifications.map((item) => {
                    const style = getActivityStyle(item.type);
                    return (
                      <div key={item.id} className="flex gap-3 items-start p-4 hover:bg-slate-50 transition-colors border-b border-slate-50 last:border-0">
                        <div className={`w-8 h-8 rounded-full ${style.bg} flex items-center justify-center ${style.color} flex-shrink-0`}>
                          <span className="material-symbols-outlined text-[16px]">{style.icon}</span>
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="text-xs text-slate-800 font-bold truncate">{item.title}</p>
                          <p className="text-[11px] text-slate-500 mt-0.5 truncate">{item.description}</p>
                          <p className="text-[10px] text-slate-400 mt-1.5">{item.createdAt}</p>
                        </div>
                      </div>
                    );
                  }) : (
                    <div className="p-8 text-center text-slate-400 text-sm">暂无动态</div>
                  )}
                </div>
              </div>
            </>
          )}
        </div>
        <div className="h-8 w-px bg-slate-200"></div>
        <div className="flex items-center gap-3">
          <div className="text-right hidden sm:block">
            <p className="text-sm font-semibold leading-none">{displayName}</p>
            <p className="text-[10px] text-slate-500 mt-1">{department}</p>
          </div>
          <div className="size-9 rounded-full bg-primary/10 border border-primary/20 flex items-center justify-center">
            <span className="material-symbols-outlined text-primary text-xl">person</span>
          </div>
        </div>
      </div>
    </header>
  );
};