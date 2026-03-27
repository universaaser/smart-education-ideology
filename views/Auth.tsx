import React, { useState } from 'react';
import { authApi, setToken } from '../services/api';
import { useAuth } from '../contexts/AuthContext';

export const Auth: React.FC = () => {
  const { login } = useAuth();
  const [isLogin, setIsLogin] = useState(true);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [formData, setFormData] = useState({
    username: '',
    password: '',
    email: '',
    role: 'TEACHER'
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError('');

    try {
      if (isLogin) {
        const result = await authApi.login(formData.username, formData.password);
        setToken(result.token);
      } else {
        const result = await authApi.register(
          formData.username,
          formData.password,
          formData.email,
          formData.role
        );
        setToken(result.token);
      }
      
      // 成功获取 token 后，通过 bootstrap 接口拉取完整的用户数据和配置
      const bootstrapData = await authApi.bootstrap();
      login(bootstrapData);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : '操作失败，请重试';
      setError(message);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen w-full flex items-center justify-center bg-background-light relative overflow-hidden font-body">
      {/* 背景装饰 */}
      <div className="absolute top-0 left-0 w-full h-full pointer-events-none overflow-hidden">
        <div className="absolute -top-24 -left-24 w-96 h-96 bg-primary/5 rounded-full blur-3xl"></div>
        <div className="absolute top-1/2 -right-24 w-64 h-64 bg-accent-red/5 rounded-full blur-3xl"></div>
      </div>

      <div className="w-full max-w-md p-8 relative z-10">
        {/* Logo */}
        <div className="flex flex-col items-center mb-10">
          <div className="size-16 bg-primary rounded-2xl flex items-center justify-center text-white shadow-2xl shadow-primary/40 mb-4 animate-in zoom-in-50 duration-500">
            <span className="material-symbols-outlined text-4xl">auto_awesome</span>
          </div>
          <h1 className="text-3xl font-bold text-slate-900 font-display tracking-tight">智教思政</h1>
          <p className="text-slate-500 mt-2 text-sm font-medium">智慧教学辅助系统 · 登录入口</p>
        </div>

        {/* 认证卡片 */}
        <div className="bg-white rounded-3xl shadow-2xl shadow-slate-200/60 border border-slate-100 overflow-hidden animate-in fade-in slide-in-from-bottom-8 duration-700">
          <div className="flex border-b border-slate-50">
            <button
              onClick={() => { setIsLogin(true); setError(''); }}
              className={`flex-1 py-4 text-sm font-bold transition-all ${isLogin ? 'text-primary border-b-2 border-primary' : 'text-slate-400 hover:text-slate-600'}`}
            >
              登 录
            </button>
            <button
              onClick={() => { setIsLogin(false); setError(''); }}
              className={`flex-1 py-4 text-sm font-bold transition-all ${!isLogin ? 'text-primary border-b-2 border-primary' : 'text-slate-400 hover:text-slate-600'}`}
            >
              注 册
            </button>
          </div>

          <form onSubmit={handleSubmit} className="p-8 space-y-5">
            {/* 错误提示 */}
            {error && (
              <div className="bg-red-50 border border-red-200 text-red-600 text-sm px-4 py-3 rounded-xl flex items-center gap-2">
                <span className="material-symbols-outlined text-sm">error</span>
                {error}
              </div>
            )}

            {!isLogin && (
              <>
                <div className="flex gap-4 mb-2">
                  <button
                    type="button"
                    onClick={() => setFormData({ ...formData, role: 'TEACHER' })}
                    className={`flex-1 flex items-center justify-center gap-2 py-2.5 rounded-xl text-sm font-bold border transition-all ${
                      formData.role === 'TEACHER'
                        ? 'border-primary bg-primary/10 text-primary shadow-sm'
                        : 'border-slate-200 text-slate-500 hover:bg-slate-50'
                    }`}
                  >
                    <span className="material-symbols-outlined text-[18px]">school</span>
                    教师注册
                  </button>
                  <button
                    type="button"
                    onClick={() => setFormData({ ...formData, role: 'STUDENT' })}
                    className={`flex-1 flex items-center justify-center gap-2 py-2.5 rounded-xl text-sm font-bold border transition-all ${
                      formData.role === 'STUDENT'
                        ? 'border-primary bg-primary/10 text-primary shadow-sm'
                        : 'border-slate-200 text-slate-500 hover:bg-slate-50'
                    }`}
                  >
                    <span className="material-symbols-outlined text-[18px]">person_book</span>
                    学生注册
                  </button>
                </div>

                <div className="space-y-1.5">
                  <label className="text-[11px] font-bold text-slate-400 uppercase tracking-wider ml-1">
                    {formData.role === 'TEACHER' ? '教工邮箱' : '联系邮箱'}
                  </label>
                  <div className="relative group">
                    <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 text-xl group-focus-within:text-primary transition-colors">mail</span>
                    <input
                      required
                      type="email"
                      className="w-full h-11 pl-10 pr-4 rounded-xl border border-slate-200 bg-slate-50 focus:bg-white focus:ring-4 focus:ring-primary/10 focus:border-primary outline-none transition-all text-sm"
                      placeholder={formData.role === 'TEACHER' ? 'example@university.edu.cn' : 'student@stu.university.edu.cn'}
                      value={formData.email}
                      onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                    />
                  </div>
                </div>
              </>
            )}

            <div className="space-y-1.5">
              <label className="text-[11px] font-bold text-slate-400 uppercase tracking-wider ml-1">
                {formData.role === 'TEACHER' ? '用户名 / 教工号' : '用户名 / 学号'}
              </label>
              <div className="relative group">
                <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 text-xl group-focus-within:text-primary transition-colors">person</span>
                <input
                  required
                  type="text"
                  className="w-full h-11 pl-10 pr-4 rounded-xl border border-slate-200 bg-slate-50 focus:bg-white focus:ring-4 focus:ring-primary/10 focus:border-primary outline-none transition-all text-sm"
                  placeholder="请输入您的账号"
                  value={formData.username}
                  onChange={(e) => setFormData({ ...formData, username: e.target.value })}
                />
              </div>
            </div>

            <div className="space-y-1.5">
              <div className="flex justify-between items-center">
                <label className="text-[11px] font-bold text-slate-400 uppercase tracking-wider ml-1">密码</label>
                {isLogin && (
                  <button type="button" className="text-[10px] text-primary hover:underline font-bold">忘记密码?</button>
                )}
              </div>
              <div className="relative group">
                <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 text-xl group-focus-within:text-primary transition-colors">lock</span>
                <input
                  required
                  type="password"
                  className="w-full h-11 pl-10 pr-4 rounded-xl border border-slate-200 bg-slate-50 focus:bg-white focus:ring-4 focus:ring-primary/10 focus:border-primary outline-none transition-all text-sm"
                  placeholder="••••••••"
                  value={formData.password}
                  onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                />
              </div>
            </div>

            <button
              disabled={isLoading}
              type="submit"
              className="w-full h-12 bg-primary hover:bg-blue-700 text-white rounded-xl font-bold shadow-lg shadow-primary/30 transition-all flex items-center justify-center gap-3 active:scale-95 disabled:opacity-70 disabled:cursor-not-allowed"
            >
              {isLoading ? (
                <div className="size-5 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>
              ) : (
                <>
                  <span>{isLogin ? '立即登录' : '创建账户'}</span>
                  <span className="material-symbols-outlined text-[18px]">arrow_forward</span>
                </>
              )}
            </button>
          </form>
        </div>

        <p className="mt-8 text-center text-xs text-slate-400">
          登录即代表您同意 <a href="#" className="text-primary hover:underline">《服务协议》</a> 与 <a href="#" className="text-primary hover:underline">《隐私政策》</a>
        </p>
      </div>
    </div>
  );
};