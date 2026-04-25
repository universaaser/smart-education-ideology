import React, { useEffect, useState } from 'react';
import {
  Button,
  Card,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { CloudServerOutlined, ReloadOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { crawlSourceApi, resourceApi, CrawlRunLogInfo, CrawlSourceInfo, ResourceInfo } from '../services/api';
import { useAuth } from '../contexts/AuthContext';

const { Text, Title, Paragraph } = Typography;
const { Option } = Select;

const formatTime = (value?: string | null) => {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString();
};

const statusColor = (status?: string) => {
  switch (status) {
    case 'APPROVED':
    case 'DONE':
      return 'green';
    case 'REJECTED':
    case 'FAILED':
      return 'red';
    case 'PENDING':
      return 'orange';
    case 'STOPPED':
      return 'default';
    default:
      return 'blue';
  }
};

export const SourceManagement: React.FC = () => {
  const { currentUser } = useAuth();
  const [sources, setSources] = useState<CrawlSourceInfo[]>([]);
  const [runLogs, setRunLogs] = useState<CrawlRunLogInfo[]>([]);
  const [reviewResources, setReviewResources] = useState<ResourceInfo[]>([]);
  const [reviewStatus, setReviewStatus] = useState<string>('PENDING');
  const [loading, setLoading] = useState(true);
  const [logLoading, setLogLoading] = useState(false);
  const [resourceLoading, setResourceLoading] = useState(false);
  const [sourceModalOpen, setSourceModalOpen] = useState(false);
  const [editingSource, setEditingSource] = useState<CrawlSourceInfo | null>(null);
  const [savingSource, setSavingSource] = useState(false);
  const [triggeringSourceId, setTriggeringSourceId] = useState<number | null>(null);
  const [updatingResourceId, setUpdatingResourceId] = useState<number | null>(null);
  const [form] = Form.useForm();

  const loadSources = async () => {
    setLoading(true);
    try {
      const data = await crawlSourceApi.listSources();
      setSources(data);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to load crawl sources');
    } finally {
      setLoading(false);
    }
  };

  const loadRunLogs = async (sourceId?: number) => {
    setLogLoading(true);
    try {
      const logs = await crawlSourceApi.listRunLogs(sourceId);
      setRunLogs(logs);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to load crawl logs');
    } finally {
      setLogLoading(false);
    }
  };

  const loadReviewResources = async (status = reviewStatus) => {
    setResourceLoading(true);
    try {
      const resources = await resourceApi.getReviewResources(status || undefined);
      setReviewResources(resources);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to load review resources');
    } finally {
      setResourceLoading(false);
    }
  };

  useEffect(() => {
    Promise.all([loadSources(), loadRunLogs(), loadReviewResources()]).finally(() => setLoading(false));
  }, []);

  const openCreateModal = () => {
    setEditingSource(null);
    form.setFieldsValue({ enabled: true });
    setSourceModalOpen(true);
  };

  const openEditModal = (source: CrawlSourceInfo) => {
    setEditingSource(source);
    form.setFieldsValue({
      name: source.name,
      baseUrl: source.baseUrl,
      enabled: source.enabled === 1,
      remark: source.remark || '',
    });
    setSourceModalOpen(true);
  };

  const saveSource = async () => {
    const values = await form.validateFields();
    setSavingSource(true);
    try {
      if (editingSource) {
        await crawlSourceApi.updateSource(editingSource.id, values);
      } else {
        await crawlSourceApi.createSource(values);
      }
      message.success('Source saved');
      setSourceModalOpen(false);
      form.resetFields();
      await loadSources();
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to save source');
    } finally {
      setSavingSource(false);
    }
  };

  const triggerSource = async (source: CrawlSourceInfo) => {
    setTriggeringSourceId(source.id);
    try {
      await crawlSourceApi.triggerSource(source.id);
      message.success('Crawl task started');
      await loadRunLogs(source.id);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to trigger crawl');
    } finally {
      setTriggeringSourceId(null);
    }
  };

  const updateReviewStatus = async (resource: ResourceInfo, nextStatus: string) => {
    setUpdatingResourceId(resource.id);
    try {
      await resourceApi.updateReviewStatus(resource.id, nextStatus, currentUser?.id);
      message.success('Review status updated');
      await loadReviewResources(reviewStatus);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to update review status');
    } finally {
      setUpdatingResourceId(null);
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
      <div style={{ maxWidth: 1120, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 24 }}>
        <Space align="start" style={{ justifyContent: 'space-between', width: '100%' }}>
          <Space direction="vertical" size={2}>
            <Title level={2} style={{ margin: 0, fontFamily: "'Lexend', sans-serif" }}>Source Management</Title>
            <Text type="secondary">Configure crawl sources, inspect crawl logs, and review resources before they enter student-facing retrieval.</Text>
          </Space>
          <Button type="primary" icon={<CloudServerOutlined />} onClick={openCreateModal}>
            New Source
          </Button>
        </Space>

        <Card
          title={<Space><CloudServerOutlined /> <Text strong>Crawl Sources</Text></Space>}
          extra={<Button icon={<ReloadOutlined />} onClick={loadSources}>Refresh</Button>}
        >
          <Table<CrawlSourceInfo>
            rowKey="id"
            dataSource={sources}
            pagination={false}
            columns={[
              { title: 'Name', dataIndex: 'name' },
              { title: 'Base URL', dataIndex: 'baseUrl', ellipsis: true },
              {
                title: 'Enabled',
                dataIndex: 'enabled',
                render: (enabled: number) => <Tag color={enabled === 1 ? 'green' : 'default'}>{enabled === 1 ? 'Enabled' : 'Disabled'}</Tag>,
              },
              { title: 'Last Run', dataIndex: 'lastRunAt', render: formatTime },
              { title: 'Remark', dataIndex: 'remark', ellipsis: true },
              {
                title: 'Actions',
                render: (_, source) => (
                  <Space>
                    <Button size="small" onClick={() => openEditModal(source)}>Edit</Button>
                    <Button
                      size="small"
                      type="primary"
                      disabled={source.enabled !== 1}
                      loading={triggeringSourceId === source.id}
                      onClick={() => triggerSource(source)}
                    >
                      Trigger
                    </Button>
                    <Button size="small" onClick={() => loadRunLogs(source.id)}>Logs</Button>
                  </Space>
                ),
              },
            ]}
          />
        </Card>

        <Card
          title={<Space><ReloadOutlined /> <Text strong>Crawl Run Logs</Text></Space>}
          extra={<Button loading={logLoading} onClick={() => loadRunLogs()}>All Logs</Button>}
        >
          <Table<CrawlRunLogInfo>
            rowKey="id"
            loading={logLoading}
            dataSource={runLogs}
            pagination={{ pageSize: 6 }}
            columns={[
              { title: 'Source', dataIndex: 'sourceName' },
              { title: 'Status', dataIndex: 'status', render: (status: string) => <Tag color={statusColor(status)}>{status}</Tag> },
              { title: 'Fetched', dataIndex: 'totalFetched' },
              { title: 'Created', dataIndex: 'totalCreated' },
              { title: 'Deduped', dataIndex: 'totalDeduplicated' },
              { title: 'Failed', dataIndex: 'totalFailed' },
              { title: 'Started', dataIndex: 'startedAt', render: formatTime },
              { title: 'Error', dataIndex: 'errorSummary', ellipsis: true },
            ]}
          />
        </Card>

        <Card
          title={<Space><SafetyCertificateOutlined /> <Text strong>Resource Review</Text></Space>}
          extra={(
            <Space>
              <Select
                value={reviewStatus}
                style={{ width: 150 }}
                onChange={(value) => {
                  setReviewStatus(value);
                  loadReviewResources(value);
                }}
              >
                <Option value="PENDING">Pending</Option>
                <Option value="APPROVED">Approved</Option>
                <Option value="REJECTED">Rejected</Option>
                <Option value="">All</Option>
              </Select>
              <Button loading={resourceLoading} onClick={() => loadReviewResources(reviewStatus)}>Refresh</Button>
            </Space>
          )}
        >
          {reviewResources.length === 0 && !resourceLoading ? (
            <Empty description="No resources for this review status." image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : (
            <List
              loading={resourceLoading}
              dataSource={reviewResources}
              renderItem={(resource) => (
                <List.Item
                  actions={[
                    <Button
                      key="approve"
                      size="small"
                      type="primary"
                      loading={updatingResourceId === resource.id}
                      disabled={resource.reviewStatus === 'APPROVED'}
                      onClick={() => updateReviewStatus(resource, 'APPROVED')}
                    >
                      Approve
                    </Button>,
                    <Button
                      key="reject"
                      size="small"
                      danger
                      loading={updatingResourceId === resource.id}
                      disabled={resource.reviewStatus === 'REJECTED'}
                      onClick={() => updateReviewStatus(resource, 'REJECTED')}
                    >
                      Reject
                    </Button>,
                  ]}
                >
                  <List.Item.Meta
                    title={(
                      <Space wrap>
                        <Text strong>{resource.title}</Text>
                        <Tag color={statusColor(resource.reviewStatus)}>{resource.reviewStatus || 'PENDING'}</Tag>
                        <Tag>{resource.source || 'Unknown source'}</Tag>
                      </Space>
                    )}
                    description={(
                      <Space direction="vertical" size={4} style={{ width: '100%' }}>
                        <Paragraph ellipsis={{ rows: 2 }} style={{ marginBottom: 0 }}>
                          {resource.content || resource.ideologySummary || '-'}
                        </Paragraph>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          URL: {resource.sourceUrl || '-'} · Reviewed: {formatTime(resource.reviewedAt)}
                        </Text>
                      </Space>
                    )}
                  />
                </List.Item>
              )}
            />
          )}
        </Card>
      </div>

      <Modal
        title={editingSource ? 'Edit Source' : 'New Source'}
        open={sourceModalOpen}
        onOk={saveSource}
        onCancel={() => setSourceModalOpen(false)}
        confirmLoading={savingSource}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false} initialValues={{ enabled: true }}>
          <Form.Item name="name" label="Source Name" rules={[{ required: true, message: 'Source name is required' }]}>
            <Input placeholder="People Daily" />
          </Form.Item>
          <Form.Item name="baseUrl" label="Base URL" rules={[{ required: true, message: 'Base URL is required' }]}>
            <Input placeholder="https://example.com/news" />
          </Form.Item>
          <Form.Item name="enabled" label="Enabled" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="remark" label="Remark">
            <Input.TextArea rows={3} placeholder="Crawler source notes" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};
