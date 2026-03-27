import React, { useRef, useState } from 'react';
import { View, NavItem, CurrentUser } from '../types';
import { userApi } from '../services/api';
import { useAuth } from '../contexts/AuthContext';

interface SidebarProps {
  currentView: View;
  onChangeView: (view: View) => void;
  onLogout: () => void;
  user?: CurrentUser | null;
  /** 头像更新后回调，用于在父组件同步更新用户信息 */
  onAvatarChange?: (avatarUrl: string) => void;
}

const ALL_NAV_ITEMS: NavItem[] = [
  { id: View.DASHBOARD, label: '仪表盘', icon: 'dashboard' },
  { id: View.STUDENT_HOME, label: '学习首页', icon: 'home' },
  { id: View.KNOWLEDGE_GRAPH, label: '知识图谱', icon: 'hub' },
  { id: View.AI_ASSISTANT, label: 'AI助教', icon: 'smart_toy', isBeta: true },
  { id: View.RESOURCE_UPLOAD, label: '资源上传', icon: 'cloud_upload' },
  { id: View.COURSE_LIBRARY, label: '课程库', icon: 'library_books' },
];

export const Sidebar: React.FC<SidebarProps> = ({ currentView, onChangeView, onLogout, user, onAvatarChange }) => {
  const { roleUi } = useAuth();
  const displayName = user?.realName || user?.username || '用户';
  const roleText = user?.department
    ? `${user.department} · ${roleUi?.roleLabel || user.role || '教师'}`
    : roleUi?.roleLabel || user?.role || '教师';

  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [avatarUrl, setAvatarUrl] = useState(user?.avatar || '');

  // 基于角色白名单过滤左侧菜单
  const navItems = ALL_NAV_ITEMS.filter(item => 
    roleUi?.allowedViews?.includes(item.id)
  );

  /** 点击头像触发文件选择 */
  const handleAvatarClick = () => {
    fileInputRef.current?.click();
  };

  /** 上传头像 */
  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setUploading(true);
    try {
      const result = await userApi.uploadAvatar(file);
      setAvatarUrl(result.avatarUrl);
      onAvatarChange?.(result.avatarUrl);
    } catch {
      // NOTE: 上传失败静默处理
    } finally {
      setUploading(false);
      // 清空 input 值，允许再次选择同一文件
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

  return (
    <aside className="w-64 bg-sidebar-bg text-white flex flex-col shrink-0 h-full">
      <div className="p-6 flex items-center gap-3">
        <div className="size-9 bg-primary rounded-lg flex items-center justify-center text-white shrink-0 shadow-lg shadow-primary/20">
          <span className="material-symbols-outlined text-2xl">auto_awesome</span>
        </div>
        <div className="overflow-hidden">
          <h1 className="text-white text-lg font-bold leading-tight font-display">智教思政</h1>
          <p className="text-slate-400 text-[11px] truncate">智慧教学辅助系统</p>
        </div>
      </div>

      <nav className="flex-1 px-4 py-4 flex flex-col gap-1">
        {navItems.map((item) => (
          <button
            key={item.id}
            onClick={() => onChangeView(item.id)}
            className={`flex items-center gap-3 px-3 py-2.5 rounded-lg transition-all w-full text-left group ${currentView === item.id
              ? 'bg-primary text-white shadow-lg shadow-primary/20'
              : 'text-slate-400 hover:bg-white/5 hover:text-white'
              }`}
          >
            <span className={`material-symbols-outlined text-xl ${currentView === item.id ? '' : 'group-hover:scale-110 transition-transform'}`}>
              {item.icon}
            </span>
            <span className="text-sm font-medium flex-1">{item.label}</span>
            {item.isBeta && (
              <span className="text-[10px] bg-red-500 text-white px-1.5 py-0.5 rounded font-bold leading-none">
                BETA
              </span>
            )}
          </button>
        ))}
      </nav>

      <div className="mt-auto p-4 border-t border-slate-800">
        <div className="flex items-center gap-3 px-2 py-2">
          {/* 可点击上传头像 */}
          <button
            onClick={handleAvatarClick}
            className="size-10 rounded-full border border-slate-700 bg-primary/10 flex items-center justify-center shrink-0 overflow-hidden hover:ring-2 hover:ring-primary/40 transition-all cursor-pointer group relative"
            title="点击更换头像"
          >
            {avatarUrl ? (
              <img src={avatarUrl} alt="头像" className="size-full object-cover" />
            ) : (
              <span className="material-symbols-outlined text-primary text-xl">person</span>
            )}
            {/* 悬浮遮罩 */}
            <div className="absolute inset-0 bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
              {uploading ? (
                <div className="size-4 border-2 border-white/40 border-t-white rounded-full animate-spin"></div>
              ) : (
                <span className="material-symbols-outlined text-white text-sm">photo_camera</span>
              )}
            </div>
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            onChange={handleFileChange}
            className="hidden"
          />
          <div className="overflow-hidden">
            <p className="text-white text-sm font-semibold truncate">{displayName}</p>
            <p className="text-slate-500 text-[11px] truncate">{roleText}</p>
          </div>
        </div>
        <button
          onClick={onLogout}
          className="w-full mt-4 flex items-center justify-center gap-2 bg-slate-800/50 hover:bg-slate-800 text-slate-400 hover:text-white py-2 rounded-lg text-xs font-medium transition-all"
        >
          <span className="material-symbols-outlined text-sm">logout</span>
          退出登录
        </button>
      </div>
    </aside>
  );
};