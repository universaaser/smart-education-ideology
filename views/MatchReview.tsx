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
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { CheckCircleOutlined, EditOutlined, ReloadOutlined, StopOutlined } from '@ant-design/icons';
import { matchReviewApi, MatchReviewHistoryInfo, MatchReviewInfo } from '../services/api';
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
      return 'green';
    case 'REJECTED':
      return 'red';
    case 'PENDING':
      return 'orange';
    default:
      return 'blue';
  }
};

export const MatchReview: React.FC = () => {
  const { currentUser } = useAuth();
  const [matches, setMatches] = useState<MatchReviewInfo[]>([]);
  const [history, setHistory] = useState<MatchReviewHistoryInfo[]>([]);
  const [status, setStatus] = useState('PENDING');
  const [loading, setLoading] = useState(true);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [updatingId, setUpdatingId] = useState<number | null>(null);
  const [selectedMatch, setSelectedMatch] = useState<MatchReviewInfo | null>(null);
  const [reviseOpen, setReviseOpen] = useState(false);
  const [form] = Form.useForm();

  const loadMatches = async (nextStatus = status) => {
    setLoading(true);
    try {
      const data = await matchReviewApi.listPending(nextStatus);
      setMatches(data || []);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to load match reviews');
    } finally {
      setLoading(false);
    }
  };

  const loadHistory = async (match: MatchReviewInfo) => {
    setSelectedMatch(match);
    setHistoryLoading(true);
    try {
      const data = await matchReviewApi.listHistory(match.id);
      setHistory(data || []);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to load review history');
    } finally {
      setHistoryLoading(false);
    }
  };

  useEffect(() => {
    loadMatches();
  }, []);

  const updateStatus = async (match: MatchReviewInfo, nextStatus: 'APPROVED' | 'REJECTED') => {
    setUpdatingId(match.id);
    try {
      if (nextStatus === 'APPROVED') {
        await matchReviewApi.approve(match.id, { reviewerId: currentUser?.id });
      } else {
        await matchReviewApi.reject(match.id, { reviewerId: currentUser?.id });
      }
      message.success('Match review updated');
      await loadMatches(status);
      if (selectedMatch?.id === match.id) {
        await loadHistory(match);
      }
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to update match review');
    } finally {
      setUpdatingId(null);
    }
  };

  const openRevise = (match: MatchReviewInfo) => {
    setSelectedMatch(match);
    form.setFieldsValue({ matchReason: match.matchReason || '', reviewComment: match.reviewComment || '' });
    setReviseOpen(true);
  };

  const saveRevision = async () => {
    if (!selectedMatch) {
      return;
    }
    const values = await form.validateFields();
    setUpdatingId(selectedMatch.id);
    try {
      await matchReviewApi.revise(selectedMatch.id, {
        reviewerId: currentUser?.id,
        matchReason: values.matchReason,
        reviewComment: values.reviewComment,
      });
      message.success('Match reason revised');
      setReviseOpen(false);
      form.resetFields();
      await loadMatches(status);
      await loadHistory(selectedMatch);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to revise match reason');
    } finally {
      setUpdatingId(null);
    }
  };

  if (loading && matches.length === 0) {
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
            <Title level={2} style={{ margin: 0, fontFamily: "'Lexend', sans-serif" }}>Match Review</Title>
            <Text type="secondary">Review AI-generated mappings between subject knowledge and ideology elements.</Text>
          </Space>
          <Space>
            <Select
              value={status}
              style={{ width: 150 }}
              onChange={(value) => {
                setStatus(value);
                loadMatches(value);
              }}
            >
              <Option value="PENDING">Pending</Option>
              <Option value="APPROVED">Approved</Option>
              <Option value="REJECTED">Rejected</Option>
              <Option value="">All</Option>
            </Select>
            <Button icon={<ReloadOutlined />} loading={loading} onClick={() => loadMatches(status)}>
              Refresh
            </Button>
          </Space>
        </Space>

        <Card title={<Space><CheckCircleOutlined /> <Text strong>AI Match Candidates</Text></Space>}>
          <Table<MatchReviewInfo>
            rowKey="id"
            loading={loading}
            dataSource={matches}
            pagination={{ pageSize: 6 }}
            columns={[
              {
                title: 'Subject Knowledge',
                render: (_, match) => (
                  <Space direction="vertical" size={2}>
                    <Text strong>{match.subjectKnowledgeName}</Text>
                    <Text type="secondary" style={{ fontSize: 12 }}>{match.subject || '-'} · {match.category || '-'}</Text>
                  </Space>
                ),
              },
              { title: 'Ideology', dataIndex: 'ideologyName' },
              { title: 'Score', dataIndex: 'matchScore', width: 90 },
              { title: 'Status', dataIndex: 'reviewStatus', width: 120, render: (value: string) => <Tag color={statusColor(value)}>{value}</Tag> },
              {
                title: 'Reason',
                dataIndex: 'matchReason',
                ellipsis: true,
                render: (value: string) => <Text>{value || '-'}</Text>,
              },
              {
                title: 'Actions',
                width: 300,
                render: (_, match) => (
                  <Space>
                    <Button size="small" onClick={() => loadHistory(match)}>History</Button>
                    <Button size="small" icon={<EditOutlined />} onClick={() => openRevise(match)}>Revise</Button>
                    <Button
                      size="small"
                      type="primary"
                      icon={<CheckCircleOutlined />}
                      loading={updatingId === match.id}
                      disabled={match.reviewStatus === 'APPROVED'}
                      onClick={() => updateStatus(match, 'APPROVED')}
                    >
                      Approve
                    </Button>
                    <Button
                      size="small"
                      danger
                      icon={<StopOutlined />}
                      loading={updatingId === match.id}
                      disabled={match.reviewStatus === 'REJECTED'}
                      onClick={() => updateStatus(match, 'REJECTED')}
                    >
                      Reject
                    </Button>
                  </Space>
                ),
              },
            ]}
          />
        </Card>

        <Card title={<Space><ReloadOutlined /> <Text strong>Review History</Text></Space>}>
          {!selectedMatch ? (
            <Empty description="Select a match to inspect review history." image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : history.length === 0 && !historyLoading ? (
            <Empty description="No review history for this match." image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : (
            <List
              loading={historyLoading}
              dataSource={history}
              renderItem={(item) => (
                <List.Item>
                  <List.Item.Meta
                    title={(
                      <Space wrap>
                        <Tag color="blue">{item.action}</Tag>
                        <Text>Version {item.version}</Text>
                        <Text type="secondary">{formatTime(item.createdAt)}</Text>
                      </Space>
                    )}
                    description={(
                      <Space direction="vertical" size={4} style={{ width: '100%' }}>
                        <Text type="secondary">{item.previousStatus || '-'} → {item.nextStatus || '-'}</Text>
                        <Paragraph ellipsis={{ rows: 2 }} style={{ marginBottom: 0 }}>
                          {item.nextReason || item.previousReason || '-'}
                        </Paragraph>
                        {item.reviewComment && <Text type="secondary">Comment: {item.reviewComment}</Text>}
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
        title="Revise Match Reason"
        open={reviseOpen}
        onOk={saveRevision}
        onCancel={() => setReviseOpen(false)}
        confirmLoading={updatingId === selectedMatch?.id}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="matchReason" label="Match Reason" rules={[{ required: true, message: 'Match reason cannot be empty' }]}>
            <Input.TextArea rows={5} />
          </Form.Item>
          <Form.Item name="reviewComment" label="Review Comment">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};
