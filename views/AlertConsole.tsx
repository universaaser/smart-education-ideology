import React, { useCallback, useEffect, useState } from 'react';
import { AlertOutlined, CheckCircleOutlined, ReloadOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { Button, Card, Col, Empty, Form, InputNumber, Row, Select, Space, Statistic, Table, Tag, Typography, message } from 'antd';
import { alertApi, StudentAlertRecordInfo, StudentAlertSummaryInfo } from '../services/api';

const { Title, Text, Paragraph } = Typography;

const LEVEL_OPTIONS = [
  { label: 'All levels', value: 0 },
  { label: 'Mild', value: 1 },
  { label: 'Moderate', value: 2 },
  { label: 'Severe', value: 3 },
];

const STATUS_OPTIONS = [
  { label: 'All statuses', value: '' },
  { label: 'Pending', value: 'PENDING' },
  { label: 'Processing', value: 'PROCESSING' },
  { label: 'Resolved', value: 'RESOLVED' },
  { label: 'Ignored', value: 'IGNORED' },
];

export const AlertConsole: React.FC = () => {
  const [summary, setSummary] = useState<StudentAlertSummaryInfo | null>(null);
  const [alerts, setAlerts] = useState<StudentAlertRecordInfo[]>([]);
  const [courseId, setCourseId] = useState<number | undefined>();
  const [alertLevel, setAlertLevel] = useState<number>(0);
  const [status, setStatus] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const [evaluating, setEvaluating] = useState(false);
  const [form] = Form.useForm<{ studentId: number; courseId: number }>();
  const [messageApi, contextHolder] = message.useMessage();

  const loadAlerts = useCallback(async () => {
    setLoading(true);
    try {
      const [summaryData, alertData] = await Promise.all([
        alertApi.getSummary(courseId),
        alertApi.list({ courseId, alertLevel: alertLevel || undefined, status: status || undefined }),
      ]);
      setSummary(summaryData);
      setAlerts(alertData ?? []);
    } catch {
      messageApi.error('Failed to load student alerts');
    } finally {
      setLoading(false);
    }
  }, [alertLevel, courseId, messageApi, status]);

  useEffect(() => {
    loadAlerts();
  }, [loadAlerts]);

  const handleEvaluate = async (values: { studentId: number; courseId: number }) => {
    setEvaluating(true);
    try {
      const records = await alertApi.evaluate(values.studentId, values.courseId);
      setCourseId(values.courseId);
      messageApi.success(records.length > 0 ? `Generated ${records.length} alert(s)` : 'No new active alerts');
      await loadAlerts();
    } catch {
      messageApi.error('Failed to evaluate student alerts');
    } finally {
      setEvaluating(false);
    }
  };

  const handleStatusChange = async (record: StudentAlertRecordInfo, nextStatus: string) => {
    try {
      await alertApi.updateStatus(record.id, nextStatus);
      messageApi.success('Alert status updated');
      await loadAlerts();
    } catch {
      messageApi.error('Failed to update alert status');
    }
  };

  const levelTag = (level: number) => {
    if (level >= 3) return <Tag color="error">Severe</Tag>;
    if (level === 2) return <Tag color="warning">Moderate</Tag>;
    return <Tag color="processing">Mild</Tag>;
  };

  const statusTag = (value: string) => {
    const colors: Record<string, string> = {
      PENDING: 'error',
      PROCESSING: 'warning',
      RESOLVED: 'success',
      IGNORED: 'default',
    };
    return <Tag color={colors[value] || 'default'}>{value}</Tag>;
  };

  const columns = [
    {
      title: 'Alert',
      key: 'alert',
      render: (_: unknown, record: StudentAlertRecordInfo) => (
        <Space direction="vertical" size={2}>
          <Text strong>{record.title}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>{record.message}</Text>
          {record.suggestion && <Text style={{ fontSize: 12, color: 'var(--color-primary)' }}>{record.suggestion}</Text>}
        </Space>
      ),
    },
    {
      title: 'Student',
      dataIndex: 'studentId',
      width: 100,
      render: (value: number) => <Text>#{value}</Text>,
    },
    {
      title: 'Course',
      dataIndex: 'courseId',
      width: 100,
      render: (value: number) => <Text>#{value}</Text>,
    },
    {
      title: 'Level',
      dataIndex: 'alertLevel',
      width: 120,
      render: levelTag,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 130,
      render: statusTag,
    },
    {
      title: 'Generated',
      dataIndex: 'generatedAt',
      width: 180,
      render: (value: string) => value ? new Date(value).toLocaleString() : '--',
    },
    {
      title: 'Action',
      key: 'action',
      width: 190,
      render: (_: unknown, record: StudentAlertRecordInfo) => (
        <Space>
          <Button size="small" onClick={() => handleStatusChange(record, 'PROCESSING')} disabled={record.status === 'PROCESSING'}>
            Process
          </Button>
          <Button size="small" type="primary" icon={<CheckCircleOutlined />} onClick={() => handleStatusChange(record, 'RESOLVED')} disabled={record.status === 'RESOLVED'}>
            Resolve
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: 32 }}>
      {contextHolder}
      <div style={{ maxWidth: 1200, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 24 }}>
        <Row justify="space-between" align="bottom" gutter={[16, 16]}>
          <Col>
            <Title level={2} style={{ margin: 0, fontFamily: "'Lexend', sans-serif" }}>Student Alert Console</Title>
            <Paragraph type="secondary" style={{ margin: '6px 0 0' }}>
              Generate behavior alerts from captured student activity events and track handling status.
            </Paragraph>
          </Col>
          <Col>
            <Button icon={<ReloadOutlined />} onClick={loadAlerts} loading={loading}>Refresh</Button>
          </Col>
        </Row>

        <Row gutter={[16, 16]}>
          <Col xs={24} md={6}><Card bordered={false}><Statistic title="Total Alerts" value={summary?.total ?? 0} prefix={<AlertOutlined />} /></Card></Col>
          <Col xs={24} md={6}><Card bordered={false}><Statistic title="Pending" value={summary?.pending ?? 0} valueStyle={{ color: 'var(--color-error)' }} /></Card></Col>
          <Col xs={24} md={6}><Card bordered={false}><Statistic title="Processing" value={summary?.processing ?? 0} valueStyle={{ color: '#faad14' }} /></Card></Col>
          <Col xs={24} md={6}><Card bordered={false}><Statistic title="Severe" value={summary?.severe ?? 0} valueStyle={{ color: 'var(--color-error)' }} /></Card></Col>
        </Row>

        <Card bordered={false} title="Evaluate student risk">
          <Form form={form} layout="inline" onFinish={handleEvaluate} initialValues={{ courseId: courseId ?? 1 }}>
            <Form.Item name="studentId" rules={[{ required: true, message: 'Student id is required' }]}>
              <InputNumber min={1} placeholder="Student id" />
            </Form.Item>
            <Form.Item name="courseId" rules={[{ required: true, message: 'Course id is required' }]}>
              <InputNumber min={1} placeholder="Course id" />
            </Form.Item>
            <Form.Item>
              <Button type="primary" htmlType="submit" icon={<ThunderboltOutlined />} loading={evaluating}>Evaluate</Button>
            </Form.Item>
          </Form>
        </Card>

        <Card
          bordered={false}
          title="Alert records"
          extra={(
            <Space>
              <InputNumber min={1} placeholder="Course id" value={courseId} onChange={(value) => setCourseId(value ?? undefined)} />
              <Select style={{ width: 140 }} options={LEVEL_OPTIONS} value={alertLevel} onChange={setAlertLevel} />
              <Select style={{ width: 150 }} options={STATUS_OPTIONS} value={status} onChange={setStatus} />
            </Space>
          )}
        >
          <Table
            rowKey="id"
            columns={columns}
            dataSource={alerts}
            loading={loading}
            pagination={{ pageSize: 8 }}
            expandable={{
              expandedRowRender: (record) => (
                <Space direction="vertical" size={4}>
                  <Text type="secondary">Type: {record.alertType}</Text>
                  <Text type="secondary">Generated at: {record.generatedAt ? new Date(record.generatedAt).toLocaleString() : '--'}</Text>
                  <Text type="secondary">Handled at: {record.handledAt ? new Date(record.handledAt).toLocaleString() : '--'}</Text>
                </Space>
              ),
            }}
            locale={{ emptyText: <Empty description="No alert records" /> }}
          />
        </Card>
      </div>
    </div>
  );
};
