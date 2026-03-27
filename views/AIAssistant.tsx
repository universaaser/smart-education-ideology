import React, { useState, useRef, useEffect } from 'react';
import { chatApi, ChatMessageInfo } from '../services/api';

/** 支持选择的 AI 模型列表 */
const AI_MODELS = [
  { value: 'deepseek', label: 'DeepSeek', icon: 'psychology' },
  { value: 'openai', label: 'OpenAI', icon: 'auto_awesome' },
  { value: 'gemini', label: 'Gemini', icon: 'stars' },
] as const;

type AiModelValue = typeof AI_MODELS[number]['value'];

const MODEL_STORAGE_KEY = 'smart_edu_ai_model';

/** 消息气泡 */
interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: string;
}

/**
 * AI 助教 — 纯实时对话模式
 *
 * NOTE: 支持多 AI 模型选择；选择持久化至 localStorage；
 * 会话在发送第一条消息时自动创建，刷新页面后丢失。
 */
export const AIAssistant: React.FC = () => {
  const [messages, setMessages] = useState<Message[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [sending, setSending] = useState(false);
  const [sessionId, setSessionId] = useState<number | null>(null);
  const [selectedModel, setSelectedModel] = useState<AiModelValue>(() => {
    // 从 localStorage 读取上次选择的模型，默认为 deepseek
    const stored = localStorage.getItem(MODEL_STORAGE_KEY) as AiModelValue | null;
    return stored && AI_MODELS.some(m => m.value === stored) ? stored : 'deepseek';
  });
  const [showModelMenu, setShowModelMenu] = useState(false);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const modelMenuRef = useRef<HTMLDivElement>(null);

  /** 滚动到最新消息 */
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  /** 点击模型菜单外侧时关闭 */
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (modelMenuRef.current && !modelMenuRef.current.contains(e.target as Node)) {
        setShowModelMenu(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  /** 格式化当前时间 */
  const now = () => new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });

  /** 切换模型：持久化并重置当前会话 */
  const handleModelChange = (model: AiModelValue) => {
    setSelectedModel(model);
    localStorage.setItem(MODEL_STORAGE_KEY, model);
    setShowModelMenu(false);
    // 切换模型时重置会话，确保新会话使用正确模型
    setSessionId(null);
    setMessages([]);
  };

  /** 确保会话已创建（携带所选模型参数） */
  const ensureSession = async (): Promise<number> => {
    if (sessionId) return sessionId;
    try {
      // NOTE: aiModel 字段对应后端 session.ai_model，且后端 ai.provider 会根据此值路由调用
      const session = await chatApi.createSession('新对话', 1, selectedModel);
      setSessionId(session.id);
      return session.id;
    } catch {
      throw new Error('无法创建会话');
    }
  };

  /** 发送消息 */
  const handleSend = async () => {
    const text = inputValue.trim();
    if (!text || sending) return;

    // 用户消息
    const userMsg: Message = {
      id: `user-${Date.now()}`,
      role: 'user',
      content: text,
      timestamp: now(),
    };
    setMessages(prev => [...prev, userMsg]);
    setInputValue('');
    setSending(true);

    try {
      const sid = await ensureSession();
      const reply: ChatMessageInfo = await chatApi.sendMessage(sid, text);

      const assistantMsg: Message = {
        id: `assistant-${reply.id || Date.now()}`,
        role: 'assistant',
        content: reply.content,
        timestamp: now(),
      };
      setMessages(prev => [...prev, assistantMsg]);
    } catch {
      const errorMsg: Message = {
        id: `error-${Date.now()}`,
        role: 'assistant',
        content: '抱歉，AI 暂时无法响应，请稍后重试。',
        timestamp: now(),
      };
      setMessages(prev => [...prev, errorMsg]);
    } finally {
      setSending(false);
    }
  };

  /** 键盘事件 */
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const currentModel = AI_MODELS.find(m => m.value === selectedModel)!;

  return (
    <div className="flex-1 flex flex-col h-full bg-background-light overflow-hidden">
      {/* 顶部标题栏，含模型选择器 */}
      <div className="shrink-0 px-6 py-4 border-b border-slate-200 bg-white">
        <div className="flex items-center gap-3">
          <div className="size-10 rounded-xl bg-gradient-to-br from-blue-600 to-purple-600 flex items-center justify-center text-white shadow-sm">
            <span className="material-symbols-outlined text-[20px]">smart_toy</span>
          </div>
          <div>
            <h2 className="font-display font-bold text-slate-900 text-base">AI 思政助教</h2>
            <p className="text-[11px] text-slate-400 mt-0.5">基于大语言模型的思政教学辅助</p>
          </div>

          {/* 模型选择器 */}
          <div className="ml-auto flex items-center gap-3">
            <div className="relative" ref={modelMenuRef}>
              <button
                id="ai-model-selector"
                onClick={() => setShowModelMenu(prev => !prev)}
                className="flex items-center gap-2 px-3 py-1.5 rounded-lg border border-slate-200 bg-slate-50 hover:bg-white hover:border-primary/40 transition-all text-sm text-slate-600 font-medium"
              >
                <span className="material-symbols-outlined text-[16px] text-primary">{currentModel.icon}</span>
                <span>{currentModel.label}</span>
                <span className="material-symbols-outlined text-[14px] text-slate-400">
                  {showModelMenu ? 'expand_less' : 'expand_more'}
                </span>
              </button>

              {/* 下拉菜单 */}
              {showModelMenu && (
                <div className="absolute right-0 top-full mt-1.5 w-40 rounded-xl border border-slate-200 bg-white shadow-lg z-50 overflow-hidden">
                  {AI_MODELS.map(model => (
                    <button
                      key={model.value}
                      onClick={() => handleModelChange(model.value)}
                      className={`w-full flex items-center gap-2.5 px-4 py-2.5 text-sm hover:bg-slate-50 transition-colors ${
                        selectedModel === model.value
                          ? 'text-primary font-semibold bg-blue-50'
                          : 'text-slate-700'
                      }`}
                    >
                      <span className={`material-symbols-outlined text-[16px] ${selectedModel === model.value ? 'text-primary' : 'text-slate-400'}`}>
                        {model.icon}
                      </span>
                      {model.label}
                      {selectedModel === model.value && (
                        <span className="material-symbols-outlined text-[14px] text-primary ml-auto">check</span>
                      )}
                    </button>
                  ))}
                </div>
              )}
            </div>

            <div className="flex items-center gap-1 text-emerald-500">
              <span className="size-2 bg-emerald-500 rounded-full animate-pulse"></span>
              <span className="text-[11px]">在线</span>
            </div>
          </div>
        </div>
      </div>

      {/* 聊天消息区域 */}
      <div className="flex-1 overflow-y-auto px-6 py-6 space-y-4">
        {messages.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full text-center">
            <div className="size-16 rounded-2xl bg-gradient-to-br from-blue-100 to-purple-100 flex items-center justify-center mb-4">
              <span className="material-symbols-outlined text-primary text-3xl">chat</span>
            </div>
            <h3 className="text-lg font-display font-bold text-slate-700">开始对话</h3>
            <p className="text-sm text-slate-400 mt-2 max-w-sm">
              当前模型：<span className="text-primary font-medium">{currentModel.label}</span>，
              可在右上角切换模型。
            </p>
            <div className="flex flex-wrap gap-2 mt-6 max-w-md justify-center">
              {['帮我分析物联网的思政元素', '如何将家国情怀融入计算机课程', '推荐课程思政案例'].map(q => (
                <button
                  key={q}
                  onClick={() => setInputValue(q)}
                  className="px-3 py-2 bg-white border border-slate-200 rounded-lg text-xs text-slate-600 hover:bg-primary/5 hover:border-primary/30 hover:text-primary transition-colors"
                >
                  {q}
                </button>
              ))}
            </div>
          </div>
        ) : (
          messages.map((msg) => (
            <div key={msg.id} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
              <div className={`max-w-[70%] ${msg.role === 'user' ? 'order-2' : ''}`}>
                <div className={`px-4 py-3 rounded-2xl text-sm leading-relaxed whitespace-pre-wrap ${msg.role === 'user'
                    ? 'bg-primary text-white rounded-br-md'
                    : 'bg-white border border-slate-100 text-slate-700 rounded-bl-md shadow-sm'
                  }`}>
                  {msg.content}
                </div>
                <p className={`text-[10px] text-slate-400 mt-1.5 ${msg.role === 'user' ? 'text-right' : 'text-left'}`}>
                  {msg.timestamp}
                </p>
              </div>
            </div>
          ))
        )}

        {/* 正在输入指示 */}
        {sending && (
          <div className="flex justify-start">
            <div className="bg-white border border-slate-100 rounded-2xl rounded-bl-md px-4 py-3 shadow-sm">
              <div className="flex gap-1.5 items-center">
                <span className="size-2 bg-slate-300 rounded-full animate-bounce" style={{ animationDelay: '0ms' }}></span>
                <span className="size-2 bg-slate-300 rounded-full animate-bounce" style={{ animationDelay: '150ms' }}></span>
                <span className="size-2 bg-slate-300 rounded-full animate-bounce" style={{ animationDelay: '300ms' }}></span>
              </div>
            </div>
          </div>
        )}
        <div ref={messagesEndRef}></div>
      </div>

      {/* 输入框 */}
      <div className="shrink-0 px-6 py-4 border-t border-slate-200 bg-white">
        <div className="flex items-end gap-3 max-w-4xl mx-auto">
          <div className="flex-1 relative">
            <textarea
              ref={inputRef}
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="请输入您的问题... (Shift+Enter 换行)"
              rows={1}
              className="w-full resize-none rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/10 focus:bg-white transition-all max-h-32"
            />
          </div>
          <button
            onClick={handleSend}
            disabled={!inputValue.trim() || sending}
            className="shrink-0 size-11 bg-primary text-white rounded-xl flex items-center justify-center hover:bg-blue-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors shadow-sm"
          >
            <span className="material-symbols-outlined text-[20px]">send</span>
          </button>
        </div>
      </div>
    </div>
  );
};