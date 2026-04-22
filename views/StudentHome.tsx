import React, { useCallback, useEffect, useState } from 'react';
import { View } from '../types';
import { dashboardApi, knowledgeApi, pathApi, LearningPathResult, ActivityInfo } from '../services/api';
import { useAuth } from '../contexts/AuthContext';
import {
  Typography, Card, Row, Col, Statistic, Button,
  List, Avatar, Spin, Space, Empty, Tooltip
} from 'antd';
import {
  RobotOutlined, ShareAltOutlined, BookOutlined, BlockOutlined,
  NodeIndexOutlined, NotificationOutlined, SyncOutlined, ArrowRightOutlined,
  CloudUploadOutlined, InfoCircleOutlined, CompassOutlined
} from '@ant-design/icons';

const { Title, Text, Paragraph } = Typography;

interface StudentHomeProps {
  onChangeView: (view: View, options?: { highlightNodeIds?: number[] }) => void;
}

export const StudentHome: React.FC<StudentHomeProps> = ({ onChangeView }) => {
  const { currentUser } = useAuth();
  const [totalNodes, setTotalNodes] = useState<number>(0);
  const [activities, setActivities] = useState<ActivityInfo[]>([]);
  const [recommendedPath, setRecommendedPath] = useState<LearningPathResult | null>(null);
  const [loadingPath, setLoadingPath] = useState(false);
  const [loading, setLoading] = useState(true);

  const loadStats = useCallback(async () => {
    try {
      const [graphData, activityData] = await Promise.all([
        knowledgeApi.getGraph(),
        dashboardApi.getActivities(5),
      ]);
      setTotalNodes(graphData.nodes?.length ?? 0);
      setActivities(activityData ?? []);
    } catch {
      // Keep the student home page usable when the backend is unavailable.
    } finally {
      setLoading(false);
    }
  }, []);

  const loadPathRecommendation = useCallback(async () => {
    if (!currentUser?.id) {
      return;
    }

    setLoadingPath(true);
    try {
      const result = await pathApi.getRecommendedPath(currentUser.id, [], [], 6);
      setRecommendedPath(result);
    } catch {
      // Ignore recommendation failures and keep the current state.
    } finally {
      setLoadingPath(false);
    }
  }, [currentUser?.id]);

  useEffect(() => {
    loadStats();
    loadPathRecommendation();
  }, [loadStats, loadPathRecommendation]);

  const handleViewPath = () => {
    if (recommendedPath?.nodeIds && recommendedPath.nodeIds.length > 0) {
      onChangeView(View.KNOWLEDGE_GRAPH, { highlightNodeIds: recommendedPath.nodeIds });
    } else {
      onChangeView(View.KNOWLEDGE_GRAPH);
    }
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', flex: 1, alignItems: 'center', justifyContent: 'center', height: '100%' }}>
        <Space direction="vertical" align="center">
          <Spin size="large" />
          <Text type="secondary">Loading learning space...</Text>
        </Space>
      </div>
    );
  }

  const greeting = (() => {
    const hour = new Date().getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 18) return 'Good afternoon';
    return 'Good evening';
  })();

  const displayName = currentUser?.realName || currentUser?.username || 'Student';

  const getActivityIcon = (type: string) => {
    switch (type) {
      case 'UPLOAD': return <CloudUploadOutlined />;
      case 'AI_ANALYSIS': return <RobotOutlined />;
      case 'GRAPH_UPDATE': return <BlockOutlined />;
      default: return <InfoCircleOutlined />;
    }
  };

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: '32px' }}>
      <div style={{ maxWidth: 1100, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: '24px' }}>
        <div
          style={{
            background: 'linear-gradient(135deg, var(--color-primary) 0%, #722ed1 100%)',
            borderRadius: 20,
            padding: 32,
            color: '#fff',
            position: 'relative',
            overflow: 'hidden'
          }}
        >
          <div style={{ position: 'absolute', top: -32, right: -32, width: 160, height: 160, borderRadius: '50%', background: 'rgba(255,255,255,0.05)' }} />
          <div style={{ position: 'absolute', bottom: -16, right: -64, width: 128, height: 128, borderRadius: '50%', background: 'rgba(255,255,255,0.05)' }} />

          <div style={{ position: 'relative', zIndex: 10 }}>
            <Text style={{ color: 'rgba(255,255,255,0.85)', fontSize: 14 }}>{greeting},</Text>
            <Title level={2} style={{ color: '#fff', margin: '4px 0 8px', fontFamily: "'Lexend', sans-serif" }}>
              Welcome back, {displayName}
            </Title>
            <Paragraph style={{ color: 'rgba(255,255,255,0.85)', maxWidth: 480, fontSize: 14, marginBottom: 0 }}>
              Your learning dashboard is ready with today's recommended path and curriculum ideology resources.
            </Paragraph>
          </div>

          <Space size={12} style={{ position: 'relative', zIndex: 10, marginTop: 24 }}>
            <Button type="primary" ghost icon={<RobotOutlined />} onClick={() => onChangeView(View.AI_ASSISTANT)} style={{ color: '#fff', borderColor: 'rgba(255,255,255,0.4)', background: 'rgba(255,255,255,0.15)' }}>
              AI Assistant
            </Button>
            <Button type="primary" ghost icon={<ShareAltOutlined />} onClick={() => onChangeView(View.KNOWLEDGE_GRAPH)} style={{ color: '#fff', borderColor: 'rgba(255,255,255,0.4)', background: 'rgba(255,255,255,0.15)' }}>
              Knowledge Graph
            </Button>
            <Button type="primary" ghost icon={<BookOutlined />} onClick={() => onChangeView(View.COURSE_LIBRARY)} style={{ color: '#fff', borderColor: 'rgba(255,255,255,0.4)', background: 'rgba(255,255,255,0.15)' }}>
              Course Library
            </Button>
          </Space>
        </div>

        <Row gutter={[24, 24]}>
          <Col xs={24} md={8}>
            <Card bordered={false} bodyStyle={{ padding: 20 }}>
              <Statistic
                title={<Text type="secondary" style={{ fontSize: 13, fontWeight: 500 }}>Knowledge Graph Nodes</Text>}
                value={totalNodes}
                suffix="items"
                prefix={<BlockOutlined style={{ color: 'var(--color-primary)', marginRight: 8 }} />}
                valueStyle={{ fontFamily: "'Lexend', sans-serif", fontWeight: 'bold' }}
              />
              <Text type="secondary" style={{ fontSize: 12, marginTop: 8, display: 'block' }}>Available knowledge points to explore</Text>
            </Card>
          </Col>
          <Col xs={24} md={8}>
            <Card bordered={false} bodyStyle={{ padding: 20 }}>
              <Statistic
                title={<Text type="secondary" style={{ fontSize: 13, fontWeight: 500 }}>Recommended Path Nodes</Text>}
                value={recommendedPath?.nodeIds?.length ?? 0}
                suffix="items"
                prefix={<NodeIndexOutlined style={{ color: '#722ed1', marginRight: 8 }} />}
                valueStyle={{ fontFamily: "'Lexend', sans-serif", fontWeight: 'bold' }}
              />
              <Text type="secondary" style={{ fontSize: 12, marginTop: 8, display: 'block' }}>Personalized learning recommendation</Text>
            </Card>
          </Col>
          <Col xs={24} md={8}>
            <Card bordered={false} bodyStyle={{ padding: 20 }}>
              <Statistic
                title={<Text type="secondary" style={{ fontSize: 13, fontWeight: 500 }}>Recent System Updates</Text>}
                value={activities.length}
                suffix="items"
                prefix={<NotificationOutlined style={{ color: '#faad14', marginRight: 8 }} />}
                valueStyle={{ fontFamily: "'Lexend', sans-serif", fontWeight: 'bold' }}
              />
              <Text type="secondary" style={{ fontSize: 12, marginTop: 8, display: 'block' }}>Resource and graph changes</Text>
            </Card>
          </Col>
        </Row>

        <Row gutter={[24, 24]}>
          <Col xs={24} lg={14}>
            <Card
              bordered={false}
              style={{ height: '100%' }}
              title={
                <div>
                  <Title level={5} style={{ margin: 0, fontFamily: "'Lexend', sans-serif" }}>Personalized Learning Path</Title>
                  <Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>Generated from the DAG structure of the knowledge graph</Text>
                </div>
              }
              extra={
                <Tooltip title="Regenerate recommended path">
                  <Button type="text" icon={<SyncOutlined spin={loadingPath} />} onClick={loadPathRecommendation} disabled={loadingPath} />
                </Tooltip>
              }
            >
              {loadingPath ? (
                <div style={{ padding: '48px 0', textAlign: 'center' }}>
                  <Space direction="vertical" align="center" size={8}>
                    <Spin />
                    <Text type="secondary">Generating the recommended path...</Text>
                  </Space>
                </div>
              ) : recommendedPath && recommendedPath.nodeNames.length > 0 ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  <List<string>
                    dataSource={recommendedPath.nodeNames}
                    renderItem={(name, index) => (
                      <List.Item style={{ padding: '12px 16px', background: 'var(--color-bg-muted)', borderRadius: 12, marginBottom: 8, border: 'none' }}>
                        <List.Item.Meta
                          avatar={
                            <Avatar size={28} style={{ backgroundColor: 'var(--color-primary-soft)', color: 'var(--color-primary)', fontWeight: 'bold', fontSize: 13 }}>
                              {index + 1}
                            </Avatar>
                          }
                          title={<Text strong style={{ fontSize: 14 }}>{name}</Text>}
                        />
                        {index < recommendedPath.nodeNames.length - 1 && (
                          <ArrowRightOutlined style={{ color: '#cbd5e1' }} />
                        )}
                      </List.Item>
                    )}
                  />
                  <Button type="primary" size="large" icon={<CompassOutlined />} onClick={handleViewPath} block style={{ borderRadius: 12 }}>
                    View the path in the knowledge graph
                  </Button>
                </div>
              ) : (
                <Empty
                  image={<CompassOutlined style={{ fontSize: 48, color: '#e2e8f0' }} />}
                  description={
                    <Space direction="vertical" size={2}>
                      <Text strong style={{ color: 'var(--color-text-secondary)' }}>No recommended path yet</Text>
                      <Text type="secondary" style={{ fontSize: 12 }}>There are currently no recommended nodes in the knowledge graph</Text>
                    </Space>
                  }
                  style={{ padding: '48px 0' }}
                >
                  <Button onClick={() => onChangeView(View.KNOWLEDGE_GRAPH)}>Explore the knowledge graph</Button>
                </Empty>
              )}
            </Card>
          </Col>

          <Col xs={24} lg={10}>
            <Card
              title="Recent Activity"
              bordered={false}
              style={{ height: '100%', display: 'flex', flexDirection: 'column' }}
              bodyStyle={{ flex: 1, display: 'flex', flexDirection: 'column' }}
            >
              <div style={{ flex: 1 }}>
                {activities.length > 0 ? (
                  <List<ActivityInfo>
                    itemLayout="horizontal"
                    dataSource={activities}
                    renderItem={activity => (
                      <List.Item style={{ borderBottom: '1px solid var(--color-bg-muted)' }}>
                        <List.Item.Meta
                          avatar={
                            <Avatar icon={getActivityIcon(activity.type)} style={{ backgroundColor: 'var(--color-primary-soft)', color: 'var(--color-primary)' }} />
                          }
                          title={<Text strong style={{ fontSize: 14 }}>{activity.title}</Text>}
                          description={
                            <Space direction="vertical" size={2}>
                              <Text type="secondary" ellipsis style={{ fontSize: 12, maxWidth: 220 }}>{activity.description}</Text>
                              <Text type="secondary" style={{ fontSize: 11, opacity: 0.8 }}>{activity.createdAt}</Text>
                            </Space>
                          }
                        />
                      </List.Item>
                    )}
                  />
                ) : (
                  <Empty description="No recent activity" style={{ padding: '48px 0' }} />
                )}
              </div>
              <div style={{ marginTop: 16 }}>
                <Button type="dashed" block icon={<BookOutlined />} onClick={() => onChangeView(View.COURSE_LIBRARY)} style={{ borderRadius: 12 }}>
                  Browse the course library
                </Button>
              </div>
            </Card>
          </Col>
        </Row>
      </div>
    </div>
  );
};
