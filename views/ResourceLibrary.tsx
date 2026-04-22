import React, { useEffect, useState } from 'react';
import {
  dashboardApi,
  courseApi,
  materialApi,
  CourseInfo,
  CourseTeachingMaterialGroupInfo,
  KnowledgeNodeInfo,
  TeachingMaterialViewInfo,
} from '../services/api';
import { downloadBlobFile } from '../services/download';
import { useAuth } from '../contexts/AuthContext';
import { View, ViewChangeHandler } from '../types';
import {
  Input, Button, Collapse, Modal, Form, Tag, Progress,
  Space, Spin, Empty, Typography, Avatar, Row, Col, Card,
  Drawer, Divider, List, message
} from 'antd';
import {
  SearchOutlined, PlusOutlined, InteractionOutlined,
  ShareAltOutlined, RobotOutlined, CodeOutlined, HeartOutlined,
  FileTextOutlined, DownloadOutlined, EditOutlined, BookOutlined
} from '@ant-design/icons';

const { Text, Title, Paragraph } = Typography;

interface ResourceLibraryProps {
  onChangeView?: ViewChangeHandler;
}

export const ResourceLibrary: React.FC<ResourceLibraryProps> = ({ onChangeView }) => {
  const { roleUi, currentUser } = useAuth();
  const canManageMaterials = !!roleUi?.capabilities?.canUploadResource;

  const [courses, setCourses] = useState<CourseInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedKeys, setExpandedKeys] = useState<string[]>([]);
  const [knowledgePointsMap, setKnowledgePointsMap] = useState<Record<number, KnowledgeNodeInfo[]>>({});
  const [kpLoadingMap, setKpLoadingMap] = useState<Record<number, boolean>>({});
  const [materialGroupsMap, setMaterialGroupsMap] = useState<Record<number, CourseTeachingMaterialGroupInfo[]>>({});
  const [materialLoadingMap, setMaterialLoadingMap] = useState<Record<number, boolean>>({});
  const [searchKeyword, setSearchKeyword] = useState('');

  const [showNewCourse, setShowNewCourse] = useState(false);
  const [form] = Form.useForm();
  const [creating, setCreating] = useState(false);

  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewMaterial, setPreviewMaterial] = useState<TeachingMaterialViewInfo | null>(null);
  const [previewTaskId, setPreviewTaskId] = useState<number | null>(null);
  const [previewSourceFileName, setPreviewSourceFileName] = useState('');
  const [exportingMaterialId, setExportingMaterialId] = useState<number | null>(null);

  useEffect(() => {
    const loadCourses = async () => {
      try {
        const data = await dashboardApi.getCourses();
        setCourses(data);
      } catch {
        // Keep an empty state when the backend is unavailable.
      } finally {
        setLoading(false);
      }
    };
    loadCourses();
  }, []);

  const loadKnowledgePoints = async (courseId: number) => {
    if (knowledgePointsMap[courseId] || kpLoadingMap[courseId]) {
      return;
    }
    setKpLoadingMap(previous => ({ ...previous, [courseId]: true }));
    try {
      const points = await courseApi.getKnowledgePoints(courseId);
      setKnowledgePointsMap(previous => ({ ...previous, [courseId]: points }));
    } catch {
      setKnowledgePointsMap(previous => ({ ...previous, [courseId]: [] }));
    } finally {
      setKpLoadingMap(previous => ({ ...previous, [courseId]: false }));
    }
  };

  const loadCourseMaterials = async (courseId: number) => {
    if (!canManageMaterials || materialGroupsMap[courseId] || materialLoadingMap[courseId]) {
      return;
    }
    setMaterialLoadingMap(previous => ({ ...previous, [courseId]: true }));
    try {
      const groups = await courseApi.getMaterials(courseId);
      setMaterialGroupsMap(previous => ({ ...previous, [courseId]: groups }));
    } catch {
      setMaterialGroupsMap(previous => ({ ...previous, [courseId]: [] }));
    } finally {
      setMaterialLoadingMap(previous => ({ ...previous, [courseId]: false }));
    }
  };

  const handleExpand = async (keys: string[] | string) => {
    const nextExpandedKeys = Array.isArray(keys) ? keys : [keys];
    setExpandedKeys(nextExpandedKeys);
    await Promise.all(nextExpandedKeys.map(async rawId => {
      const courseId = Number(rawId);
      await Promise.all([
        loadKnowledgePoints(courseId),
        loadCourseMaterials(courseId),
      ]);
    }));
  };

  const handleCreateCourse = async () => {
    try {
      const values = await form.validateFields();
      if (!currentUser?.id) {
        message.error('Current user is unavailable');
        return;
      }

      setCreating(true);
      const newCourse = await courseApi.create({
        name: values.name,
        code: values.code || '',
        description: values.description || '',
        semester: values.semester || '',
        teacherId: currentUser.id,
      });

      setCourses(previous => [{
        ...newCourse,
        gradeColor: newCourse.gradeColor || 'warning',
        gradeLabel: newCourse.gradeLabel || 'General',
        progress: 0,
      }, ...previous]);
      setShowNewCourse(false);
      form.resetFields();
    } catch {
      // Ignore validation errors from the modal form.
    } finally {
      setCreating(false);
    }
  };

  const handlePreviewMaterial = async (materialId: number, taskId: number, sourceFileName: string) => {
    setPreviewOpen(true);
    setPreviewLoading(true);
    setPreviewTaskId(taskId);
    setPreviewSourceFileName(sourceFileName);
    try {
      const material = await materialApi.getById(materialId);
      setPreviewMaterial(material);
    } catch (error: unknown) {
      setPreviewMaterial(null);
      message.error(error instanceof Error ? error.message : 'Failed to load teaching material');
    } finally {
      setPreviewLoading(false);
    }
  };

  const downloadMarkdown = async (materialId: number) => {
    setExportingMaterialId(materialId);
    try {
      const blob = await materialApi.exportMarkdown(materialId);
      downloadBlobFile(blob, `teaching-material-${materialId}.md`);
      message.success('Markdown exported');
    } catch (error: unknown) {
      message.error(error instanceof Error ? error.message : 'Failed to export markdown');
    } finally {
      setExportingMaterialId(null);
    }
  };

  const handleContinueEditing = (taskId: number, materialId?: number) => {
    if (!onChangeView) {
      return;
    }

    onChangeView(View.RESOURCE_UPLOAD, {
      resourceUploadTarget: {
        taskId,
        materialId,
      },
    });
  };

  const filteredCourses = courses.filter(course =>
    !searchKeyword.trim() || course.name.toLowerCase().includes(searchKeyword.toLowerCase())
  );

  const mapGradeColor = (color: string) => {
    const mapping: Record<string, string> = {
      orange: 'warning',
      emerald: 'success',
      blue: 'processing',
      red: 'error',
    };
    return mapping[color] || 'default';
  };

  const mapMaterialStatusColor = (status?: string) => {
    switch (status) {
      case 'PUBLISHED':
        return 'green';
      case 'DRAFT':
        return 'orange';
      default:
        return 'default';
    }
  };

  const formatTimestamp = (value?: string) => {
    if (!value) {
      return '-';
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
  };

  const renderPanelHeader = (course: CourseInfo) => (
    <Row align="middle" style={{ width: '100%' }} wrap={false}>
      <Col flex="48px">
        <Avatar shape="square" size="large" style={{ backgroundColor: '#e6f4ff', color: '#1677ff' }}>
          {course.name?.charAt(0) || 'C'}
        </Avatar>
      </Col>
      <Col flex="auto">
        <div style={{ paddingLeft: 12 }}>
          <Text strong style={{ fontSize: 16 }}>{course.name}</Text>
          <div style={{ marginTop: 4 }}>
            <Tag color={mapGradeColor(course.gradeColor || '')}>{course.gradeLabel}</Tag>
            <Text type="secondary" style={{ fontSize: 13, marginLeft: 8 }}>
              <InteractionOutlined /> Progress {course.progress}%
            </Text>
          </div>
        </div>
      </Col>
      <Col flex="140px">
        <Progress percent={course.progress || 0} size="small" showInfo={false} />
      </Col>
    </Row>
  );

  const renderKnowledgeSection = (courseId: number) => {
    if (kpLoadingMap[courseId]) {
      return <div style={{ textAlign: 'center', padding: '24px 0' }}><Spin /></div>;
    }

    const points = knowledgePointsMap[courseId] || [];
    if (points.length === 0) {
      return <Empty description="No linked knowledge points." image={Empty.PRESENTED_IMAGE_SIMPLE} />;
    }

    return (
      <Space direction="vertical" style={{ width: '100%' }} size={12}>
        {points.map(point => (
          <Card key={point.id} size="small" bordered style={{ borderColor: '#f0f0f0' }}>
            <Row wrap={false} gutter={16}>
              <Col>
                <Avatar
                  icon={point.nodeType === 'IDEO' ? <RobotOutlined /> : <CodeOutlined />}
                  style={{
                    backgroundColor: point.nodeType === 'IDEO' ? '#fff1f0' : '#e6f4ff',
                    color: point.nodeType === 'IDEO' ? '#cf1322' : '#0958d9'
                  }}
                />
              </Col>
              <Col flex="auto">
                <Space style={{ marginBottom: 4 }}>
                  <Text strong>{point.name}</Text>
                  <Tag color={point.nodeType === 'IDEO' ? 'error' : 'processing'} bordered={false}>
                    {point.nodeType === 'TECH' ? 'Technology' : 'Ideology'}
                  </Tag>
                </Space>
                {point.technicalDefinition && (
                  <Text type="secondary" style={{ display: 'block', marginBottom: point.ideologicalValue ? 8 : 0, fontSize: 13 }}>
                    {point.technicalDefinition}
                  </Text>
                )}
                {point.ideologicalValue && (
                  <div
                    style={{
                      padding: '8px 12px',
                      background: '#fff1f0',
                      borderRadius: 8,
                      border: '1px solid #ffa39e',
                      display: 'flex',
                      gap: 6,
                      marginTop: 8
                    }}
                  >
                    <HeartOutlined style={{ color: '#cf1322', marginTop: 2 }} />
                    <Text style={{ color: '#a8071a', fontSize: 13 }}>{point.ideologicalValue}</Text>
                  </div>
                )}
              </Col>
            </Row>
          </Card>
        ))}
      </Space>
    );
  };

  const renderMaterialsSection = (courseId: number) => {
    if (!canManageMaterials) {
      return null;
    }

    const groups = materialGroupsMap[courseId] || [];
    return (
      <div style={{ marginTop: 24 }}>
        <Text strong style={{ fontSize: 14, display: 'block', marginBottom: 16 }}>
          <FileTextOutlined style={{ color: '#1677ff', marginRight: 8 }} />
          Teaching Materials
        </Text>

        {materialLoadingMap[courseId] ? (
          <div style={{ textAlign: 'center', padding: '24px 0' }}><Spin /></div>
        ) : groups.length > 0 ? (
          <Space direction="vertical" style={{ width: '100%' }} size={12}>
            {groups.map(group => {
              const historyVersions = (group.versions || []).filter(
                version => version.materialId !== group.latestMaterialId
              );
              const latestMaterialId = group.latestMaterialId;

              return (
                <Card key={`material-group-${group.parseTaskId}`} size="small" bordered style={{ borderColor: '#f0f0f0' }}>
                  <Space direction="vertical" style={{ width: '100%' }} size={12}>
                    <Space style={{ width: '100%', justifyContent: 'space-between' }} align="start" wrap>
                      <Space direction="vertical" size={2}>
                        <Text strong style={{ fontSize: 15 }}>
                          {group.displayTitle || group.sourceFileName || `Task ${group.parseTaskId}`}
                        </Text>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          Source file: {group.sourceFileName || '-'}
                        </Text>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          Updated: {formatTimestamp(group.updatedAt)}
                        </Text>
                      </Space>
                      <Space wrap>
                        <Tag color="blue">Latest v{group.latestVersionNo || '-'}</Tag>
                        <Tag color={mapMaterialStatusColor(group.latestStatus)}>{group.latestStatus || 'UNKNOWN'}</Tag>
                      </Space>
                    </Space>

                    <Space wrap>
                      <Button
                        size="small"
                        onClick={() => latestMaterialId && handlePreviewMaterial(latestMaterialId, group.parseTaskId, group.sourceFileName)}
                        disabled={!latestMaterialId}
                      >
                        View
                      </Button>
                      <Button
                        size="small"
                        icon={<DownloadOutlined />}
                        onClick={() => latestMaterialId && downloadMarkdown(latestMaterialId)}
                        disabled={!latestMaterialId}
                        loading={exportingMaterialId === latestMaterialId}
                      >
                        Export Markdown
                      </Button>
                      <Button
                        size="small"
                        type="primary"
                        icon={<EditOutlined />}
                        onClick={() => handleContinueEditing(group.parseTaskId, latestMaterialId || undefined)}
                        disabled={!latestMaterialId || !onChangeView}
                      >
                        Continue Editing
                      </Button>
                    </Space>

                    <Divider style={{ margin: '4px 0' }} />
                    <Text strong>Version History</Text>
                    {historyVersions.length > 0 ? (
                      <List
                        size="small"
                        dataSource={historyVersions}
                        renderItem={version => (
                          <List.Item
                            actions={[
                              <Button
                                key="view"
                                size="small"
                                onClick={() => handlePreviewMaterial(version.materialId, group.parseTaskId, group.sourceFileName)}
                              >
                                View
                              </Button>,
                              <Button
                                key="export"
                                size="small"
                                onClick={() => downloadMarkdown(version.materialId)}
                                loading={exportingMaterialId === version.materialId}
                              >
                                Export
                              </Button>,
                              <Button
                                key="edit"
                                size="small"
                                type="primary"
                                onClick={() => handleContinueEditing(group.parseTaskId, version.materialId)}
                                disabled={!onChangeView}
                              >
                                Continue Editing
                              </Button>,
                            ]}
                          >
                            <Space wrap>
                              <Text>v{version.versionNo}</Text>
                              <Tag color={mapMaterialStatusColor(version.status)}>{version.status}</Tag>
                              <Text type="secondary" style={{ fontSize: 12 }}>
                                {formatTimestamp(version.updatedAt)}
                              </Text>
                            </Space>
                          </List.Item>
                        )}
                      />
                    ) : (
                      <Text type="secondary">No older versions.</Text>
                    )}
                  </Space>
                </Card>
              );
            })}
          </Space>
        ) : (
          <Empty description="No saved teaching materials." image={Empty.PRESENTED_IMAGE_SIMPLE} />
        )}
      </div>
    );
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', flex: 1, alignItems: 'center', justifyContent: 'center', height: '100%' }}>
        <Space direction="vertical" align="center">
          <Spin size="large" />
          <Text type="secondary">Loading course library...</Text>
        </Space>
      </div>
    );
  }

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: '32px' }}>
      <div style={{ maxWidth: 1000, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: '24px' }}>
        <Row justify="space-between" align="middle">
          <Col>
            <Title level={2} style={{ margin: 0, fontFamily: "'Lexend', sans-serif" }}>Course Library</Title>
            <Text type="secondary">Manage courses and review linked knowledge points and ideology mapping.</Text>
          </Col>
          <Col>
            <Space size={16}>
              <Input
                placeholder="Search courses..."
                prefix={<SearchOutlined />}
                value={searchKeyword}
                onChange={event => setSearchKeyword(event.target.value)}
                style={{ width: 200, borderRadius: 8 }}
                allowClear
              />
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setShowNewCourse(true)}>
                New Course
              </Button>
            </Space>
          </Col>
        </Row>

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
                    Linked Knowledge Points and Ideology Mapping
                  </Text>
                  {renderKnowledgeSection(course.id)}
                  {renderMaterialsSection(course.id)}
                </div>
              )
            }))}
          />
        ) : (
          <Empty
            description="No courses found."
            image={<BookOutlined style={{ fontSize: 48, color: '#d9d9d9' }} />}
            style={{ padding: '64px 0', background: '#fff', borderRadius: 12, border: '1px solid #f0f0f0' }}
          />
        )}
      </div>

      <Drawer
        title={previewMaterial?.title || 'Teaching Material Preview'}
        open={previewOpen}
        onClose={() => {
          setPreviewOpen(false);
          setPreviewMaterial(null);
          setPreviewTaskId(null);
          setPreviewSourceFileName('');
        }}
        width={720}
        extra={
          <Space>
            <Button
              icon={<DownloadOutlined />}
              onClick={() => previewMaterial && downloadMarkdown(previewMaterial.materialId)}
              disabled={!previewMaterial}
              loading={previewMaterial != null && exportingMaterialId === previewMaterial.materialId}
            >
              Export Markdown
            </Button>
            <Button
              type="primary"
              icon={<EditOutlined />}
              onClick={() => previewTaskId && previewMaterial && handleContinueEditing(previewTaskId, previewMaterial.materialId)}
              disabled={!previewMaterial || !previewTaskId || !onChangeView}
            >
              Continue Editing
            </Button>
          </Space>
        }
      >
        {previewLoading ? (
          <div style={{ textAlign: 'center', padding: '80px 0' }}>
            <Spin />
          </div>
        ) : previewMaterial ? (
          <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <Card size="small">
              <Space direction="vertical" size={4}>
                <Text strong>Version</Text>
                <Text>v{previewMaterial.versionNo || '-'}</Text>
                <Text strong>Status</Text>
                <Tag color={mapMaterialStatusColor(previewMaterial.status)}>{previewMaterial.status || 'UNKNOWN'}</Tag>
                <Text strong>Source File</Text>
                <Text>{previewSourceFileName || '-'}</Text>
                <Text strong>Updated</Text>
                <Text>{formatTimestamp(previewMaterial.updatedAt)}</Text>
              </Space>
            </Card>

            <div>
              <Text strong>Lecture Notes</Text>
              <Paragraph style={{ whiteSpace: 'pre-wrap', marginTop: 8 }}>
                {previewMaterial.lectureNotes || 'No lecture notes.'}
              </Paragraph>
            </div>

            <div>
              <Text strong>Cases</Text>
              {(previewMaterial.cases || []).length > 0 ? (
                <List
                  size="small"
                  dataSource={previewMaterial.cases}
                  renderItem={item => <List.Item>{item}</List.Item>}
                  style={{ marginTop: 8 }}
                />
              ) : (
                <Text type="secondary" style={{ display: 'block', marginTop: 8 }}>No cases.</Text>
              )}
            </div>

            <div>
              <Text strong>Questions</Text>
              {(previewMaterial.questions || []).length > 0 ? (
                <Space direction="vertical" style={{ width: '100%', marginTop: 8 }}>
                  {previewMaterial.questions.map((question, index) => (
                    <Card key={`preview-question-${index}`} size="small">
                      <Space direction="vertical" size={8} style={{ width: '100%' }}>
                        <Text strong>{question.stem || `Question ${index + 1}`}</Text>
                        <div>
                          <Text strong>Reference Answer</Text>
                          <Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
                            {question.referenceAnswer || 'No reference answer.'}
                          </Paragraph>
                        </div>
                        <div>
                          <Text strong>Scoring Points</Text>
                          {(question.scoringPoints || []).length > 0 ? (
                            <List
                              size="small"
                              dataSource={question.scoringPoints}
                              renderItem={item => <List.Item>{item}</List.Item>}
                            />
                          ) : (
                            <Text type="secondary">No scoring points.</Text>
                          )}
                        </div>
                      </Space>
                    </Card>
                  ))}
                </Space>
              ) : (
                <Text type="secondary" style={{ display: 'block', marginTop: 8 }}>No questions.</Text>
              )}
            </div>

            <div>
              <Text strong>Trace Summary</Text>
              {(previewMaterial.traceItems || []).length > 0 ? (
                <Space direction="vertical" style={{ width: '100%', marginTop: 8 }}>
                  {previewMaterial.traceItems.map((trace, index) => (
                    <Card key={`preview-trace-${index}`} size="small">
                      <Space direction="vertical" size={6}>
                        <Space wrap>
                          <Tag color="blue">{trace.knowledgePointName || 'Unknown Point'}</Tag>
                          <Tag color="red">{trace.ideologyElement || 'Unknown Element'}</Tag>
                        </Space>
                        <Text type="secondary">{trace.evidenceSnippet || '-'}</Text>
                        <Text>{trace.matchReason || '-'}</Text>
                      </Space>
                    </Card>
                  ))}
                </Space>
              ) : (
                <Text type="secondary" style={{ display: 'block', marginTop: 8 }}>No trace summary.</Text>
              )}
            </div>
          </Space>
        ) : (
          <Empty description="No material selected." />
        )}
      </Drawer>

      <Modal
        title="New Course"
        open={showNewCourse}
        onCancel={() => setShowNewCourse(false)}
        onOk={handleCreateCourse}
        okText="Create Course"
        cancelText="Cancel"
        confirmLoading={creating}
      >
        <Form form={form} layout="vertical" requiredMark={false}>
          <Form.Item name="name" label="Course Name" rules={[{ required: true, message: 'Please enter the course name' }]}>
            <Input placeholder="For example: Internet of Things Technology and Applications" />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="code" label="Course Code">
                <Input placeholder="For example: CS101" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="semester" label="Semester">
                <Input placeholder="For example: Spring 2026" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="Course Description">
            <Input.TextArea placeholder="Course overview and teaching goals..." rows={3} autoSize={{ minRows: 3, maxRows: 6 }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};
