import React, { useEffect, useMemo, useRef, useState } from 'react';
import { chatApi, ChatCitationInfo, ChatResponseInfo, ChatMessageInfo, ChatSessionInfo } from '../services/api';
import { Input, Button, Space, Typography, Avatar, Spin, Tag, List, Empty, Popconfirm } from 'antd';
import {
  RobotOutlined, SendOutlined, UserOutlined,
  LoadingOutlined, MessageOutlined
} from '@ant-design/icons';
import { useAuth } from '../contexts/AuthContext';

const { Text, Title, Paragraph } = Typography;

const ACTIVE_PROVIDER = 'openai';
const ACTIVE_PROVIDER_LABEL = 'Local OpenAI-Compatible';
const ACTIVE_MODEL_LABEL = 'gpt-5.4';
const ACTIVE_ENDPOINT_LABEL = 'http://localhost:8317/v1';

interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: string;
  citations?: ChatCitationInfo[];
  retrievalStatus?: string;
}

interface AIAssistantProps {
  onQuestionSubmit?: (question: string) => void;
}

export const AIAssistant: React.FC<AIAssistantProps> = ({ onQuestionSubmit }) => {
  const { currentUser } = useAuth();
  const [messages, setMessages] = useState<Message[]>([]);
  const [sessions, setSessions] = useState<ChatSessionInfo[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [sending, setSending] = useState(false);
  const [loadingSessions, setLoadingSessions] = useState(false);
  const [sessionId, setSessionId] = useState<number | null>(null);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<any>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, sending]);

  const loadSessions = async () => {
    const currentUserId = currentUser?.id;
    if (!currentUserId) {
      return;
    }
    setLoadingSessions(true);
    try {
      const data = await chatApi.getSessions(currentUserId);
      setSessions(data || []);
      if (!sessionId && data && data.length > 0) {
        setSessionId(data[0].id);
        const detail = await chatApi.getSessionDetail(data[0].id);
        setMessages(mapSessionMessages(detail.messages));
      }
    } catch {
      setSessions([]);
    } finally {
      setLoadingSessions(false);
    }
  };

  useEffect(() => {
    loadSessions();
  }, [currentUser?.id]);

  const now = () => new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });

  const mapSessionMessages = (sessionMessages: ChatMessageInfo[]): Message[] => sessionMessages.map((item) => ({
    id: String(item.id),
    role: item.role === 'USER' ? 'user' : 'assistant',
    content: item.content,
    timestamp: new Date(item.createdAt).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }),
  }));

  const refreshSessions = async (activeSessionId?: number) => {
    const currentUserId = currentUser?.id;
    if (!currentUserId) {
      return;
    }
    const data = await chatApi.getSessions(currentUserId);
    setSessions(data || []);
    if (activeSessionId) {
      const current = data?.find(item => item.id === activeSessionId);
      if (current) {
        setSessionId(current.id);
      }
    }
  };

  const ensureSession = async (): Promise<number> => {
    if (sessionId) {
      return sessionId;
    }

    const currentUserId = currentUser?.id;
    if (!currentUserId) {
      throw new Error('Current user is not available');
    }

    try {
      const session = await chatApi.createSession('New Chat', currentUserId, ACTIVE_PROVIDER);
      setSessionId(session.id);
      await refreshSessions(session.id);
      return session.id;
    } catch (error) {
      throw new Error(error instanceof Error ? error.message : 'Failed to create chat session');
    }
  };

  const handleSend = async () => {
    const text = inputValue.trim();
    if (!text || sending) {
      return;
    }

    const userMessage: Message = {
      id: `user-${Date.now()}`,
      role: 'user',
      content: text,
      timestamp: now(),
    };
    setMessages(previous => [...previous, userMessage]);
    setInputValue('');
    setSending(true);

    try {
      onQuestionSubmit?.(text);
      const currentSessionId = await ensureSession();
      const reply: ChatResponseInfo = await chatApi.sendMessage(currentSessionId, text);

      const assistantMessage: Message = {
        id: `assistant-${reply.message.id || Date.now()}`,
        role: 'assistant',
        content: reply.message.content,
        timestamp: now(),
        citations: reply.citations || [],
        retrievalStatus: reply.retrievalStatus,
      };
      setMessages(previous => [...previous, assistantMessage]);
      await refreshSessions(currentSessionId);
    } catch (error) {
      const errorText = error instanceof Error && error.message
        ? error.message
        : 'The AI service is temporarily unavailable. Please try again later.';
      const errorMessage: Message = {
        id: `error-${Date.now()}`,
        role: 'assistant',
        content: errorText,
        timestamp: now(),
      };
      setMessages(previous => [...previous, errorMessage]);
    } finally {
      setSending(false);
      setTimeout(() => {
        inputRef.current?.focus();
      }, 50);
    }
  };

  const handleSelectSession = async (targetSessionId: number) => {
    setSessionId(targetSessionId);
    try {
      const detail = await chatApi.getSessionDetail(targetSessionId);
      setMessages(mapSessionMessages(detail.messages));
    } catch {
      setMessages([]);
    }
  };

  const handleDeleteSession = async (targetSessionId: number) => {
    await chatApi.deleteSession(targetSessionId);
    if (sessionId === targetSessionId) {
      setSessionId(null);
      setMessages([]);
    }
    await loadSessions();
  };

  const currentSessionTitle = useMemo(() => {
    const currentSession = sessions.find(item => item.id === sessionId);
    return currentSession?.title || 'New Chat';
  }, [sessionId, sessions]);

  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      handleSend();
    }
  };

  const retrievalText = (status?: string) => {
    if (status === 'FOUND') return 'Knowledge base evidence found';
    if (status === 'WEAK_MATCH') return 'Weak knowledge base match';
    if (status === 'NO_CONTEXT') return 'No reliable knowledge base source';
    return '';
  };

  const retrievalColor = (status?: string) => {
    if (status === 'FOUND') return 'green';
    if (status === 'WEAK_MATCH') return 'orange';
    if (status === 'NO_CONTEXT') return 'red';
    return 'default';
  };

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100%', backgroundColor: 'var(--color-bg-muted)', overflow: 'hidden' }}>
      <div
        style={{
          padding: '16px 24px',
          background: 'var(--color-bg-panel)',
          borderBottom: '1px solid var(--color-border)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          zIndex: 10
        }}
      >
        <Space size={16}>
          <Avatar
            size={40}
            icon={<RobotOutlined />}
            style={{ backgroundImage: 'linear-gradient(135deg, var(--color-primary) 0%, #722ed1 100%)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
          />
          <div>
            <Title level={5} style={{ margin: 0, fontFamily: "'Lexend', sans-serif" }}>AI Teaching Assistant</Title>
            <Text type="secondary" style={{ fontSize: 12 }}>{currentSessionTitle}</Text>
          </div>
        </Space>

        <Space size={8} wrap>
          <Tag color="blue">{ACTIVE_PROVIDER_LABEL}</Tag>
          <Tag color="geekblue">{ACTIVE_MODEL_LABEL}</Tag>
          <Tag>{ACTIVE_ENDPOINT_LABEL}</Tag>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginLeft: 8 }}>
            <div style={{ width: 8, height: 8, borderRadius: '50%', backgroundColor: 'var(--color-success)' }} />
            <Text type="secondary" style={{ fontSize: 13, color: 'var(--color-success)' }}>Online</Text>
          </div>
        </Space>
      </div>

      <div style={{ flex: 1, display: 'grid', gridTemplateColumns: '280px minmax(0, 1fr)', gridTemplateRows: 'minmax(0, 1fr) auto', minHeight: 0 }}>
        <div style={{ gridRow: '1 / span 2', borderRight: '1px solid var(--color-border)', background: 'var(--color-bg-panel)', overflowY: 'auto', padding: 16 }}>
          <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 12 }}>
            <Text strong>History</Text>
            <Button size="small" onClick={() => { setSessionId(null); setMessages([]); }}>New Chat</Button>
          </Space>
          {loadingSessions ? (
            <Spin />
          ) : sessions.length > 0 ? (
            <List
              dataSource={sessions}
              renderItem={item => (
                <List.Item
                  style={{
                    cursor: 'pointer',
                    padding: '10px 12px',
                    borderRadius: 10,
                    marginBottom: 8,
                    background: item.id === sessionId ? 'var(--color-primary-soft)' : 'transparent',
                    border: '1px solid var(--color-border)',
                  }}
                  onClick={() => handleSelectSession(item.id)}
                  actions={[
                    <Popconfirm key="delete" title="Delete this chat?" onConfirm={() => handleDeleteSession(item.id)}>
                      <Button type="link" danger size="small" onClick={event => event.stopPropagation()}>Delete</Button>
                    </Popconfirm>,
                  ]}
                >
                  <List.Item.Meta
                    avatar={<Avatar icon={<MessageOutlined />} style={{ backgroundColor: 'var(--color-primary-soft)', color: 'var(--color-primary)' }} />}
                    title={<Text strong style={{ fontSize: 14 }}>{item.title}</Text>}
                    description={<Text type="secondary" style={{ fontSize: 12 }}>{new Date(item.updatedAt).toLocaleString()}</Text>}
                  />
                </List.Item>
              )}
            />
          ) : (
            <Empty description="No chat history" />
          )}
        </div>

        <div style={{ gridColumn: 2, gridRow: 1, minWidth: 0, overflowY: 'auto', padding: '24px 10%', display: 'flex', flexDirection: 'column', gap: 16 }}>
          {messages.length === 0 ? (
            <div style={{ margin: 'auto', textAlign: 'center', maxWidth: 400 }}>
              <Avatar
                size={64}
                icon={<MessageOutlined />}
                style={{ backgroundColor: 'var(--color-primary-soft)', color: 'var(--color-primary)', marginBottom: 24 }}
              />
              <Title level={3} style={{ fontFamily: "'Lexend', sans-serif" }}>Start a Conversation</Title>
              <Paragraph type="secondary" style={{ marginBottom: 32 }}>
                Current runtime: <Text strong style={{ color: 'var(--color-primary)' }}>{ACTIVE_PROVIDER_LABEL}</Text> using <Text strong style={{ color: 'var(--color-primary)' }}>{ACTIVE_MODEL_LABEL}</Text> at <Text code>{ACTIVE_ENDPOINT_LABEL}</Text>.
              </Paragraph>
              <Space size={12} wrap style={{ justifyContent: 'center' }}>
                {[
                  'Analyze ideology elements in IoT education',
                  'How can patriotic themes be integrated into computing courses?',
                  'Recommend course ideology case studies'
                ].map(question => (
                  <Button key={question} onClick={() => setInputValue(question)} shape="round" style={{ color: '#595959' }}>
                    {question}
                  </Button>
                ))}
              </Space>
            </div>
          ) : (
            messages.map(message => (
              <div key={message.id} style={{ display: 'flex', justifyContent: message.role === 'user' ? 'flex-end' : 'flex-start' }}>
                <div
                  style={{
                    maxWidth: '75%',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: message.role === 'user' ? 'flex-end' : 'flex-start'
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12, flexDirection: message.role === 'user' ? 'row-reverse' : 'row' }}>
                    <Avatar
                      size={36}
                      icon={message.role === 'user' ? <UserOutlined /> : <RobotOutlined />}
                      style={{
                        backgroundColor: message.role === 'user' ? '#bae0ff' : '#1677ff',
                        color: message.role === 'user' ? '#0958d9' : '#fff'
                      }}
                    />
                    <div
                      style={{
                        padding: '12px 16px',
                        borderRadius: 16,
                        borderTopRightRadius: message.role === 'user' ? 4 : 16,
                        borderTopLeftRadius: message.role === 'assistant' ? 4 : 16,
                        backgroundColor: message.role === 'user' ? 'var(--color-primary)' : 'var(--color-bg-panel)',
                        color: message.role === 'user' ? '#fff' : '#1f2937',
                        boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
                        lineHeight: 1.6,
                        fontSize: 14,
                        whiteSpace: 'pre-wrap',
                        wordBreak: 'break-word',
                        border: message.role === 'assistant' ? '1px solid var(--color-border)' : 'none'
                      }}
                    >
                      {message.content}
                    </div>
                  </div>
                  {message.role === 'assistant' && message.retrievalStatus && (
                    <div style={{ marginTop: 8, marginLeft: 48, maxWidth: 'calc(100% - 48px)' }}>
                      <Tag color={retrievalColor(message.retrievalStatus)} style={{ marginBottom: 8 }}>
                        {retrievalText(message.retrievalStatus)}
                      </Tag>
                      {message.citations && message.citations.length > 0 ? (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                          {message.citations.map((citation, index) => (
                            <div
                              key={`${citation.itemType}-${citation.referenceId}-${index}`}
                              style={{
                                padding: '8px 10px',
                                border: '1px solid var(--color-border)',
                                borderRadius: 8,
                                background: 'var(--color-bg-panel)',
                                fontSize: 12,
                                color: '#475569',
                                lineHeight: 1.5
                              }}
                            >
                              <Text strong style={{ fontSize: 12 }}>{citation.title || 'Untitled source'}</Text>
                              <div style={{ marginTop: 4, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                                {citation.snippet}
                              </div>
                              <div style={{ marginTop: 4 }}>
                                {citation.source && <Text type="secondary" style={{ fontSize: 12 }}>{citation.source}</Text>}
                                {citation.sourceUrl && (
                                  <a
                                    href={citation.sourceUrl}
                                    target="_blank"
                                    rel="noreferrer"
                                    style={{ marginLeft: citation.source ? 8 : 0 }}
                                  >
                                    Source link
                                  </a>
                                )}
                              </div>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          No reliable knowledge-base evidence was retrieved for this answer.
                        </Text>
                      )}
                    </div>
                  )}
                  <Text type="secondary" style={{ fontSize: 11, marginTop: 6, opacity: 0.8, padding: '0 48px' }}>
                    {message.timestamp}
                  </Text>
                </div>
              </div>
            ))
          )}

          {sending && (
            <div style={{ display: 'flex', justifyContent: 'flex-start', marginTop: 8 }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
                <Avatar size={36} icon={<RobotOutlined />} style={{ backgroundColor: 'var(--color-primary)', color: '#fff' }} />
                <div
                  style={{
                    padding: '12px 20px',
                    borderRadius: 16,
                    borderTopLeftRadius: 4,
                    backgroundColor: 'var(--color-bg-panel)',
                    border: '1px solid var(--color-border)',
                    boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
                    display: 'flex',
                    alignItems: 'center'
                  }}
                >
                  <Spin indicator={<LoadingOutlined style={{ fontSize: 18, color: 'var(--color-primary)' }} spin />} />
                </div>
              </div>
            </div>
          )}
          <div ref={messagesEndRef} style={{ height: 1 }} />
        </div>

        <div style={{ gridColumn: 2, gridRow: 2, minWidth: 0, padding: '20px 10%', background: 'var(--color-bg-panel)', borderTop: '1px solid var(--color-border)', zIndex: 10 }}>
          <div style={{ maxWidth: 1000, margin: '0 auto', display: 'flex', gap: 12, alignItems: 'flex-end' }}>
            <Input.TextArea
              ref={inputRef}
              value={inputValue}
              onChange={event => setInputValue(event.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Enter your question... (Shift+Enter for a new line)"
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
    </div>
  );
};
