import React, { useState, useEffect } from 'react';
import {
  dashboardApi,
  courseApi,
  CourseInfo,
  KnowledgeNodeInfo,
} from '../services/api';
import {
  Input, Button, Collapse, Modal, Form, Tag, Progress,
  Space, Spin, Empty, Typography, Avatar, Row, Col, Card
} from 'antd';
import {
  SearchOutlined, PlusOutlined, BookOutlined, InteractionOutlined,
  ShareAltOutlined, RobotOutlined, CodeOutlined, HeartOutlined
} from '@ant-design/icons';

const { Text, Title } = Typography;
const { Panel } = Collapse;

export const ResourceLibrary: React.FC = () => {
  const [courses, setCourses] = useState<CourseInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedKeys, setExpandedKeys] = useState<string[]>([]);
  const [knowledgePointsMap, setKnowledgePointsMap] = useState<Record<number, KnowledgeNodeInfo[]>>({});
  const [kpLoadingMap, setKpLoadingMap] = useState<Record<number, boolean>>({});
  const [searchKeyword, setSearchKeyword] = useState('');

  // 新建课程弹窗
  const [showNewCourse, setShowNewCourse] = useState(false);
  const [form] = Form.useForm();
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    const loadCourses = async () => {
      try {
        const data = await dashboardApi.getCourses();
        setCourses(data);
      } catch {
        // NOTE: 后端未连接保持空列表
      } finally {
        setLoading(false);
      }
    };
    loadCourses();
  }, []);

  const handleExpand = async (keys: string[] | string) => {
    const newExpandedKeys = Array.isArray(keys) ? keys : [keys];
    setExpandedKeys(newExpandedKeys);

    // 找到新展开的ID
    const expandedIds = newExpandedKeys.map(k => Number(k));
    for (const id of expandedIds) {
      if (!knowledgePointsMap[id] && !kpLoadingMap[id]) {
        // 需要加载
        setKpLoadingMap(prev => ({ ...prev, [id]: true }));
        try {
          const points = await courseApi.getKnowledgePoints(id);
          setKnowledgePointsMap(prev => ({ ...prev, [id]: points }));
        } catch {
          setKnowledgePointsMap(prev => ({ ...prev, [id]: [] }));
        } finally {
          setKpLoadingMap(prev => ({ ...prev, [id]: false }));
        }
      }
    }
  };

  const handleCreateCourse = async () => {
    try {
      const values = await form.validateFields();
      setCreating(true);
      const newCourse = await courseApi.create({
        name: values.name,
        code: values.code || '',
        description: values.description || '',
        semester: values.semester || '',
      });
      setCourses(prev => [{
        ...newCourse,
        gradeColor: newCourse.gradeColor || 'warning',
        gradeLabel: newCourse.gradeLabel || '一般',
        progress: 0,
      }, ...prev]);
      setShowNewCourse(false);
      form.resetFields();
    } catch (e) {
      // ignore
    } finally {
      setCreating(false);
    }
  };

  const filteredCourses = courses.filter(c =>
    !searchKeyword.trim() || c.name.toLowerCase().includes(searchKeyword.toLowerCase())
  );

  const mapGradeColor = (color: string) => {
    const mapping: Record<string, string> = {
      'orange': 'warning',
      'emerald': 'success',
      'blue': 'processing',
      'red': 'error',
    };
    return mapping[color] || 'default';
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', flex: 1, alignItems: 'center', justifyContent: 'center', height: '100%' }}>
        <Space direction="vertical" align="center">
          <Spin size="large" />
          <Text type="secondary">加载课程库...</Text>
        </Space>
      </div>
    );
  }

  const renderPanelHeader = (course: CourseInfo) => (
    <Row align="middle" style={{ width: '100%' }} wrap={false}>
      <Col flex="48px">
        <Avatar shape="square" size="large" style={{ backgroundColor: '#e6f4ff', color: '#1677ff' }}>
          {course.name?.charAt(0) || '课'}
        </Avatar>
      </Col>
      <Col flex="auto">
        <div style={{ paddingLeft: 12 }}>
          <Text strong style={{ fontSize: 16 }}>{course.name}</Text>
          <div style={{ marginTop: 4 }}>
            <Tag color={mapGradeColor(course.gradeColor || '')}>{course.gradeLabel}</Tag>
            <Text type="secondary" style={{ fontSize: 13, marginLeft: 8 }}>
              <InteractionOutlined /> 进度 {course.progress}%
            </Text>
          </div>
        </div>
      </Col>
      <Col flex="140px">
        <Progress percent={course.progress || 0} size="small" showInfo={false} />
      </Col>
    </Row>
  );

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: '32px' }}>
      <div style={{ maxWidth: 1000, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: '24px' }}>
        
        {/* 头部 */}
        <Row justify="space-between" align="middle">
          <Col>
            <Title level={2} style={{ margin: 0, fontFamily: "'Lexend', sans-serif" }}>课程库</Title>
            <Text type="secondary">管理课程并查看知识点思政映射</Text>
          </Col>
          <Col>
            <Space size={16}>
              <Input
                placeholder="搜索课程..."
                prefix={<SearchOutlined />}
                value={searchKeyword}
                onChange={e => setSearchKeyword(e.target.value)}
                style={{ width: 200, borderRadius: 8 }}
                allowClear
              />
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setShowNewCourse(true)}>
                新建课程
              </Button>
            </Space>
          </Col>
        </Row>

        {/* 列表 */}
        {filteredCourses.length > 0 ? (
          <Collapse
            activeKey={expandedKeys}
            onChange={handleExpand}
            expandIconPosition="end"
            ghost
            items={filteredCourses.map(course => ({
              key: String(course.id),
              label: renderPanelHeader(course),
              style: {
                marginBottom: 16,
                background: '#fff',
                borderRadius: 12,
                border: '1px solid #f0f0f0',
                boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
                overflow: 'hidden',
              },
              children: (
                <div style={{ padding: '0 8px 8px' }}>
                  <Text strong style={{ fontSize: 14, display: 'block', marginBottom: 16 }}>
                    <ShareAltOutlined style={{ color: '#1677ff', marginRight: 8 }} />
                    关联知识点 · 思政映射
                  </Text>
                  
                  {kpLoadingMap[course.id] ? (
                    <div style={{ textAlign: 'center', padding: '24px 0' }}><Spin /></div>
                  ) : (knowledgePointsMap[course.id] || []).length > 0 ? (
                    <Space direction="vertical" style={{ width: '100%' }} size={12}>
                      {(knowledgePointsMap[course.id] || []).map(kp => (
                        <Card key={kp.id} size="small" bordered style={{ borderColor: '#f0f0f0' }}>
                          <Row wrap={false} gutter={16}>
                            <Col>
                              <Avatar 
                                icon={kp.nodeType === 'IDEO' ? <RobotOutlined /> : <CodeOutlined />} 
                                style={{
                                  backgroundColor: kp.nodeType === 'IDEO' ? '#fff1f0' : '#e6f4ff',
                                  color: kp.nodeType === 'IDEO' ? '#cf1322' : '#0958d9'
                                }}
                              />
                            </Col>
                            <Col flex="auto">
                              <Space style={{ marginBottom: 4 }}>
                                <Text strong>{kp.name}</Text>
                                <Tag color={kp.nodeType === 'IDEO' ? 'error' : 'processing'} bordered={false}>
                                  {kp.nodeType === 'TECH' ? '技术' : '思政'}
                                </Tag>
                              </Space>
                              {kp.technicalDefinition && (
                                <Text type="secondary" style={{ display: 'block', marginBottom: kp.ideologicalValue ? 8 : 0, fontSize: 13 }}>
                                  {kp.technicalDefinition}
                                </Text>
                              )}
                              {kp.ideologicalValue && (
                                <div style={{
                                  padding: '8px 12px', background: '#fff1f0', borderRadius: 8,
                                  border: '1px solid #ffa39e', display: 'flex', gap: 6, marginTop: 8
                                }}>
                                  <HeartOutlined style={{ color: '#cf1322', marginTop: 2 }} />
                                  <Text style={{ color: '#a8071a', fontSize: 13 }}>{kp.ideologicalValue}</Text>
                                </div>
                              )}
                            </Col>
                          </Row>
                        </Card>
                      ))}
                    </Space>
                  ) : (
                    <Empty description="暂无关联知识点" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                  )}
                </div>
              )
            }))}
          />
        ) : (
          <Empty
            description="暂无课程数据"
            image={<BookOutlined style={{ fontSize: 48, color: '#d9d9d9' }} />}
            style={{ padding: '64px 0', background: '#fff', borderRadius: 12, border: '1px solid #f0f0f0' }}
          />
        )}
      </div>

      <Modal
        title="新建课程"
        open={showNewCourse}
        onCancel={() => setShowNewCourse(false)}
        onOk={handleCreateCourse}
        okText="创建课程"
        cancelText="取消"
        confirmLoading={creating}
      >
        <Form form={form} layout="vertical" requiredMark={false}>
          <Form.Item name="name" label="课程名称" rules={[{ required: true, message: '请输入课程名称' }]}>
            <Input placeholder="如：物联网技术与应用" />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="code" label="课程编码">
                <Input placeholder="如：CS101" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="semester" label="学期">
                <Input placeholder="如：2026春" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="课程描述">
            <Input.TextArea placeholder="课程简介与教学目标..." rows={3} autoSize={{ minRows: 3, maxRows: 6 }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};