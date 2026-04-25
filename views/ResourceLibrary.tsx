import React, { useEffect, useState } from 'react';
import {
  dashboardApi,
  courseApi,
  materialApi,
  semanticApi,
  CourseChapterInfo,
  CourseInfo,
  CourseStatusSummaryInfo,
  CourseTeachingMaterialGroupInfo,
  KnowledgeNodeInfo,
  TeachingMaterialViewInfo,
  SemanticHit,
} from '../services/api';
import { downloadBlobFile } from '../services/download';
import { useAuth } from '../contexts/AuthContext';
import { View, ViewChangeHandler } from '../types';
import { TeachingMaterialPreview } from '../components/TeachingMaterialPreview';
import {
  Input, Button, Collapse, Modal, Form, Tag, Progress,
  Space, Spin, Empty, Typography, Avatar, Row, Col, Card,
  Drawer, Divider, List, message
} from 'antd';
import {
  SearchOutlined, PlusOutlined, InteractionOutlined,
  ShareAltOutlined, RobotOutlined, CodeOutlined, HeartOutlined,
  FileTextOutlined, DownloadOutlined, EditOutlined, BookOutlined,
  CompassOutlined
} from '@ant-design/icons';

const { Text, Title } = Typography;

interface ResourceLibraryProps {
  onChangeView?: ViewChangeHandler;
  onMaterialOpen?: (materialId: number, courseId: number) => void;
  onCourseOpen?: (courseId: number) => void;
}

export const ResourceLibrary: React.FC<ResourceLibraryProps> = ({ onChangeView, onMaterialOpen, onCourseOpen }) => {
  const { roleUi, currentUser } = useAuth();
  const canManageMaterials = !!roleUi?.capabilities?.canUploadResource;

  const [courses, setCourses] = useState<CourseInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedKeys, setExpandedKeys] = useState<string[]>([]);
  const [knowledgePointsMap, setKnowledgePointsMap] = useState<Record<number, KnowledgeNodeInfo[]>>({});
  const [kpLoadingMap, setKpLoadingMap] = useState<Record<number, boolean>>({});
  const [chaptersMap, setChaptersMap] = useState<Record<number, CourseChapterInfo[]>>({});
  const [statusSummaryMap, setStatusSummaryMap] = useState<Record<number, CourseStatusSummaryInfo>>({});
  const [chapterLoadingMap, setChapterLoadingMap] = useState<Record<number, boolean>>({});
  const [materialGroupsMap, setMaterialGroupsMap] = useState<Record<number, CourseTeachingMaterialGroupInfo[]>>({});
  const [materialLoadingMap, setMaterialLoadingMap] = useState<Record<number, boolean>>({});
  const [searchKeyword, setSearchKeyword] = useState('');
  const [semanticQuery, setSemanticQuery] = useState('');
  const [semanticResults, setSemanticResults] = useState<SemanticHit[]>([]);
  const [semanticLoading, setSemanticLoading] = useState(false);

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
        const data = currentUser?.id && !canManageMaterials
          ? await courseApi.getStudentCourses(currentUser.id)
          : await dashboardApi.getCourses();
        setCourses(data);
      } catch {
        // Keep an empty state when the backend is unavailable.
      } finally {
        setLoading(false);
      }
    };
    loadCourses();
  }, [canManageMaterials, currentUser?.id]);

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

  const loadCourseChapters = async (courseId: number) => {
    if (chaptersMap[courseId] || chapterLoadingMap[courseId]) {
      return;
    }
    setChapterLoadingMap(previous => ({ ...previous, [courseId]: true }));
    try {
      const [chapters, summary] = await Promise.all([
        courseApi.getChapters(courseId),
        courseApi.getStatusSummary(courseId),
      ]);
      setChaptersMap(previous => ({ ...previous, [courseId]: chapters }));
      setStatusSummaryMap(previous => ({ ...previous, [courseId]: summary }));
    } catch {
      setChaptersMap(previous => ({ ...previous, [courseId]: [] }));
    } finally {
      setChapterLoadingMap(previous => ({ ...previous, [courseId]: false }));
    }
  };

  const loadCourseMaterials = async (courseId: number) => {
    if (materialGroupsMap[courseId] || materialLoadingMap[courseId]) {
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
      onCourseOpen?.(courseId);
      await Promise.all([
        loadKnowledgePoints(courseId),
        loadCourseChapters(courseId),
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

  const handlePreviewMaterial = async (materialId: number, taskId: number, sourceFileName: string, courseId?: number) => {
    if (courseId !== undefined) {
      onMaterialOpen?.(materialId, courseId);
    }
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

  const handleSemanticSearch = async () => {
    const query = semanticQuery.trim();
    if (!query) {
      setSemanticResults([]);
      return;
    }
    setSemanticLoading(true);
    try {
      const hits = await semanticApi.search({
        scope: 'knowledge_points',
        text: query,
        topK: 8,
      });
      setSemanticResults(hits);
    } catch {
      message.error('Semantic search failed. Ensure vector search backend is enabled.');
      setSemanticResults([]);
    } finally {
      setSemanticLoading(false);
    }
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

  const sortChapters = (chapters: CourseChapterInfo[]) => [...chapters].sort((first, second) =>
    (first.sortOrder || 0) - (second.sortOrder || 0) || first.id - second.id
  );

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
    const groups = materialGroupsMap[courseId] || [];
    const chapters = sortChapters(chaptersMap[courseId] || []);
    const summary = statusSummaryMap[courseId];
    const visibleGroups = canManageMaterials
      ? groups
      : groups.filter(group => (group.versions || []).some(version => version.status === 'PUBLISHED'));
    const groupedByChapter = chapters.map(chapter => ({
      chapter,
      groups: visibleGroups.filter(group => group.chapterId === chapter.id),
    }));
    const unboundGroups = visibleGroups.filter(group => !group.chapterId);
    const sections = [
      ...groupedByChapter,
      ...(unboundGroups.length > 0 ? [{ chapter: null, groups: unboundGroups }] : []),
    ].filter(section => section.groups.length > 0);

    const renderMaterialGroup = (group: CourseTeachingMaterialGroupInfo) => {
      const visibleVersions = canManageMaterials
        ? (group.versions || [])
        : (group.versions || []).filter(version => version.status === 'PUBLISHED');
      const latestVersion = canManageMaterials
        ? visibleVersions.find(version => version.materialId === group.latestMaterialId) || visibleVersions[0]
        : visibleVersions[0];
      const historyVersions = visibleVersions.filter(
        version => version.materialId !== latestVersion?.materialId
      );
      const latestMaterialId = latestVersion?.materialId;

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
                <Tag color="blue">Latest v{latestVersion?.versionNo || group.latestVersionNo || '-'}</Tag>
                <Tag color={mapMaterialStatusColor(latestVersion?.status || group.latestStatus)}>{latestVersion?.status || group.latestStatus || 'UNKNOWN'}</Tag>
              </Space>
            </Space>

            <Space wrap>
              <Button
                size="small"
                onClick={() => latestMaterialId && handlePreviewMaterial(latestMaterialId, group.parseTaskId, group.sourceFileName, courseId)}
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
                style={{ display: canManageMaterials ? undefined : 'none' }}
              >
                Export Markdown
              </Button>
              <Button
                size="small"
                type="primary"
                icon={<EditOutlined />}
                onClick={() => handleContinueEditing(group.parseTaskId, latestMaterialId || undefined)}
                disabled={!latestMaterialId || !onChangeView}
                style={{ display: canManageMaterials ? undefined : 'none' }}
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
                        onClick={() => handlePreviewMaterial(version.materialId, group.parseTaskId, group.sourceFileName, courseId)}
                      >
                        View
                      </Button>,
                      <Button
                        key="export"
                        size="small"
                        onClick={() => downloadMarkdown(version.materialId)}
                        loading={exportingMaterialId === version.materialId}
                        style={{ display: canManageMaterials ? undefined : 'none' }}
                      >
                        Export
                      </Button>,
                      <Button
                        key="edit"
                        size="small"
                        type="primary"
                        onClick={() => handleContinueEditing(group.parseTaskId, version.materialId)}
                        disabled={!onChangeView}
                        style={{ display: canManageMaterials ? undefined : 'none' }}
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
    };

    return (
      <div style={{ marginTop: 24 }}>
        <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }} align="start" wrap>
          <Text strong style={{ fontSize: 14 }}>
            <FileTextOutlined style={{ color: '#1677ff', marginRight: 8 }} />
            Teaching Materials
          </Text>
          {summary && (
            <Space wrap>
              <Tag color="blue">Chapters {summary.chapterCount}</Tag>
              <Tag color="cyan">Parse Tasks {summary.parseTaskCount}</Tag>
              <Tag color="green">Published {summary.publishedMaterialCount}</Tag>
              <Tag color="orange">Drafts {summary.draftMaterialCount}</Tag>
              <Tag color="purple">Knowledge {summary.knowledgePointCount}</Tag>
            </Space>
          )}
        </Space>

        {materialLoadingMap[courseId] || chapterLoadingMap[courseId] ? (
          <div style={{ textAlign: 'center', padding: '24px 0' }}><Spin /></div>
        ) : visibleGroups.length > 0 ? (
          <Space direction="vertical" style={{ width: '100%' }} size={16}>
            {sections.map(section => (
              <Card
                key={section.chapter ? `chapter-${section.chapter.id}` : 'chapter-unbound'}
                size="small"
                bordered
                title={section.chapter ? section.chapter.title : 'Unassigned Materials'}
                extra={<Tag>{section.groups.length} item(s)</Tag>}
                style={{ borderColor: '#f0f0f0' }}
              >
                <Space direction="vertical" style={{ width: '100%' }} size={12}>
                  {section.groups.map(renderMaterialGroup)}
                </Space>
              </Card>
            ))}
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
              <Input.Search
                placeholder="Semantic search (knowledge points)..."
                prefix={<CompassOutlined />}
                value={semanticQuery}
                onChange={event => setSemanticQuery(event.target.value)}
                onSearch={handleSemanticSearch}
                loading={semanticLoading}
                style={{ width: 260, borderRadius: 8 }}
                allowClear
                enterButton
              />
              <Input
                placeholder="Search courses..."
                prefix={<SearchOutlined />}
                value={searchKeyword}
                onChange={event => setSearchKeyword(event.target.value)}
                style={{ width: 200, borderRadius: 8 }}
                allowClear
              />
              {canManageMaterials && (
                <Button type="primary" icon={<PlusOutlined />} onClick={() => setShowNewCourse(true)}>
                  New Course
                </Button>
              )}
            </Space>
          </Col>
        </Row>

        {semanticResults.length > 0 && (
          <Card
            size="small"
            title={
              <Space>
                <CompassOutlined style={{ color: '#1677ff' }} />
                <Text strong>Semantic Search Results</Text>
              </Space>
            }
            style={{ borderRadius: 12, border: '1px solid #e2e8f0' }}
          >
            <List
              size="small"
              dataSource={semanticResults}
              renderItem={item => (
                <List.Item>
                  <List.Item.Meta
                    title={
                      <Space>
                        <Text strong>{item.title || 'Untitled'}</Text>
                        <Tag color="blue">{(item.score ?? 0).toFixed(3)}</Tag>
                      </Space>
                    }
                    description={
                      <Text type="secondary" style={{ fontSize: 13 }}>
                        {item.snippet || '-'}
                      </Text>
                    }
                  />
                </List.Item>
              )}
            />
          </Card>
        )}

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
          canManageMaterials ? (
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
          ) : null
        }
      >
        {previewLoading ? (
          <div style={{ textAlign: 'center', padding: '80px 0' }}>
            <Spin />
          </div>
        ) : previewMaterial ? (
          <TeachingMaterialPreview material={previewMaterial} sourceFileName={previewSourceFileName} />
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
