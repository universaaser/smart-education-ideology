import React, { useState, useEffect, useRef } from 'react';
import {
  uploadApi,
  materialApi,
  dashboardApi,
  UploadTaskInfo,
  TeachingMaterialDraftInfo,
  TeachingMaterialSaveRequest,
  TeachingQuestionInfo,
  TeachingTraceItemInfo,
  MaterialVersionItemInfo,
  TeachingMaterialTraceInfo,
  CourseInfo
} from '../services/api';
import {
  Upload, Button, Progress, Card, Typography, Space,
  Alert, Spin, message, Avatar, Input, Divider, Tag, Select
} from 'antd';
import {
  InboxOutlined, PlayCircleOutlined, CheckCircleOutlined,
  CloseCircleOutlined, FileTextOutlined, ReloadOutlined,
  FileWordOutlined, FilePdfOutlined, FileExcelOutlined, FilePptOutlined,
  RobotOutlined, FileUnknownOutlined, DownloadOutlined
} from '@ant-design/icons';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;
const { Dragger } = Upload;
const { Option } = Select;

interface ResourceUploadProps {
  userId?: number;
}

type UploadStatus = 'idle' | 'uploading' | 'parsing' | 'completed' | 'failed';

export const ResourceUpload: React.FC<ResourceUploadProps> = ({ userId = 1 }) => {
  const [file, setFile] = useState<File | null>(null);
  const [status, setStatus] = useState<UploadStatus>('idle');
  const [progress, setProgress] = useState(0);
  const [currentStep, setCurrentStep] = useState('');
  const [taskResult, setTaskResult] = useState<UploadTaskInfo | null>(null);
  const [error, setError] = useState('');
  const [editorDraft, setEditorDraft] = useState<TeachingMaterialDraftInfo | null>(null);
  const [editorLoading, setEditorLoading] = useState(false);
  const [savingDraft, setSavingDraft] = useState(false);
  const [publishingVersion, setPublishingVersion] = useState(false);
  const [materialVersions, setMaterialVersions] = useState<MaterialVersionItemInfo[]>([]);
  const [versionsLoading, setVersionsLoading] = useState(false);
  const [exportingMarkdown, setExportingMarkdown] = useState(false);
  const [courses, setCourses] = useState<CourseInfo[]>([]);
  const [coursesLoading, setCoursesLoading] = useState(false);
  const [selectedCourseId, setSelectedCourseId] = useState<number | undefined>(undefined);
  const [traceRows, setTraceRows] = useState<TeachingMaterialTraceInfo[]>([]);
  const [traceTotal, setTraceTotal] = useState(0);
  const [traceLoading, setTraceLoading] = useState(false);
  const [traceKnowledgeFilter, setTraceKnowledgeFilter] = useState('');
  const [traceIdeologyFilter, setTraceIdeologyFilter] = useState('');
  const [traceUseCourseFilter, setTraceUseCourseFilter] = useState(false);
  const [rollbackTip, setRollbackTip] = useState('');
  const pollTimerRef = useRef<number | null>(null);
  const isUnmountedRef = useRef(false);

  useEffect(() => {
    return () => {
      /**
       * 组件卸载时清理轮询定时器，避免卸载后继续 setState。
       * 这是上传页离开后仍有异步回调触发的主要风险点。
       */
      isUnmountedRef.current = true;
      if (pollTimerRef.current !== null) {
        window.clearTimeout(pollTimerRef.current);
        pollTimerRef.current = null;
      }
    };
  }, []);

  useEffect(() => {
    const loadCourses = async () => {
      setCoursesLoading(true);
      try {
        const courseList = await dashboardApi.getCourses();
        if (!isUnmountedRef.current) {
          setCourses(courseList);
        }
      } catch {
        if (!isUnmountedRef.current) {
          message.error('Failed to load courses');
        }
      } finally {
        if (!isUnmountedRef.current) {
          setCoursesLoading(false);
        }
      }
    };
    loadCourses();
  }, []);

  const loadMaterialVersions = async (taskId: number) => {
    setVersionsLoading(true);
    try {
      const versions = await uploadApi.getTaskMaterialVersions(taskId);
      if (!isUnmountedRef.current) {
        setMaterialVersions(versions);
      }
    } catch {
      if (!isUnmountedRef.current) {
        message.error('Failed to load material versions');
      }
    } finally {
      if (!isUnmountedRef.current) {
        setVersionsLoading(false);
      }
    }
  };

  const loadTraceRows = async (
    taskId: number,
    materialId?: number | null,
    page = 1,
  ) => {
    setTraceLoading(true);
    try {
      const courseIdFilter = traceUseCourseFilter && selectedCourseId ? selectedCourseId : undefined;
      const response = materialId
        ? await materialApi.getTraces(materialId, {
          knowledgePoint: traceKnowledgeFilter.trim() || undefined,
          ideologyElement: traceIdeologyFilter.trim() || undefined,
          page,
          size: 20,
        })
        : await uploadApi.getTaskTraces(taskId, {
          courseId: courseIdFilter,
          knowledgePoint: traceKnowledgeFilter.trim() || undefined,
          ideologyElement: traceIdeologyFilter.trim() || undefined,
          page,
          size: 20,
        });

      if (!isUnmountedRef.current) {
        setTraceRows(response.records || []);
        setTraceTotal(response.total || 0);
      }
    } catch {
      if (!isUnmountedRef.current) {
        message.error('Failed to load trace rows');
      }
    } finally {
      if (!isUnmountedRef.current) {
        setTraceLoading(false);
      }
    }
  };

  useEffect(() => {
    const loadEditorDraft = async () => {
      const taskId = taskResult?.taskId;
      if (!taskId || status !== 'completed') return;
      setEditorLoading(true);
      try {
        const draft = await uploadApi.getEditorDraft(taskId);
        if (!isUnmountedRef.current) {
          setEditorDraft(draft);
          if (draft.courseId) {
            setSelectedCourseId(draft.courseId);
          }
        }
        await loadMaterialVersions(taskId);
        await loadTraceRows(taskId, draft.materialId);
      } catch {
        if (!isUnmountedRef.current) {
          message.error('Failed to load editor draft');
        }
      } finally {
        if (!isUnmountedRef.current) {
          setEditorLoading(false);
        }
      }
    };
    loadEditorDraft();
  }, [taskResult?.taskId, status]);

  const pollTaskStatus = async (taskId: number) => {
    const poll = async () => {
      try {
        const taskInfo = await uploadApi.getTaskStatus(taskId);
        if (isUnmountedRef.current) return;

        setProgress(taskInfo.progress);
        setCurrentStep(taskInfo.currentStep);

        if (taskInfo.status === 'COMPLETED') {
          setStatus('completed');
          setTaskResult(taskInfo);
          return;
        }

        if (taskInfo.status === 'FAILED') {
          setStatus('failed');
          setError(taskInfo.errorMessage || '解析失败');
          return;
        }

        pollTimerRef.current = window.setTimeout(poll, 2000);
      } catch {
        if (isUnmountedRef.current) return;
        setStatus('failed');
        setError('获取任务状态失败');
      }
    };
    poll();
  };

  const handleUpload = async () => {
    if (!file) return;

    if (pollTimerRef.current !== null) {
      window.clearTimeout(pollTimerRef.current);
      pollTimerRef.current = null;
    }

    setStatus('uploading');
    setProgress(0);
    setError('');
    setCurrentStep('正在上传文件...');

    try {
      const result = await uploadApi.uploadFile(file, userId, selectedCourseId);
      setStatus('parsing');
      setProgress(10);
      setCurrentStep('文件已上传，正在解析...');
      pollTaskStatus(result.taskId);
    } catch (err: unknown) {
      setStatus('failed');
      setError(err instanceof Error ? err.message : '上传失败');
      message.error('上传失败');
    }
  };

  const handleReset = () => {
    if (pollTimerRef.current !== null) {
      window.clearTimeout(pollTimerRef.current);
      pollTimerRef.current = null;
    }
    setFile(null);
    setStatus('idle');
    setProgress(0);
    setCurrentStep('');
    setTaskResult(null);
    setError('');
    setEditorDraft(null);
    setEditorLoading(false);
    setSavingDraft(false);
    setPublishingVersion(false);
    setMaterialVersions([]);
    setVersionsLoading(false);
    setExportingMarkdown(false);
    setSelectedCourseId(undefined);
    setTraceRows([]);
    setTraceTotal(0);
    setTraceLoading(false);
    setTraceKnowledgeFilter('');
    setTraceIdeologyFilter('');
    setTraceUseCourseFilter(false);
    setRollbackTip('');
  };

  const updateEditorDraft = (updater: (draft: TeachingMaterialDraftInfo) => TeachingMaterialDraftInfo) => {
    setEditorDraft(prev => (prev ? updater(prev) : prev));
  };

  const updateCaseItem = (index: number, value: string) => {
    updateEditorDraft((draft) => {
      const cases = [...draft.cases];
      cases[index] = value;
      return { ...draft, cases };
    });
  };

  const addCaseItem = () => {
    updateEditorDraft((draft) => ({ ...draft, cases: [...draft.cases, ''] }));
  };

  const removeCaseItem = (index: number) => {
    updateEditorDraft((draft) => ({ ...draft, cases: draft.cases.filter((_, i) => i !== index) }));
  };

  const updateQuestion = (index: number, field: keyof TeachingQuestionInfo, value: string | string[]) => {
    updateEditorDraft((draft) => {
      const questions = [...draft.questions];
      const current = questions[index] || { stem: '', referenceAnswer: '', scoringPoints: [] };
      questions[index] = {
        ...current,
        [field]: value,
      };
      return { ...draft, questions };
    });
  };

  const addQuestion = () => {
    updateEditorDraft((draft) => ({
      ...draft,
      questions: [...draft.questions, { stem: '', referenceAnswer: '', scoringPoints: [''] }]
    }));
  };

  const removeQuestion = (index: number) => {
    updateEditorDraft((draft) => ({
      ...draft,
      questions: draft.questions.filter((_, i) => i !== index)
    }));
  };

  const updateScoringPoint = (questionIndex: number, pointIndex: number, value: string) => {
    updateEditorDraft((draft) => {
      const questions = [...draft.questions];
      const target = questions[questionIndex] || { stem: '', referenceAnswer: '', scoringPoints: [] };
      const scoringPoints = [...(target.scoringPoints || [])];
      scoringPoints[pointIndex] = value;
      questions[questionIndex] = { ...target, scoringPoints };
      return { ...draft, questions };
    });
  };

  const addScoringPoint = (questionIndex: number) => {
    updateEditorDraft((draft) => {
      const questions = [...draft.questions];
      const target = questions[questionIndex] || { stem: '', referenceAnswer: '', scoringPoints: [] };
      questions[questionIndex] = { ...target, scoringPoints: [...(target.scoringPoints || []), ''] };
      return { ...draft, questions };
    });
  };

  const removeScoringPoint = (questionIndex: number, pointIndex: number) => {
    updateEditorDraft((draft) => {
      const questions = [...draft.questions];
      const target = questions[questionIndex] || { stem: '', referenceAnswer: '', scoringPoints: [] };
      questions[questionIndex] = {
        ...target,
        scoringPoints: (target.scoringPoints || []).filter((_, i) => i !== pointIndex)
      };
      return { ...draft, questions };
    });
  };

  const buildSavePayload = (draft: TeachingMaterialDraftInfo): TeachingMaterialSaveRequest => ({
    title: draft.title || '',
    lectureNotes: draft.lectureNotes || '',
    cases: (draft.cases || []).map(item => item.trim()).filter(Boolean),
    questions: (draft.questions || []).map((question) => ({
      stem: question.stem?.trim() || '',
      referenceAnswer: question.referenceAnswer?.trim() || '',
      scoringPoints: (question.scoringPoints || []).map(point => point.trim()).filter(Boolean),
    })),
  });

  const handleSaveDraft = async () => {
    if (!taskResult?.taskId || !editorDraft) return;
    setSavingDraft(true);
    try {
      const saved = await uploadApi.saveEditorDraft(taskResult.taskId, buildSavePayload(editorDraft));
      setEditorDraft(saved);
      await loadTraceRows(taskResult.taskId, saved.materialId);
      message.success('Draft saved');
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to save draft');
    } finally {
      setSavingDraft(false);
    }
  };

  const handlePublishVersion = async () => {
    if (!taskResult?.taskId || !editorDraft) return;
    setPublishingVersion(true);
    try {
      const saved = await uploadApi.savePublishedMaterial(taskResult.taskId, buildSavePayload(editorDraft));
      setEditorDraft(prev => prev ? {
        ...prev,
        materialId: saved.materialId,
        courseId: saved.courseId,
        versionNo: saved.versionNo,
        status: saved.status,
        updatedAt: saved.updatedAt,
        traceItems: saved.traceItems,
      } : prev);
      if (saved.courseId) {
        setSelectedCourseId(saved.courseId);
      }
      await loadMaterialVersions(taskResult.taskId);
      await loadTraceRows(taskResult.taskId, saved.materialId);
      setRollbackTip('');
      message.success(`Version ${saved.versionNo} saved`);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to save version');
    } finally {
      setPublishingVersion(false);
    }
  };

  const handleViewVersion = async (materialId: number) => {
    setEditorLoading(true);
    try {
      const material = await materialApi.getById(materialId);
      setEditorDraft({
        materialId: material.materialId,
        parseTaskId: material.parseTaskId,
        userId: material.userId,
        courseId: material.courseId,
        title: material.title || '',
        lectureNotes: material.lectureNotes || '',
        cases: material.cases || [],
        questions: material.questions || [],
        traceItems: material.traceItems || [],
        schemaVersion: material.schemaVersion || 'v1',
        versionNo: material.versionNo || 0,
        status: material.status || 'DRAFT',
        updatedAt: material.updatedAt,
      });
      if (material.courseId) {
        setSelectedCourseId(material.courseId);
      }
      await loadTraceRows(material.parseTaskId, material.materialId);
      setRollbackTip('');
      message.success(`Loaded version ${material.versionNo}`);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to load material version');
    } finally {
      setEditorLoading(false);
    }
  };

  const handleExportMarkdown = async () => {
    const materialId = editorDraft?.materialId;
    if (!materialId) {
      message.warning('Please save a material version before export');
      return;
    }
    setExportingMarkdown(true);
    try {
      const blob = await materialApi.exportMarkdown(materialId);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `teaching-material-${materialId}.md`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
      message.success('Markdown exported');
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to export markdown');
    } finally {
      setExportingMarkdown(false);
    }
  };

  const handleRollbackVersion = async (materialId: number, versionNo: number) => {
    if (!taskResult?.taskId) return;
    setEditorLoading(true);
    try {
      const rolledBack = await uploadApi.rollbackMaterialVersion(taskResult.taskId, materialId);
      setEditorDraft(rolledBack);
      if (rolledBack.courseId) {
        setSelectedCourseId(rolledBack.courseId);
      }
      await loadMaterialVersions(taskResult.taskId);
      await loadTraceRows(taskResult.taskId, rolledBack.materialId);
      const now = new Date().toLocaleString();
      setRollbackTip(`Rolled back from version v${versionNo} at ${now}. Unsaved until you click Save Draft or Save Version.`);
      message.success(`Rolled back from version v${versionNo}`);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to rollback version');
    } finally {
      setEditorLoading(false);
    }
  };

  const handleSearchTraces = async () => {
    if (!taskResult?.taskId) return;
    await loadTraceRows(taskResult.taskId, editorDraft?.materialId || null);
  };

  const formatFileSize = (bytes: number): string => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  const getFileIcon = (fileName: string) => {
    const ext = fileName.split('.').pop()?.toLowerCase();
    switch (ext) {
      case 'doc':
      case 'docx': return <FileWordOutlined style={{ color: '#1677ff' }} />;
      case 'pdf':  return <FilePdfOutlined style={{ color: '#cf1322' }} />;
      case 'xls':
      case 'xlsx': return <FileExcelOutlined style={{ color: '#389e0d' }} />;
      case 'ppt':
      case 'pptx': return <FilePptOutlined style={{ color: '#d4380d' }} />;
      default:     return <FileUnknownOutlined style={{ color: '#8c8c8c' }} />;
    }
  };

  const draggerProps = {
    name: 'file',
    multiple: false,
    fileList: [],
    beforeUpload: (file) => {
      const isAllowed = /\.(pdf|doc|docx|ppt|pptx|xls|xlsx)$/i.test(file.name);
      if (!isAllowed) {
        message.error(`${file.name} 格式不支持！`);
        return Upload.LIST_IGNORE;
      }
      setFile(file);
      setStatus('idle');
      setError('');
      setTaskResult(null);
      setMaterialVersions([]);
      setTraceRows([]);
      setTraceTotal(0);
      setRollbackTip('');
      return false; // 阻止自动上传
    },
    disabled: status !== 'idle'
  };

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: '32px' }}>
      <div style={{ maxWidth: 800, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: '32px' }}>
        
        {/* 头部 */}
        <div>
          <Title level={2} style={{ margin: 0, fontFamily: "'Lexend', sans-serif" }}>智能文档解析</Title>
          <Text type="secondary">上传教学文档，AI 自动提取知识点并挖掘思政元素</Text>
        </div>

        {/* 上传区 */}
        <Card bordered={false} style={{ borderRadius: 12 }}>
          <Space direction="vertical" size={6} style={{ width: '100%' }}>
            <Text strong>Course Binding (Optional)</Text>
            <Select
              allowClear
              loading={coursesLoading}
              placeholder="Select course for this upload task"
              value={selectedCourseId}
              onChange={(value) => setSelectedCourseId(value)}
              style={{ width: '100%' }}
            >
              {courses.map((course) => (
                <Option key={course.id} value={course.id}>
                  {course.name}
                </Option>
              ))}
            </Select>
            <Text type="secondary" style={{ fontSize: 12 }}>
              Selected course will be attached to parse task, material versions, and AI generation context.
            </Text>
          </Space>
        </Card>

        <Card bordered={false} bodyStyle={{ padding: status === 'idle' ? 32 : 16 }} style={{ borderRadius: 16 }}>
          {status === 'idle' && (
            <div style={{ marginBottom: file ? 24 : 0 }}>
              <Dragger {...draggerProps} style={{ padding: '32px 0', border: '2px dashed #e2e8f0', borderRadius: 16 }}>
                <p className="ant-upload-drag-icon">
                  <InboxOutlined style={{ color: '#1677ff', opacity: 0.8 }} />
                </p>
                <p className="ant-upload-text">拖拽文件到此处，或点击浏览</p>
                <p className="ant-upload-hint">支持 PDF、Word、PPT、Excel 格式，最大 50MB</p>
              </Dragger>
            </div>
          )}

          {file && status === 'idle' && (
            <Alert
              message={
                 <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
                   <Space size={16}>
                     <Avatar icon={getFileIcon(file.name)} style={{ backgroundColor: '#f0f5ff' }} size="large" />
                     <Space direction="vertical" size={2}>
                       <Text strong>{file.name}</Text>
                       <Text type="secondary" style={{ fontSize: 12 }}>{formatFileSize(file.size)}</Text>
                     </Space>
                   </Space>
                   <Button type="primary" icon={<PlayCircleOutlined />} onClick={handleUpload}>开始解析</Button>
                 </Space>
              }
              type="info"
              showIcon={false}
              style={{ borderRadius: 12, border: '1px solid #bae0ff', backgroundColor: '#e6f4ff' }}
            />
          )}

          {/* 解析中 */}
          {(status === 'uploading' || status === 'parsing') && (
            <div style={{ textAlign: 'center', padding: '40px 0' }}>
              <Spin size="large" style={{ marginBottom: 16 }} />
              <div style={{ marginBottom: 16 }}>
                <Text strong>{currentStep || '处理中...'}</Text>
                <br />
                <Text type="secondary" style={{ fontSize: 12 }}>{file?.name}</Text>
              </div>
              <Progress percent={progress} status="active" />
            </div>
          )}

          {/* 失败 */}
          {status === 'failed' && (
            <Alert
              message="解析失败"
              description={error}
              type="error"
              showIcon
              icon={<CloseCircleOutlined />}
              action={<Button icon={<ReloadOutlined />} onClick={handleReset} size="small" type="primary" danger ghost>重新上传</Button>}
              style={{ borderRadius: 12, padding: 24 }}
            />
          )}
        </Card>

        {/* 成功预览 */}
        {status === 'completed' && taskResult && (
          <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <Alert
              message={<Text strong>解析完成：{taskResult.fileName}</Text>}
              type="success"
              showIcon
              icon={<CheckCircleOutlined />}
              action={<Button type="default" size="small" icon={<ReloadOutlined />} onClick={handleReset}>上传新文件</Button>}
              style={{ borderRadius: 12, padding: '16px 20px' }}
            />

            {taskResult.parsedContent && (
              <Card 
                title={<Space><FileTextOutlined style={{ color: '#1677ff' }} /> 文档内容摘要</Space>}
                bordered={false}
                style={{ borderRadius: 12 }}
              >
                <Paragraph style={{ whiteSpace: 'pre-wrap', color: '#475569', fontSize: 14 }}>
                  {taskResult.parsedContent}
                </Paragraph>
              </Card>
            )}

            {taskResult.aiAnalysis && (
              <Card 
                title={<Space><RobotOutlined style={{ color: '#ef4444' }} /> 思政价值分析</Space>}
                bordered={false}
                style={{ borderRadius: 12 }}
              >
                <Paragraph style={{ whiteSpace: 'pre-wrap', color: '#475569', fontSize: 14 }}>
                  {taskResult.aiAnalysis}
                </Paragraph>
              </Card>
            )}
            <Card
              title={<Space><FileTextOutlined style={{ color: '#1677ff' }} /> Teaching Editor</Space>}
              bordered={false}
              style={{ borderRadius: 12 }}
              extra={
                <Space>
                  <Button
                    onClick={handleSaveDraft}
                    loading={savingDraft}
                    disabled={!editorDraft || editorLoading}
                  >
                    Save Draft
                  </Button>
                  <Button
                    type="primary"
                    onClick={handlePublishVersion}
                    loading={publishingVersion}
                    disabled={!editorDraft || editorLoading}
                  >
                    Save Version
                  </Button>
                  <Button
                    icon={<DownloadOutlined />}
                    onClick={handleExportMarkdown}
                    loading={exportingMarkdown}
                    disabled={!editorDraft || editorLoading}
                  >
                    Export Markdown
                  </Button>
                </Space>
              }
            >
              {editorLoading && (
                <div style={{ padding: 12 }}>
                  <Spin />
                </div>
              )}

              {!editorLoading && editorDraft && (
                <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                  <Card size="small" style={{ borderRadius: 8 }}>
                    <Space direction="vertical" size="small" style={{ width: '100%' }}>
                      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                        <Text strong>Version History</Text>
                        <Button
                          size="small"
                          onClick={() => taskResult?.taskId && loadMaterialVersions(taskResult.taskId)}
                          loading={versionsLoading}
                        >
                          Refresh
                        </Button>
                      </Space>
                      {materialVersions.length === 0 && (
                        <Text type="secondary">No saved versions yet.</Text>
                      )}
                      {materialVersions.map((version) => (
                        <Space
                          key={`version-${version.materialId}`}
                          style={{ width: '100%', justifyContent: 'space-between' }}
                        >
                          <Text type={version.materialId === editorDraft.materialId ? 'success' : undefined}>
                            v{version.versionNo} | {version.status} {version.isLatest === 1 ? '(latest)' : ''}
                          </Text>
                          <Space>
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              {version.updatedAt || '-'}
                            </Text>
                            <Button size="small" onClick={() => handleViewVersion(version.materialId)}>
                              View
                            </Button>
                            <Button
                              size="small"
                              disabled={version.materialId === editorDraft.materialId && editorDraft.status === 'DRAFT'}
                              onClick={() => handleRollbackVersion(version.materialId, version.versionNo)}
                            >
                              Rollback to Draft
                            </Button>
                          </Space>
                        </Space>
                      ))}
                    </Space>
                  </Card>

                  <Alert
                    type="info"
                    showIcon
                    message={`Version: ${editorDraft.versionNo || 0} | Status: ${editorDraft.status || 'DRAFT'} | Updated: ${editorDraft.updatedAt || '-'}`}
                  />
                  {rollbackTip && (
                    <Alert
                      type="warning"
                      showIcon
                      message={rollbackTip}
                    />
                  )}

                  <div>
                    <Text strong>Title</Text>
                    <Input
                      value={editorDraft.title}
                      onChange={(event) => updateEditorDraft((draft) => ({ ...draft, title: event.target.value }))}
                      maxLength={300}
                    />
                  </div>

                  <div>
                    <Text strong>Lecture Notes</Text>
                    <TextArea
                      value={editorDraft.lectureNotes}
                      onChange={(event) => updateEditorDraft((draft) => ({ ...draft, lectureNotes: event.target.value }))}
                      autoSize={{ minRows: 6, maxRows: 14 }}
                      maxLength={10000}
                    />
                  </div>

                  <Divider style={{ margin: '8px 0' }} />
                  <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                    <Text strong>Cases</Text>
                    <Button size="small" onClick={addCaseItem}>Add Case</Button>
                  </Space>
                  <Space direction="vertical" style={{ width: '100%' }}>
                    {(editorDraft.cases || []).map((item, index) => (
                      <Space key={`case-${index}`} align="start" style={{ width: '100%' }}>
                        <TextArea
                          value={item}
                          onChange={(event) => updateCaseItem(index, event.target.value)}
                          autoSize={{ minRows: 2, maxRows: 6 }}
                          maxLength={2000}
                        />
                        <Button danger onClick={() => removeCaseItem(index)}>Remove</Button>
                      </Space>
                    ))}
                  </Space>

                  <Divider style={{ margin: '8px 0' }} />
                  <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                    <Text strong>Questions</Text>
                    <Button size="small" onClick={addQuestion}>Add Question</Button>
                  </Space>
                  <Space direction="vertical" style={{ width: '100%' }}>
                    {(editorDraft.questions || []).map((question, index) => (
                      <Card key={`question-${index}`} size="small" style={{ borderRadius: 8 }}>
                        <Space direction="vertical" style={{ width: '100%' }}>
                          <Input
                            placeholder="Question stem"
                            value={question.stem}
                            onChange={(event) => updateQuestion(index, 'stem', event.target.value)}
                            maxLength={2000}
                          />
                          <TextArea
                            placeholder="Reference answer"
                            value={question.referenceAnswer}
                            onChange={(event) => updateQuestion(index, 'referenceAnswer', event.target.value)}
                            autoSize={{ minRows: 2, maxRows: 6 }}
                            maxLength={3000}
                          />
                          <Space direction="vertical" style={{ width: '100%' }}>
                            <Text type="secondary">Scoring Points</Text>
                            {(question.scoringPoints || []).map((point, pointIndex) => (
                              <Space key={`question-${index}-point-${pointIndex}`} style={{ width: '100%' }}>
                                <Input
                                  value={point}
                                  onChange={(event) => updateScoringPoint(index, pointIndex, event.target.value)}
                                  maxLength={2000}
                                />
                                <Button danger onClick={() => removeScoringPoint(index, pointIndex)}>Remove</Button>
                              </Space>
                            ))}
                            <Button size="small" onClick={() => addScoringPoint(index)}>Add Scoring Point</Button>
                          </Space>
                          <Button danger onClick={() => removeQuestion(index)}>Remove Question</Button>
                        </Space>
                      </Card>
                    ))}
                  </Space>

                  <Divider style={{ margin: '8px 0' }} />
                  <Text strong>Trace Summary</Text>
                  <Space style={{ width: '100%' }} wrap>
                    <Input
                      placeholder="Knowledge point keyword"
                      value={traceKnowledgeFilter}
                      onChange={(event) => setTraceKnowledgeFilter(event.target.value)}
                      style={{ minWidth: 220 }}
                    />
                    <Input
                      placeholder="Ideology element keyword"
                      value={traceIdeologyFilter}
                      onChange={(event) => setTraceIdeologyFilter(event.target.value)}
                      style={{ minWidth: 220 }}
                    />
                    <Button
                      type={traceUseCourseFilter ? 'primary' : 'default'}
                      disabled={!selectedCourseId}
                      onClick={() => setTraceUseCourseFilter((previous) => !previous)}
                    >
                      Current Course
                    </Button>
                    <Button onClick={handleSearchTraces} loading={traceLoading}>
                      Search
                    </Button>
                  </Space>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    Current task filter is always applied. Total records: {traceTotal}
                  </Text>
                  <Space direction="vertical" style={{ width: '100%' }}>
                    {traceRows.length > 0 && traceRows.map((trace, index) => (
                      <Card key={`trace-row-${trace.id || index}`} size="small" style={{ borderRadius: 8 }}>
                        <Space direction="vertical" size="small">
                          <Space wrap>
                            <Tag color="blue">{trace.knowledgePointName || 'Unknown Point'}</Tag>
                            <Tag color="red">{trace.ideologyElement || 'Unknown Element'}</Tag>
                            {trace.courseId && <Tag color="gold">Course {trace.courseId}</Tag>}
                          </Space>
                          <Text type="secondary">{trace.evidenceSnippet || '-'}</Text>
                          <Text style={{ fontSize: 12 }}>{trace.matchReason || '-'}</Text>
                        </Space>
                      </Card>
                    ))}
                    {traceRows.length === 0 && (editorDraft.traceItems || []).map((trace: TeachingTraceItemInfo, index) => (
                      <Card key={`trace-fallback-${index}`} size="small" style={{ borderRadius: 8 }}>
                        <Space direction="vertical" size="small">
                          <Space>
                            <Tag color="blue">{trace.knowledgePointName || 'Unknown Point'}</Tag>
                            <Tag color="red">{trace.ideologyElement || 'Unknown Element'}</Tag>
                          </Space>
                          <Text type="secondary">{trace.evidenceSnippet || '-'}</Text>
                          <Text style={{ fontSize: 12 }}>{trace.matchReason || '-'}</Text>
                        </Space>
                      </Card>
                    ))}
                    {traceRows.length === 0 && (editorDraft.traceItems || []).length === 0 && (
                      <Text type="secondary">No trace records.</Text>
                    )}
                  </Space>
                </Space>
              )}
            </Card>
          </Space>
        )}
      </div>
    </div>
  );
};
