import React, { useState, useEffect, useCallback } from 'react';
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
      const [graphData, acts] = await Promise.all([
        knowledgeApi.getGraph(),
        dashboardApi.getActivities(5),
      ]);
      setTotalNodes(graphData.nodes?.length ?? 0);
      setActivities(acts ?? []);
    } catch {
      // NOTE: 后端不可用时静默降级
    } finally {
      setLoading(false);
    }
  }, []);

  const loadPathRecommendation = useCallback(async () => {
    if (!currentUser?.id) return;
    setLoadingPath(true);
    try {
      const result = await pathApi.getRecommendedPath(currentUser.id, [], [], 6);
      setRecommendedPath(result);
    } catch {
      // NOTE: 推荐失败时静默处理
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
          <Text type="secondary">加载学习空间...</Text>
        </Space>
      </div>
    );
  }

  const greeting = (() => {
    const hour = new Date().getHours();
    if (hour < 12) return '早上好';
    if (hour < 18) return '下午好';
    return '晚上好';
  })();

  const displayName = currentUser?.realName || currentUser?.username || '同学';

  const getActivityIcon = (type: string) => {
    switch(type) {
      case 'UPLOAD': return <CloudUploadOutlined />;
      case 'AI_ANALYSIS': return <RobotOutlined />;
      case 'GRAPH_UPDATE': return <BlockOutlined />;
      default: return <InfoCircleOutlined />;
    }
  };

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: '32px' }}>
      <div style={{ maxWidth: 1100, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: '24px' }}>

        {/* 欢迎横幅 */}
        <div style={{ 
          background: 'linear-gradient(135deg, var(--color-primary) 0%, #722ed1 100%)', 
          borderRadius: 20, padding: 32, color: '#fff', position: 'relative', overflow: 'hidden' 
        }}>
          <div style={{ position: 'absolute', top: -32, right: -32, width: 160, height: 160, borderRadius: '50%', background: 'rgba(255,255,255,0.05)' }} />
          <div style={{ position: 'absolute', bottom: -16, right: -64, width: 128, height: 128, borderRadius: '50%', background: 'rgba(255,255,255,0.05)' }} />
          
          <div style={{ position: 'relative', zIndex: 10 }}>
            <Text style={{ color: 'rgba(255,255,255,0.85)', fontSize: 14 }}>{greeting}！</Text>
            <Title level={2} style={{ color: '#fff', margin: '4px 0 8px', fontFamily: "'Lexend', sans-serif" }}>
              欢迎回来，{displayName}
            </Title>
            <Paragraph style={{ color: 'rgba(255,255,255,0.85)', maxWidth: 480, fontSize: 14, marginBottom: 0 }}>
              智教思政平台已为您准备了今日的学习路径和思政内容，继续探索吧 🚀
            </Paragraph>
          </div>

          <Space size={12} style={{ position: 'relative', zIndex: 10, marginTop: 24 }}>
            <Button type="primary" ghost icon={<RobotOutlined />} onClick={() => onChangeView(View.AI_ASSISTANT)} style={{ color: '#fff', borderColor: 'rgba(255,255,255,0.4)', background: 'rgba(255,255,255,0.15)' }}>
              AI 助教
            </Button>
            <Button type="primary" ghost icon={<ShareAltOutlined />} onClick={() => onChangeView(View.KNOWLEDGE_GRAPH)} style={{ color: '#fff', borderColor: 'rgba(255,255,255,0.4)', background: 'rgba(255,255,255,0.15)' }}>
              知识图谱
            </Button>
            <Button type="primary" ghost icon={<BookOutlined />} onClick={() => onChangeView(View.COURSE_LIBRARY)} style={{ color: '#fff', borderColor: 'rgba(255,255,255,0.4)', background: 'rgba(255,255,255,0.15)' }}>
              课程资源
            </Button>
          </Space>
        </div>

        {/* 统计数字卡片 */}
        <Row gutter={[24, 24]}>
          <Col xs={24} md={8}>
            <Card bordered={false} bodyStyle={{ padding: 20 }}>
              <Statistic
                title={<Text type="secondary" style={{ fontSize: 13, fontWeight: 500 }}>知识图谱节点</Text>}
                value={totalNodes}
                suffix="个"
                prefix={<BlockOutlined style={{ color: 'var(--color-primary)', marginRight: 8 }} />}
                valueStyle={{ fontFamily: "'Lexend', sans-serif", fontWeight: 'bold' }}
              />
              <Text type="secondary" style={{ fontSize: 12, marginTop: 8, display: 'block' }}>可供探索的知识点</Text>
            </Card>
          </Col>
          <Col xs={24} md={8}>
            <Card bordered={false} bodyStyle={{ padding: 20 }}>
              <Statistic
                title={<Text type="secondary" style={{ fontSize: 13, fontWeight: 500 }}>已推荐路径节点</Text>}
                value={recommendedPath?.nodeIds?.length ?? 0}
                suffix="个"
                prefix={<NodeIndexOutlined style={{ color: '#722ed1', marginRight: 8 }} />}
                valueStyle={{ fontFamily: "'Lexend', sans-serif", fontWeight: 'bold' }}
              />
              <Text type="secondary" style={{ fontSize: 12, marginTop: 8, display: 'block' }}>个性化学习推荐</Text>
            </Card>
          </Col>
          <Col xs={24} md={8}>
            <Card bordered={false} bodyStyle={{ padding: 20 }}>
              <Statistic
                title={<Text type="secondary" style={{ fontSize: 13, fontWeight: 500 }}>近期系统动态</Text>}
                value={activities.length}
                suffix="条"
                prefix={<NotificationOutlined style={{ color: '#faad14', marginRight: 8 }} />}
                valueStyle={{ fontFamily: "'Lexend', sans-serif", fontWeight: 'bold' }}
              />
              <Text type="secondary" style={{ fontSize: 12, marginTop: 8, display: 'block' }}>资源与图谱更新</Text>
            </Card>
          </Col>
        </Row>

        <Row gutter={[24, 24]}>
          {/* 个性化学习路径推荐 */}
          <Col xs={24} lg={14}>
            <Card 
              bordered={false} 
              style={{ height: '100%' }}
              title={
                <div>
                  <Title level={5} style={{ margin: 0, fontFamily: "'Lexend', sans-serif" }}>个性化学习路径推荐</Title>
                  <Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>基于知识图谱 DAG 结构智能生成</Text>
                </div>
              }
              extra={
                <Tooltip title="重新生成推荐路径">
                  <Button type="text" icon={<SyncOutlined spin={loadingPath} />} onClick={loadPathRecommendation} disabled={loadingPath} />
                </Tooltip>
              }
            >
              {loadingPath ? (
                <div style={{ padding: '48px 0', textAlign: 'center' }}>
                  <Space direction="vertical" align="center" size={8}>
                    <Spin />
                    <Text type="secondary">正在生成推荐路径...</Text>
                  </Space>
                </div>
              ) : recommendedPath && recommendedPath.nodeNames.length > 0 ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  <List<string>
                    dataSource={recommendedPath.nodeNames}
                    renderItem={(name, idx) => (
                      <List.Item style={{ padding: '12px 16px', background: 'var(--color-bg-muted)', borderRadius: 12, marginBottom: 8, border: 'none' }}>
                        <List.Item.Meta
                          avatar={
                            <Avatar size={28} style={{ backgroundColor: 'var(--color-primary-soft)', color: 'var(--color-primary)', fontWeight: 'bold', fontSize: 13 }}>
                              {idx + 1}
                            </Avatar>
                          }
                          title={<Text strong style={{ fontSize: 14 }}>{name}</Text>}
                        />
                        {idx < recommendedPath.nodeNames.length - 1 && (
                          <ArrowRightOutlined style={{ color: '#cbd5e1' }} />
                        )}
                      </List.Item>
                    )}
                  />
                  <Button type="primary" size="large" icon={<CompassOutlined />} onClick={handleViewPath} block style={{ borderRadius: 12 }}>
                    在知识图谱中查看路径
                  </Button>
                </div>
              ) : (
                <Empty
                  image={<CompassOutlined style={{ fontSize: 48, color: '#e2e8f0' }} />}
                  description={
                    <Space direction="vertical" size={2}>
                      <Text strong style={{ color: 'var(--color-text-secondary)' }}>暂无推荐路径</Text>
                      <Text type="secondary" style={{ fontSize: 12 }}>知识图谱中暂无可推荐的节点</Text>
                    </Space>
                  }
                  style={{ padding: '48px 0' }}
                >
                  <Button onClick={() => onChangeView(View.KNOWLEDGE_GRAPH)}>前往知识图谱探索</Button>
                </Empty>
              )}
            </Card>
          </Col>

          {/* 近期系统动态 */}
          <Col xs={24} lg={10}>
             <Card 
               title="近期动态" 
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
                  <Empty description="暂无近期动态" style={{ padding: '48px 0' }} />
                )}
              </div>
              <div style={{ marginTop: 16 }}>
                <Button type="dashed" block icon={<BookOutlined />} onClick={() => onChangeView(View.COURSE_LIBRARY)} style={{ borderRadius: 12 }}>
                  浏览课程资源库
                </Button>
              </div>
            </Card>
          </Col>
        </Row>

      </div>
    </div>
  );
};
