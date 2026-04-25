import React, { useEffect, useState } from 'react';
import { View, ViewChangeHandler } from '../types';
import { dashboardApi, CourseInfo, ActivityInfo, TrendItem, DashboardOverviewInfo } from '../services/api';
import {
  Card, Statistic, Row, Col, Typography, Button, List, Avatar,
  Table, Tag, Space, Progress, Spin, Empty
} from 'antd';
import {
  RobotOutlined, CloudUploadOutlined, WarningOutlined,
  ShareAltOutlined, InfoCircleOutlined, RiseOutlined,
  ExportOutlined, EditOutlined, FileSearchOutlined, CheckCircleOutlined
} from '@ant-design/icons';

const { Title, Text } = Typography;

interface DashboardProps {
  onChangeView: ViewChangeHandler;
}

export const Dashboard: React.FC<DashboardProps> = ({ onChangeView }) => {
  const [stats, setStats] = useState<Record<string, unknown>>({});
  const [courses, setCourses] = useState<CourseInfo[]>([]);
  const [activities, setActivities] = useState<ActivityInfo[]>([]);
  const [trendData, setTrendData] = useState<TrendItem[]>([]);
  const [overview, setOverview] = useState<DashboardOverviewInfo | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadData = async () => {
      try {
        const [statsData, coursesData, activitiesData, trendResult, overviewData] = await Promise.all([
          dashboardApi.getStats(),
          dashboardApi.getCourses(),
          dashboardApi.getActivities(4),
          dashboardApi.getTrend(),
          dashboardApi.getOverview(),
        ]);
        setStats(statsData || {});
        setCourses(coursesData || []);
        setActivities(activitiesData || []);
        setTrendData(trendResult || []);
        setOverview(overviewData || null);
      } catch {
        // Keep the page renderable when the backend is unavailable.
      } finally {
        setLoading(false);
      }
    };
    loadData();
  }, []);

  const getActivityStyle = (type: string) => {
    switch (type) {
      case 'AI_ANALYSIS': return { icon: <RobotOutlined />, color: 'var(--color-primary)', bg: 'var(--color-primary-soft)' };
      case 'UPLOAD': return { icon: <CloudUploadOutlined />, color: '#10b981', bg: '#d1fae5' };
      case 'ALERT': return { icon: <WarningOutlined />, color: 'var(--color-error)', bg: '#fee2e2' };
      case 'KNOWLEDGE_UPDATE':
      case 'GRAPH_UPDATE': return { icon: <ShareAltOutlined />, color: '#8b5cf6', bg: '#ede9fe' };
      default: return { icon: <InfoCircleOutlined />, color: 'var(--color-text-secondary)', bg: '#f1f5f9' };
    }
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', flex: 1, alignItems: 'center', justifyContent: 'center', height: '100%' }}>
        <Space direction="vertical" align="center">
          <Spin size="large" />
          <Text type="secondary">Loading dashboard data...</Text>
        </Space>
      </div>
    );
  }

  const maxTrend = trendData.length > 0 ? Math.max(...trendData.map(item => item.value), 1) : 100;
  const chartWidth = 600;
  const chartHeight = 250;
  const chartPadding = { top: 20, right: 20, bottom: 40, left: 50 };
  const innerWidth = chartWidth - chartPadding.left - chartPadding.right;
  const innerHeight = chartHeight - chartPadding.top - chartPadding.bottom;

  const getPoint = (index: number, value: number) => {
    const x = chartPadding.left + (trendData.length > 1 ? (index / (trendData.length - 1)) * innerWidth : innerWidth / 2);
    const y = chartPadding.top + innerHeight - (value / Math.max(maxTrend, 100)) * innerHeight;
    return { x, y };
  };

  const linePath = trendData.length > 0
    ? trendData.map((item, index) => {
      const { x, y } = getPoint(index, item.value);
      return `${index === 0 ? 'M' : 'L'} ${x} ${y}`;
    }).join(' ')
    : '';

  const areaPath = trendData.length > 0
    ? linePath +
    ` L ${getPoint(trendData.length - 1, 0).x} ${chartPadding.top + innerHeight}` +
    ` L ${getPoint(0, 0).x} ${chartPadding.top + innerHeight} Z`
    : '';

  const mapGradeColor = (color: string) => {
    const mapping: Record<string, string> = {
      orange: 'warning',
      emerald: 'success',
      green: 'success',
      blue: 'processing',
      red: 'error',
    };
    return mapping[color] || 'default';
  };

  const mapView = (view: string): View | null => {
    return Object.values(View).includes(view as View) ? view as View : null;
  };

  const mapTodoColor = (status: string) => {
    const mapping: Record<string, string> = {
      warning: '#f59e0b',
      processing: 'var(--color-primary)',
      purple: '#8b5cf6',
      error: 'var(--color-error)',
    };
    return mapping[status] || 'var(--color-text-secondary)';
  };

  const mapTaskStatus = (status: string) => {
    const mapping: Record<string, string> = {
      COMPLETED: 'success',
      FAILED: 'error',
      PARSING: 'processing',
      ANALYZING: 'processing',
      PENDING: 'default',
    };
    return mapping[status] || 'default';
  };

  const toStatisticValue = (value: unknown, fallback: string | number = '--'): string | number => {
    if (typeof value === 'number') return value;
    if (typeof value === 'string' && value.trim() !== '') {
      const parsed = Number(value);
      return Number.isNaN(parsed) ? fallback : parsed;
    }
    return fallback;
  };

  const columns = [
    {
      title: 'Course',
      dataIndex: 'name',
      key: 'name',
      render: (name: string) => (
        <Space>
          <Avatar shape="square" style={{ backgroundColor: 'var(--color-primary-soft)', color: 'var(--color-primary)' }}>
            {name?.charAt(0) || 'C'}
          </Avatar>
          <Text strong>{name}</Text>
        </Space>
      ),
    },
    {
      title: 'Progress',
      dataIndex: 'progress',
      key: 'progress',
      render: (progress: number) => (
        <Progress percent={progress} size="small" style={{ width: 120 }} />
      ),
    },
    {
      title: 'Ideology Score',
      dataIndex: 'gradeLabel',
      key: 'gradeLabel',
      render: (text: string, record: CourseInfo) => (
        <Tag color={mapGradeColor(record.gradeColor || '')}>{text}</Tag>
      ),
    },
    {
      title: 'Action',
      key: 'action',
      align: 'right' as const,
      render: (_: unknown, record: CourseInfo) => (
        <Button
          type="text"
          icon={<EditOutlined />}
          style={{ color: 'var(--color-primary)' }}
          onClick={() => onChangeView(View.COURSE_MANAGEMENT, { courseId: record.id })}
        />
      ),
    },
  ];

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: '32px' }}>
      <div style={{ maxWidth: 1200, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: '32px' }}>
        <Row justify="space-between" align="bottom">
          <Col>
            <Title level={2} style={{ margin: 0, fontFamily: "'Lexend', sans-serif" }}>Welcome back</Title>
            <Text type="secondary">Here is today's teaching overview and curriculum ideology analysis.</Text>
          </Col>
          <Col>
            <Button icon={<RobotOutlined />}>Generate Summary</Button>
          </Col>
        </Row>

        <Row gutter={[24, 24]}>
          <Col xs={24} sm={12} lg={6}>
            <Card bordered={false} hoverable>
              <Statistic
                title="Ideology Integration Rate"
                value={toStatisticValue(stats.ideologyRate)}
                suffix="%"
                valueStyle={{ color: 'var(--color-text-primary)', fontWeight: 'bold' }}
                prefix={<ShareAltOutlined style={{ color: 'var(--color-primary)', marginRight: 8 }} />}
              />
              <div style={{ marginTop: 8, fontSize: 13 }}>
                <Text type="success"><RiseOutlined /> </Text>
                <Text type="secondary" style={{ marginLeft: 4 }}>Knowledge-point ideology mapping</Text>
              </div>
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card bordered={false} hoverable>
              <Statistic
                title="Ideology Findings"
                value={toStatisticValue(stats.ideologyCount)}
                valueStyle={{ color: 'var(--color-text-primary)', fontWeight: 'bold' }}
                prefix={<RobotOutlined style={{ color: 'var(--color-error)', marginRight: 8 }} />}
              />
              <div style={{ marginTop: 8, fontSize: 13 }}>
                <Text type="success"><RiseOutlined /> </Text>
                <Text type="secondary" style={{ marginLeft: 4 }}>Linked knowledge points</Text>
              </div>
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card bordered={false} hoverable>
              <Statistic
                title="Student Activity"
                value={toStatisticValue(stats.studentActivity)}
                valueStyle={{ color: 'var(--color-text-primary)', fontWeight: 'bold' }}
                prefix={<InfoCircleOutlined style={{ color: '#8b5cf6', marginRight: 8 }} />}
              />
              <div style={{ marginTop: 8, fontSize: 13 }}>
                <Text type="success"><RiseOutlined /> </Text>
                <Text type="secondary" style={{ marginLeft: 4 }}>Learning activity records</Text>
              </div>
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card
              bordered={false}
              hoverable
              onClick={() => onChangeView(View.ALERTS)}
              style={{ cursor: 'pointer', border: '1px solid transparent' }}
            >
              <Statistic
                title="Open Alerts"
                value={toStatisticValue(stats.alertCount, 0)}
                valueStyle={{ color: 'var(--color-text-primary)', fontWeight: 'bold' }}
                prefix={<WarningOutlined style={{ color: '#f59e0b', marginRight: 8 }} />}
              />
              <div style={{ marginTop: 8, fontSize: 13, display: 'flex', justifyContent: 'space-between' }}>
                <div>
                  <Text type="warning">Attention needed</Text>
                  <Text type="secondary" style={{ marginLeft: 4 }}>Student alert review</Text>
                </div>
                <ExportOutlined style={{ color: 'var(--color-text-tertiary)' }} />
              </div>
            </Card>
          </Col>
        </Row>

        <Row gutter={[24, 24]}>
          {(overview?.todoCards || []).map(card => {
            const target = mapView(card.view);
            return (
              <Col xs={24} sm={12} lg={6} key={card.key}>
                <Card
                  bordered={false}
                  hoverable={Boolean(target)}
                  onClick={() => target && onChangeView(target)}
                  style={{ cursor: target ? 'pointer' : 'default', height: '100%' }}
                >
                  <Statistic
                    title={card.title}
                    value={card.count}
                    valueStyle={{ color: 'var(--color-text-primary)', fontWeight: 'bold' }}
                    prefix={<WarningOutlined style={{ color: mapTodoColor(card.status), marginRight: 8 }} />}
                  />
                  <div style={{ marginTop: 8, fontSize: 13, display: 'flex', justifyContent: 'space-between', gap: 12 }}>
                    <Text type="secondary">{card.description}</Text>
                    {target && <ExportOutlined style={{ color: 'var(--color-text-tertiary)', flex: '0 0 auto' }} />}
                  </div>
                </Card>
              </Col>
            );
          })}
        </Row>

        <Row gutter={[24, 24]}>
          <Col xs={24} lg={16}>
            <Card
              title="Ideology Integration Trend"
              bordered={false}
              bodyStyle={{ padding: 24 }}
              style={{ height: '100%' }}
              extra={<Text type="secondary">Percentage trend of knowledge points with ideology integration</Text>}
            >
              <div style={{ minHeight: 300, width: '100%' }}>
                {trendData.length > 0 ? (
                  <svg viewBox={`0 0 ${chartWidth} ${chartHeight}`} style={{ width: '100%', height: '100%' }}>
                    {[0, 25, 50, 75, 100].map(tick => {
                      const y = chartPadding.top + innerHeight - (tick / 100) * innerHeight;
                      return (
                        <g key={tick}>
                          <line
                            x1={chartPadding.left} y1={y}
                            x2={chartWidth - chartPadding.right} y2={y}
                            stroke="#F1F5F9" strokeWidth={1}
                          />
                          <text x={chartPadding.left - 8} y={y + 4} textAnchor="end" fill="#94A3B8" fontSize={11}>
                            {tick}%
                          </text>
                        </g>
                      );
                    })}
                    <defs>
                      <linearGradient id="areaGradient" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="var(--color-primary)" stopOpacity="0.15" />
                        <stop offset="100%" stopColor="var(--color-primary)" stopOpacity="0" />
                      </linearGradient>
                    </defs>
                    <path d={areaPath} fill="url(#areaGradient)" />
                    <path
                      d={linePath}
                      fill="none"
                      stroke="var(--color-primary)"
                      strokeWidth={2.5}
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                    {trendData.map((item, index) => {
                      const { x, y } = getPoint(index, item.value);
                      return (
                        <g key={index}>
                          <circle cx={x} cy={y} r={4} fill="var(--color-primary)" stroke="white" strokeWidth={2} />
                          <text
                            x={x} y={chartHeight - 10}
                            textAnchor="middle" fill="#94A3B8" fontSize={11}
                          >
                            {item.week}
                          </text>
                          <text x={x} y={y - 12} textAnchor="middle" fill="var(--color-primary)" fontSize={11} fontWeight="600">
                            {item.value}%
                          </text>
                        </g>
                      );
                    })}
                  </svg>
                ) : (
                  <Empty description="No trend data" style={{ marginTop: 60 }} />
                )}
              </div>
            </Card>
          </Col>
          <Col xs={24} lg={8}>
            <Card title="Latest Activity" bordered={false} bodyStyle={{ padding: '0 24px', height: 350, overflowY: 'auto' }}>
              {activities.length > 0 ? (
                <List<ActivityInfo>
                  itemLayout="horizontal"
                  dataSource={activities}
                  renderItem={item => {
                    const style = getActivityStyle(item.type);
                    return (
                      <List.Item>
                        <List.Item.Meta
                          avatar={<Avatar icon={style.icon} style={{ backgroundColor: style.bg, color: style.color }} />}
                          title={<Text strong style={{ fontSize: 13 }}>{item.title}</Text>}
                          description={
                            <Space direction="vertical" size={2}>
                              <Text type="secondary" style={{ fontSize: 12 }}>{item.description}</Text>
                              <Text type="secondary" style={{ fontSize: 11 }}>{item.createdAt}</Text>
                            </Space>
                          }
                        />
                      </List.Item>
                    );
                  }}
                />
              ) : (
                <Empty description="No activity yet" style={{ marginTop: 80 }} />
              )}
            </Card>
          </Col>
        </Row>

        <Row gutter={[24, 24]}>
          <Col xs={24} lg={10}>
            <Card title="Material Publishing" bordered={false} style={{ height: '100%' }}>
              <Row gutter={16}>
                <Col span={8}>
                  <Statistic title="Drafts" value={overview?.materialSummary?.draftCount || 0} prefix={<EditOutlined />} />
                </Col>
                <Col span={8}>
                  <Statistic title="Published" value={overview?.materialSummary?.publishedCount || 0} prefix={<CheckCircleOutlined />} />
                </Col>
                <Col span={8}>
                  <Statistic title="Latest" value={overview?.materialSummary?.latestCount || 0} prefix={<FileSearchOutlined />} />
                </Col>
              </Row>
              <Button style={{ marginTop: 24 }} onClick={() => onChangeView(View.COURSE_LIBRARY)}>
                Open Course Library
              </Button>
            </Card>
          </Col>
          <Col xs={24} lg={14}>
            <Card title="Recent Parse Tasks" bordered={false} bodyStyle={{ padding: '0 24px', height: 220, overflowY: 'auto' }}>
              {overview?.recentParseTasks?.length ? (
                <List
                  dataSource={overview.recentParseTasks}
                  renderItem={task => (
                    <List.Item
                      actions={[
                        <Button key="open" type="link" onClick={() => onChangeView(View.RESOURCE_UPLOAD, { resourceUploadTarget: { taskId: task.id } })}>
                          Open
                        </Button>,
                      ]}
                    >
                      <List.Item.Meta
                        avatar={<Avatar icon={<CloudUploadOutlined />} style={{ backgroundColor: 'var(--color-primary-soft)', color: 'var(--color-primary)' }} />}
                        title={<Space><Text strong>{task.fileName}</Text><Tag color={mapTaskStatus(task.status)}>{task.status}</Tag></Space>}
                        description={<Space direction="vertical" size={2} style={{ width: '100%' }}>
                          <Progress percent={task.progress} size="small" />
                          <Text type="secondary" style={{ fontSize: 11 }}>{task.updatedAt}</Text>
                        </Space>}
                      />
                    </List.Item>
                  )}
                />
              ) : (
                <Empty description="No parse tasks" style={{ marginTop: 40 }} />
              )}
            </Card>
          </Col>
        </Row>

        <Card title="My Courses" bordered={false} bodyStyle={{ padding: 0 }}>
          <Table
            columns={columns}
            dataSource={courses}
            rowKey="id"
            pagination={false}
            locale={{ emptyText: <Empty description="No course data" style={{ padding: '32px 0' }} /> }}
          />
        </Card>
      </div>
    </div>
  );
};
