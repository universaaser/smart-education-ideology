import React, { useState, useRef, useEffect } from 'react';
import { chatApi, ChatMessageInfo } from '../services/api';
import { Input, Button, Dropdown, Space, Typography, Avatar, Spin } from 'antd';
import {
  RobotOutlined, BulbOutlined, SendOutlined, UserOutlined,
  DownOutlined, CheckOutlined, LoadingOutlined, MessageOutlined
} from '@ant-design/icons';
import { useAuth } from '../contexts/AuthContext';

const { Text, Title, Paragraph } = Typography;

const AI_MODELS = [
  { value: 'default', label: '默认', icon: <RobotOutlined /> },
  { value: 'deepseek', label: 'DeepSeek', icon: <RobotOutlined /> },
  { value: 'openai', label: 'OpenAI', icon: <BulbOutlined /> },
  { value: 'gemini', label: 'Gemini', icon: <RobotOutlined /> },
] as const;

type AiModelValue = typeof AI_MODELS[number]['value'];
const MODEL_STORAGE_KEY = 'smart_edu_ai_model';

interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: string;
}

export const AIAssistant: React.FC = () => {
  const { currentUser } = useAuth();
  const [messages, setMessages] = useState<Message[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [sending, setSending] = useState(false);
  const [sessionId, setSessionId] = useState<number | null>(null);
  const [selectedModel, setSelectedModel] = useState<AiModelValue>(() => {
    const stored = localStorage.getItem(MODEL_STORAGE_KEY) as AiModelValue | null;
    return stored && AI_MODELS.some(m => m.value === stored) ? stored : 'default';
  });

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<any>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, sending]);

  const now = () => new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });

  const handleModelChange = (model: AiModelValue) => {
    setSelectedModel(model);
    localStorage.setItem(MODEL_STORAGE_KEY, model);
    setSessionId(null);
    setMessages([]);
  };

  const ensureSession = async (): Promise<number> => {
    if (sessionId) return sessionId;
    try {
      /**
       * 使用当前登录用户的 ID 归属会话，避免固定 userId=1 导致会话串号。
       * 当鉴权数据尚未就绪时保留兜底值 1，确保界面仍可回退运行。
       */
      const session = await chatApi.createSession('新对话', currentUser?.id ?? 1, selectedModel);
      setSessionId(session.id);
      return session.id;
    } catch {
      throw new Error('无法创建会话');
    }
  };

  const handleSend = async () => {
    const text = inputValue.trim();
    if (!text || sending) return;

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
      // 回到输入框焦点
      setTimeout(() => {
        inputRef.current?.focus();
      }, 50);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const currentModel = AI_MODELS.find(m => m.value === selectedModel)!;

  const getMenuProps = () => {
    return {
      items: AI_MODELS.map(model => ({
        key: model.value,
        label: (
          <Space>
            <span style={{ color: selectedModel === model.value ? 'var(--color-primary)' : 'inherit' }}>{model.icon}</span>
            <span style={{ color: selectedModel === model.value ? 'var(--color-primary)' : 'inherit', fontWeight: selectedModel === model.value ? 600 : 'normal' }}>
              {model.label}
            </span>
            {selectedModel === model.value && <CheckOutlined style={{ color: 'var(--color-primary)', marginLeft: 8 }} />}
          </Space>
        ),
        onClick: () => handleModelChange(model.value)
      }))
    };
  };

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100%', backgroundColor: 'var(--color-bg-muted)', overflow: 'hidden' }}>
      
      {/* Header */}
      <div style={{ padding: '16px 24px', background: 'var(--color-bg-panel)', borderBottom: '1px solid var(--color-border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', zIndex: 10 }}>
        <Space size={16}>
          <Avatar 
            size={40} 
            icon={<RobotOutlined />} 
            style={{ backgroundImage: 'linear-gradient(135deg, var(--color-primary) 0%, #722ed1 100%)', display: 'flex', alignItems: 'center', justifyContent: 'center' }} 
          />
          <div>
            <Title level={5} style={{ margin: 0, fontFamily: "'Lexend', sans-serif" }}>AI 思政助教</Title>
            <Text type="secondary" style={{ fontSize: 12 }}>基于大语言模型的思政教学辅助</Text>
          </div>
        </Space>

        <Space>
          <Dropdown menu={getMenuProps()} placement="bottomRight" trigger={['click']}>
            <Button style={{ display: 'flex', alignItems: 'center' }}>
              <span style={{ color: 'var(--color-primary)', marginRight: 4 }}>{currentModel.icon}</span>
              {currentModel.label}
              <DownOutlined style={{ fontSize: 10, color: '#8c8c8c', marginLeft: 4 }} />
            </Button>
          </Dropdown>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginLeft: 8 }}>
            <div style={{ width: 8, height: 8, borderRadius: '50%', backgroundColor: 'var(--color-success)' }} />
            <Text type="secondary" style={{ fontSize: 13, color: 'var(--color-success)' }}>在线</Text>
          </div>
        </Space>
      </div>

      {/* Chat Area */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '24px 10%', display: 'flex', flexDirection: 'column', gap: 16 }}>
        {messages.length === 0 ? (
          <div style={{ margin: 'auto', textAlign: 'center', maxWidth: 400 }}>
            <Avatar 
              size={64} 
              icon={<MessageOutlined />} 
              style={{ backgroundColor: 'var(--color-primary-soft)', color: 'var(--color-primary)', marginBottom: 24 }} 
            />
            <Title level={3} style={{ fontFamily: "'Lexend', sans-serif" }}>开始对话</Title>
            <Paragraph type="secondary" style={{ marginBottom: 32 }}>
              当前模型：<Text strong style={{ color: 'var(--color-primary)' }}>{currentModel.label}</Text>，可在右上角切换模型。
            </Paragraph>
            <Space size={12} wrap style={{ justifyContent: 'center' }}>
              {['帮我分析物联网的思政元素', '如何将家国情怀融入计算机课程', '推荐课程思政案例'].map(q => (
                <Button key={q} onClick={() => setInputValue(q)} shape="round" style={{ color: '#595959' }}>
                  {q}
                </Button>
              ))}
            </Space>
          </div>
        ) : (
          messages.map((msg) => (
            <div key={msg.id} style={{ display: 'flex', justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start' }}>
              <div style={{ 
                maxWidth: '75%', 
                display: 'flex', 
                flexDirection: 'column', 
                alignItems: msg.role === 'user' ? 'flex-end' : 'flex-start' 
              }}>
                <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12, flexDirection: msg.role === 'user' ? 'row-reverse' : 'row' }}>
                  <Avatar 
                    size={36} 
                    icon={msg.role === 'user' ? <UserOutlined /> : <RobotOutlined />} 
                    style={{ 
                      backgroundColor: msg.role === 'user' ? '#bae0ff' : '#1677ff', 
                      color: msg.role === 'user' ? '#0958d9' : '#fff' 
                    }} 
                  />
                  <div style={{
                    padding: '12px 16px',
                    borderRadius: 16,
                    borderTopRightRadius: msg.role === 'user' ? 4 : 16,
                    borderTopLeftRadius: msg.role === 'assistant' ? 4 : 16,
                    backgroundColor: msg.role === 'user' ? 'var(--color-primary)' : 'var(--color-bg-panel)',
                    color: msg.role === 'user' ? '#fff' : '#1f2937',
                    boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
                    lineHeight: 1.6,
                    fontSize: 14,
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                    border: msg.role === 'assistant' ? '1px solid var(--color-border)' : 'none'
                  }}>
                    {msg.content}
                  </div>
                </div>
                <Text type="secondary" style={{ fontSize: 11, marginTop: 6, opacity: 0.8, padding: '0 48px' }}>
                  {msg.timestamp}
                </Text>
              </div>
            </div>
          ))
        )}

        {/* Loading Indicator */}
        {sending && (
          <div style={{ display: 'flex', justifyContent: 'flex-start', marginTop: 8 }}>
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
              <Avatar size={36} icon={<RobotOutlined />} style={{ backgroundColor: 'var(--color-primary)', color: '#fff' }} />
              <div style={{
                padding: '12px 20px',
                borderRadius: 16, borderTopLeftRadius: 4,
                backgroundColor: 'var(--color-bg-panel)', border: '1px solid var(--color-border)',
                boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
                display: 'flex', alignItems: 'center'
              }}>
                <Spin indicator={<LoadingOutlined style={{ fontSize: 18, color: 'var(--color-primary)' }} spin />} />
              </div>
            </div>
          </div>
        )}
        <div ref={messagesEndRef} style={{ height: 1 }} />
      </div>

      {/* Input Area */}
      <div style={{ padding: '20px 10%', background: 'var(--color-bg-panel)', borderTop: '1px solid var(--color-border)', zIndex: 10 }}>
        <div style={{ maxWidth: 1000, margin: '0 auto', display: 'flex', gap: 12, alignItems: 'flex-end' }}>
          <Input.TextArea
            ref={inputRef}
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="请输入您的问题... (Shift+Enter 换行)"
            autoSize={{ minRows: 1, maxRows: 6 }}
            style={{ 
              borderRadius: 12, 
              padding: '12px 16px', 
              boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
              border: '1px solid #e2e8f0',
              fontSize: 14
            }}
          />
          <Button
            type="primary"
            size="large"
            icon={<SendOutlined />}
            onClick={handleSend}
            disabled={!inputValue.trim() || sending}
            style={{ borderRadius: 12, height: 46, width: 46, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
          />
        </div>
      </div>
    </div>
  );
};
