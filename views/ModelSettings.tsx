import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { ApiOutlined, ReloadOutlined, SaveOutlined, SettingOutlined } from '@ant-design/icons';
import {
  aiProviderApi,
  AiProviderConfigInfo,
  AiProviderConfigRequest,
  AiRouteConfigInfo,
  AiRouteConfigRequest,
} from '../services/api';

const { Text, Title, Paragraph } = Typography;

const TASK_LABELS: Record<string, string> = {
  chat: 'Chat assistant',
  parse: 'Document parsing',
  ideology: 'Ideology mining',
  'question-gen': 'Question generation',
  crawl: 'Resource crawling',
  path: 'Learning path',
  vision: 'Vision analysis',
  embedding: 'Embedding retrieval',
};

export const ModelSettings: React.FC = () => {
  const [providers, setProviders] = useState<AiProviderConfigInfo[]>([]);
  const [routes, setRoutes] = useState<AiRouteConfigInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [savingProviderKey, setSavingProviderKey] = useState<string | null>(null);
  const [testingProviderKey, setTestingProviderKey] = useState<string | null>(null);
  const [savingRouteTask, setSavingRouteTask] = useState<string | null>(null);
  const [providerForms, setProviderForms] = useState<Record<string, AiProviderConfigRequest>>({});
  const [routeForms, setRouteForms] = useState<Record<string, AiRouteConfigRequest>>({});

  const providerOptions = useMemo(
    () => providers.map((provider) => ({ label: provider.label || provider.providerKey, value: provider.providerKey })),
    [providers],
  );

  const syncForms = (providerData: AiProviderConfigInfo[], routeData: AiRouteConfigInfo[]) => {
    setProviderForms(Object.fromEntries(providerData.map((provider) => [
      provider.providerKey,
      {
        label: provider.label,
        enabled: provider.enabled,
        apiBase: provider.apiBase || '',
        model: provider.model || '',
        apiKey: '',
        timeoutSeconds: provider.timeoutSeconds,
      },
    ])));
    setRouteForms(Object.fromEntries(routeData.map((route) => [
      route.taskType,
      {
        providerKey: route.providerKey,
        model: route.model || '',
      },
    ])));
  };

  const loadData = async () => {
    setLoading(true);
    try {
      const [providerData, routeData] = await Promise.all([
        aiProviderApi.listProviders(),
        aiProviderApi.listRoutes(),
      ]);
      setProviders(providerData || []);
      setRoutes(routeData || []);
      syncForms(providerData || [], routeData || []);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to load AI provider settings');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const updateProviderForm = (providerKey: string, patch: Partial<AiProviderConfigRequest>) => {
    setProviderForms((prev) => ({
      ...prev,
      [providerKey]: { ...prev[providerKey], ...patch },
    }));
  };

  const updateRouteForm = (taskType: string, patch: Partial<AiRouteConfigRequest>) => {
    setRouteForms((prev) => ({
      ...prev,
      [taskType]: { ...prev[taskType], ...patch },
    }));
  };

  const saveProvider = async (provider: AiProviderConfigInfo) => {
    const form = providerForms[provider.providerKey];
    setSavingProviderKey(provider.providerKey);
    try {
      const payload: AiProviderConfigRequest = {
        label: form?.label || provider.label,
        enabled: Boolean(form?.enabled),
        apiBase: form?.apiBase || '',
        model: form?.model || '',
        timeoutSeconds: form?.timeoutSeconds || provider.timeoutSeconds,
      };
      if (form?.apiKey?.trim()) {
        payload.apiKey = form.apiKey.trim();
      }
      const saved = await aiProviderApi.saveProvider(provider.providerKey, payload);
      const nextProviders = providers.map((item) => (item.providerKey === saved.providerKey ? saved : item));
      setProviders(nextProviders);
      updateProviderForm(saved.providerKey, { apiKey: '' });
      message.success('Provider saved');
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to save provider');
    } finally {
      setSavingProviderKey(null);
    }
  };

  const testProvider = async (provider: AiProviderConfigInfo) => {
    setTestingProviderKey(provider.providerKey);
    try {
      const result = await aiProviderApi.testProvider(provider.providerKey);
      if (result.success) {
        message.success(result.message);
      } else {
        message.warning(result.message);
      }
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to test provider');
    } finally {
      setTestingProviderKey(null);
    }
  };

  const saveRoute = async (route: AiRouteConfigInfo) => {
    const form = routeForms[route.taskType];
    setSavingRouteTask(route.taskType);
    try {
      const saved = await aiProviderApi.saveRoute(route.taskType, {
        providerKey: form?.providerKey || route.providerKey,
        model: form?.model || '',
      });
      setRoutes(routes.map((item) => (item.taskType === saved.taskType ? saved : item)));
      message.success('Route saved');
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to save route');
    } finally {
      setSavingRouteTask(null);
    }
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', flex: 1, alignItems: 'center', justifyContent: 'center' }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: 32 }}>
      <div style={{ maxWidth: 1180, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 24 }}>
        <Space align="start" style={{ justifyContent: 'space-between', width: '100%' }}>
          <Space direction="vertical" size={2}>
            <Title level={2} style={{ margin: 0, fontFamily: "'Lexend', sans-serif" }}>Model Settings</Title>
            <Text type="secondary">Manage AI providers and task routing for the teaching workflow.</Text>
          </Space>
          <Button icon={<ReloadOutlined />} onClick={loadData}>
            Refresh
          </Button>
        </Space>

        <Alert
          type="info"
          showIcon
          message="Runtime routing note"
          description="This page saves provider and route settings for admin visibility. Existing AI services may still use application configuration until runtime DB routing is connected. Provider test checks configuration completeness in this demo build."
        />

        <Card title={<Space><ApiOutlined /> <Text strong>Providers</Text></Space>}>
          <Table
            rowKey="providerKey"
            dataSource={providers}
            pagination={false}
            columns={[
              {
                title: 'Provider',
                dataIndex: 'providerKey',
                width: 150,
                render: (_, provider) => (
                  <Space direction="vertical" size={0}>
                    <Text strong>{provider.label}</Text>
                    <Text type="secondary">{provider.providerKey}</Text>
                  </Space>
                ),
              },
              {
                title: 'Enabled',
                width: 110,
                render: (_, provider) => (
                  <Switch
                    checked={Boolean(providerForms[provider.providerKey]?.enabled)}
                    onChange={(enabled) => updateProviderForm(provider.providerKey, { enabled })}
                  />
                ),
              },
              {
                title: 'API Base',
                render: (_, provider) => (
                  <Input
                    value={providerForms[provider.providerKey]?.apiBase}
                    placeholder="https://api.example.com/v1"
                    onChange={(event) => updateProviderForm(provider.providerKey, { apiBase: event.target.value })}
                  />
                ),
              },
              {
                title: 'Model',
                width: 190,
                render: (_, provider) => (
                  <Input
                    value={providerForms[provider.providerKey]?.model}
                    placeholder="model name"
                    onChange={(event) => updateProviderForm(provider.providerKey, { model: event.target.value })}
                  />
                ),
              },
              {
                title: 'API Key',
                width: 210,
                render: (_, provider) => (
                  <Space direction="vertical" size={4} style={{ width: '100%' }}>
                    <Tag color={provider.keyConfigured ? 'green' : 'default'}>
                      {provider.keyConfigured ? 'Configured' : 'Missing'}
                    </Tag>
                    <Input.Password
                      value={providerForms[provider.providerKey]?.apiKey}
                      placeholder="Leave blank to keep current key"
                      onChange={(event) => updateProviderForm(provider.providerKey, { apiKey: event.target.value })}
                    />
                  </Space>
                ),
              },
              {
                title: 'Timeout',
                width: 130,
                render: (_, provider) => (
                  <InputNumber
                    min={1}
                    max={300}
                    value={providerForms[provider.providerKey]?.timeoutSeconds}
                    onChange={(value) => updateProviderForm(provider.providerKey, { timeoutSeconds: value || 120 })}
                  />
                ),
              },
              {
                title: 'Actions',
                width: 190,
                render: (_, provider) => (
                  <Space>
                    <Button
                      icon={<SaveOutlined />}
                      loading={savingProviderKey === provider.providerKey}
                      onClick={() => saveProvider(provider)}
                    >
                      Save
                    </Button>
                    <Button
                      loading={testingProviderKey === provider.providerKey}
                      onClick={() => testProvider(provider)}
                    >
                      Test
                    </Button>
                  </Space>
                ),
              },
            ]}
          />
        </Card>

        <Card title={<Space><SettingOutlined /> <Text strong>Task Routes</Text></Space>}>
          <Paragraph type="secondary">
            Route settings cover chat, parsing, question generation, crawling, learning path, vision and embedding tasks.
          </Paragraph>
          <Table
            rowKey="taskType"
            dataSource={routes}
            pagination={false}
            columns={[
              {
                title: 'Task',
                dataIndex: 'taskType',
                width: 230,
                render: (taskType: string) => (
                  <Space direction="vertical" size={0}>
                    <Text strong>{TASK_LABELS[taskType] || taskType}</Text>
                    <Text type="secondary">{taskType}</Text>
                  </Space>
                ),
              },
              {
                title: 'Provider',
                width: 260,
                render: (_, route) => (
                  <Select
                    style={{ width: '100%' }}
                    value={routeForms[route.taskType]?.providerKey}
                    options={providerOptions}
                    onChange={(providerKey) => updateRouteForm(route.taskType, { providerKey })}
                  />
                ),
              },
              {
                title: 'Model Override',
                render: (_, route) => (
                  <Input
                    value={routeForms[route.taskType]?.model}
                    placeholder="Leave blank to use provider model"
                    onChange={(event) => updateRouteForm(route.taskType, { model: event.target.value })}
                  />
                ),
              },
              {
                title: 'Actions',
                width: 120,
                render: (_, route) => (
                  <Button
                    icon={<SaveOutlined />}
                    loading={savingRouteTask === route.taskType}
                    onClick={() => saveRoute(route)}
                  >
                    Save
                  </Button>
                ),
              },
            ]}
          />
        </Card>
      </div>
    </div>
  );
};
