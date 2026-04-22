import React, { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Avatar,
  Button,
  Card,
  message,
  Progress,
  Select,
  Space,
  Spin,
  Typography,
  Upload,
} from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  FileExcelOutlined,
  FilePdfOutlined,
  FilePptOutlined,
  FileTextOutlined,
  FileUnknownOutlined,
  FileWordOutlined,
  InboxOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  RobotOutlined,
} from '@ant-design/icons';
import {
  chatApi,
  CourseInfo,
  dashboardApi,
  MaterialVersionItemInfo,
  materialApi,
  SelectionExplainHistoryInfo,
  SelectionExplainResponse,
  TeachingMaterialDraftInfo,
  TeachingMaterialSaveRequest,
  TeachingMaterialTraceInfo,
  TeachingMaterialViewInfo,
  TeachingQuestionInfo,
  UploadTaskInfo,
  uploadApi,
} from '../services/api';
import { TeachingMaterialEditorCard } from '../components/TeachingMaterialEditorCard';
import { downloadBlobFile } from '../services/download';
import { ResourceUploadTarget } from '../types';

const { Title, Text, Paragraph } = Typography;
const { Dragger } = Upload;
const { Option } = Select;

interface ResourceUploadProps {
  userId?: number;
  resourceUploadTarget?: ResourceUploadTarget;
}

type UploadStatus = 'idle' | 'uploading' | 'parsing' | 'completed' | 'failed';

export const ResourceUpload: React.FC<ResourceUploadProps> = ({
  userId,
  resourceUploadTarget,
}) => {
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
  const [selectionExplainLoading, setSelectionExplainLoading] = useState(false);
  const [selectionExplainResult, setSelectionExplainResult] = useState<SelectionExplainResponse | null>(null);
  const [selectionExplainHistory, setSelectionExplainHistory] = useState<SelectionExplainHistoryInfo[]>([]);
  const [selectionHistoryLoading, setSelectionHistoryLoading] = useState(false);
  const [selectedLectureText, setSelectedLectureText] = useState('');
  const [openContextWarning, setOpenContextWarning] = useState('');
  const pollTimerRef = useRef<number | null>(null);
  const isUnmountedRef = useRef(false);
  const openedExternalTargetKeyRef = useRef('');
  const dismissedExternalTargetKeyRef = useRef('');
  const resourceUploadTargetKey = resourceUploadTarget
    ? `${resourceUploadTarget.taskId}:${resourceUploadTarget.materialId ?? 'latest'}`
    : '';
  const hasActiveUser = userId !== undefined && userId !== null;

  useEffect(() => {
    return () => {
      /**
       * 页面卸载时必须清理轮询，避免任务轮询在视图切走后继续回写状态。
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

  const buildDraftFromMaterial = (material: TeachingMaterialViewInfo): TeachingMaterialDraftInfo => ({
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

  const resetTransientPanels = () => {
    setTraceRows([]);
    setTraceTotal(0);
    setTraceLoading(false);
    setTraceKnowledgeFilter('');
    setTraceIdeologyFilter('');
    setTraceUseCourseFilter(false);
    setRollbackTip('');
    setSelectionExplainLoading(false);
    setSelectionExplainResult(null);
    setSelectionExplainHistory([]);
    setSelectionHistoryLoading(false);
    setSelectedLectureText('');
  };

  const resetWorkingState = (options?: { preserveWarning?: boolean }) => {
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
    resetTransientPanels();
    if (!options?.preserveWarning) {
      setOpenContextWarning('');
    }
  };

  const loadMaterialVersions = async (taskId: number): Promise<MaterialVersionItemInfo[]> => {
    setVersionsLoading(true);
    try {
      const versions = await uploadApi.getTaskMaterialVersions(taskId);
      if (!isUnmountedRef.current) {
        setMaterialVersions(versions);
      }
      return versions;
    } catch {
      if (!isUnmountedRef.current) {
        setMaterialVersions([]);
        message.error('Failed to load material versions');
      }
      return [];
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

  const loadSelectionExplainHistory = async (
    materialId?: number | null,
    courseId?: number | null,
  ) => {
    if (!hasActiveUser) {
      if (!isUnmountedRef.current) {
        setSelectionExplainHistory([]);
        setSelectionHistoryLoading(false);
      }
      return;
    }
    setSelectionHistoryLoading(true);
    try {
      const response = await chatApi.getSelectionExplainHistory({
        userId,
        materialId: materialId ?? undefined,
        courseId: materialId ? undefined : courseId ?? undefined,
        page: 1,
        size: 10,
      });
      if (!isUnmountedRef.current) {
        setSelectionExplainHistory(response.records || []);
      }
    } catch {
      if (!isUnmountedRef.current) {
        message.error('Failed to load selection explanation history');
      }
    } finally {
      if (!isUnmountedRef.current) {
        setSelectionHistoryLoading(false);
      }
    }
  };

  const refreshEditorPanels = async (
    taskId: number,
    materialId?: number | null,
    courseId?: number | null,
  ) => {
    await Promise.all([
      loadTraceRows(taskId, materialId),
      loadSelectionExplainHistory(materialId, courseId),
    ]);
  };

  const loadMaterialIntoEditor = async (
    materialId: number,
    options?: { successMessage?: boolean },
  ): Promise<TeachingMaterialDraftInfo> => {
    const material = await materialApi.getById(materialId);
    const draft = buildDraftFromMaterial(material);
    if (!isUnmountedRef.current) {
      setEditorDraft(draft);
      setSelectedCourseId(material.courseId ?? undefined);
      setRollbackTip('');
    }
    await refreshEditorPanels(material.parseTaskId, material.materialId, material.courseId);
    if (options?.successMessage && !isUnmountedRef.current) {
      message.success(`Loaded version ${material.versionNo}`);
    }
    return draft;
  };

  const loadTaskIntoEditor = async (
    taskInfo: UploadTaskInfo,
    preferredMaterialId?: number,
  ) => {
    setEditorLoading(true);
    try {
      const draft = await uploadApi.getEditorDraft(taskInfo.taskId);
      if (isUnmountedRef.current) {
        return;
      }

      setFile(null);
      setStatus('completed');
      setProgress(taskInfo.progress || 100);
      setCurrentStep(taskInfo.currentStep || '');
      setTaskResult(taskInfo);
      setError('');
      setSelectionExplainResult(null);
      setSelectedLectureText('');
      setRollbackTip('');
      setTraceRows([]);
      setTraceTotal(0);
      setEditorDraft(draft);
      setSelectedCourseId(draft.courseId ?? taskInfo.courseId ?? undefined);

      let activeDraft = draft;
      const versions = await loadMaterialVersions(taskInfo.taskId);
      if (preferredMaterialId !== undefined) {
        const exists = versions.some((version) => version.materialId === preferredMaterialId);
        if (!exists) {
          throw new Error('Requested material version was not found for this task');
        }
        activeDraft = await loadMaterialIntoEditor(preferredMaterialId);
      } else {
        await refreshEditorPanels(taskInfo.taskId, draft.materialId, draft.courseId);
      }

      if (!isUnmountedRef.current) {
        setEditorDraft(activeDraft);
      }
    } finally {
      if (!isUnmountedRef.current) {
        setEditorLoading(false);
      }
    }
  };

  useEffect(() => {
    if (!resourceUploadTargetKey) {
      dismissedExternalTargetKeyRef.current = '';
      if (openedExternalTargetKeyRef.current) {
        openedExternalTargetKeyRef.current = '';
        resetWorkingState();
      }
      return;
    }

    if (dismissedExternalTargetKeyRef.current === resourceUploadTargetKey) {
      return;
    }

    if (openedExternalTargetKeyRef.current === resourceUploadTargetKey) {
      return;
    }

    let cancelled = false;

    const openExistingTask = async () => {
      setOpenContextWarning('');
      try {
        const taskInfo = await uploadApi.getTaskStatus(resourceUploadTarget.taskId);
        if (cancelled || isUnmountedRef.current) {
          return;
        }
        if (taskInfo.status !== 'COMPLETED') {
          throw new Error(`Task ${resourceUploadTarget.taskId} is not ready for editing`);
        }
        await loadTaskIntoEditor(taskInfo, resourceUploadTarget.materialId);
        if (cancelled || isUnmountedRef.current) {
          return;
        }
        openedExternalTargetKeyRef.current = resourceUploadTargetKey;
        dismissedExternalTargetKeyRef.current = '';
      } catch (error: unknown) {
        if (cancelled || isUnmountedRef.current) {
          return;
        }
        openedExternalTargetKeyRef.current = '';
        dismissedExternalTargetKeyRef.current = resourceUploadTargetKey;
        resetWorkingState({ preserveWarning: true });
        setOpenContextWarning(
          error instanceof Error
            ? error.message
            : 'Failed to open the requested teaching material',
        );
      }
    };

    openExistingTask();

    return () => {
      cancelled = true;
    };
  }, [resourceUploadTarget, resourceUploadTargetKey]);

  const pollTaskStatus = (taskId: number) => {
    const poll = async () => {
      try {
        const taskInfo = await uploadApi.getTaskStatus(taskId);
        if (isUnmountedRef.current) {
          return;
        }

        setProgress(taskInfo.progress);
        setCurrentStep(taskInfo.currentStep);

        if (taskInfo.status === 'COMPLETED') {
          await loadTaskIntoEditor(taskInfo);
          return;
        }

        if (taskInfo.status === 'FAILED') {
          setStatus('failed');
          setError(taskInfo.errorMessage || 'Failed to parse file');
          return;
        }

        pollTimerRef.current = window.setTimeout(poll, 2000);
      } catch {
        if (!isUnmountedRef.current) {
          setStatus('failed');
          setError('Failed to fetch task status');
        }
      }
    };

    poll();
  };

  const handleUpload = async () => {
    if (!file) {
      return;
    }
    const activeUserId = userId;
    if (activeUserId === undefined || activeUserId === null) {
      message.error('Current user is unavailable');
      return;
    }

    if (pollTimerRef.current !== null) {
      window.clearTimeout(pollTimerRef.current);
      pollTimerRef.current = null;
    }

    dismissedExternalTargetKeyRef.current = '';
    openedExternalTargetKeyRef.current = '';
    setStatus('uploading');
    setProgress(0);
    setError('');
    setOpenContextWarning('');
    setCurrentStep('Uploading file...');

    try {
      const result = await uploadApi.uploadFile(file, activeUserId, selectedCourseId);
      setStatus('parsing');
      setProgress(10);
      setCurrentStep('File uploaded, parsing in progress...');
      pollTaskStatus(result.taskId);
    } catch (err: unknown) {
      setStatus('failed');
      setError(err instanceof Error ? err.message : 'Upload failed');
      message.error('Upload failed');
    }
  };

  const handleReset = () => {
    dismissedExternalTargetKeyRef.current = resourceUploadTargetKey;
    openedExternalTargetKeyRef.current = '';
    resetWorkingState();
  };

  const updateEditorDraft = (updater: (draft: TeachingMaterialDraftInfo) => TeachingMaterialDraftInfo) => {
    setEditorDraft((previous) => (previous ? updater(previous) : previous));
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
    updateEditorDraft((draft) => ({
      ...draft,
      cases: draft.cases.filter((_, currentIndex) => currentIndex !== index),
    }));
  };

  const updateQuestion = (index: number, field: keyof TeachingQuestionInfo, value: string | string[]) => {
    updateEditorDraft((draft) => {
      const questions = [...draft.questions];
      const current = questions[index] || { stem: '', referenceAnswer: '', scoringPoints: [] };
      questions[index] = { ...current, [field]: value };
      return { ...draft, questions };
    });
  };

  const addQuestion = () => {
    updateEditorDraft((draft) => ({
      ...draft,
      questions: [...draft.questions, { stem: '', referenceAnswer: '', scoringPoints: [''] }],
    }));
  };

  const removeQuestion = (index: number) => {
    updateEditorDraft((draft) => ({
      ...draft,
      questions: draft.questions.filter((_, currentIndex) => currentIndex !== index),
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
      questions[questionIndex] = {
        ...target,
        scoringPoints: [...(target.scoringPoints || []), ''],
      };
      return { ...draft, questions };
    });
  };

  const removeScoringPoint = (questionIndex: number, pointIndex: number) => {
    updateEditorDraft((draft) => {
      const questions = [...draft.questions];
      const target = questions[questionIndex] || { stem: '', referenceAnswer: '', scoringPoints: [] };
      questions[questionIndex] = {
        ...target,
        scoringPoints: (target.scoringPoints || []).filter((_, currentIndex) => currentIndex !== pointIndex),
      };
      return { ...draft, questions };
    });
  };

  const buildSavePayload = (draft: TeachingMaterialDraftInfo): TeachingMaterialSaveRequest => ({
    title: draft.title || '',
    lectureNotes: draft.lectureNotes || '',
    cases: (draft.cases || []).map((item) => item.trim()).filter(Boolean),
    questions: (draft.questions || []).map((question) => ({
      stem: question.stem?.trim() || '',
      referenceAnswer: question.referenceAnswer?.trim() || '',
      scoringPoints: (question.scoringPoints || []).map((point) => point.trim()).filter(Boolean),
    })),
  });

  const handleSaveDraft = async () => {
    if (!taskResult?.taskId || !editorDraft) {
      return;
    }
    setSavingDraft(true);
    try {
      const saved = await uploadApi.saveEditorDraft(taskResult.taskId, buildSavePayload(editorDraft));
      setEditorDraft(saved);
      setSelectedCourseId(saved.courseId ?? taskResult.courseId ?? undefined);
      await refreshEditorPanels(taskResult.taskId, saved.materialId, saved.courseId);
      message.success('Draft saved');
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to save draft');
    } finally {
      setSavingDraft(false);
    }
  };

  const handlePublishVersion = async () => {
    if (!taskResult?.taskId || !editorDraft) {
      return;
    }
    setPublishingVersion(true);
    try {
      const saved = await uploadApi.savePublishedMaterial(taskResult.taskId, buildSavePayload(editorDraft));
      setEditorDraft((previous) =>
        previous
          ? {
              ...previous,
              materialId: saved.materialId,
              courseId: saved.courseId,
              versionNo: saved.versionNo,
              status: saved.status,
              updatedAt: saved.updatedAt,
              traceItems: saved.traceItems,
            }
          : previous,
      );
      setSelectedCourseId(saved.courseId ?? taskResult.courseId ?? undefined);
      await loadMaterialVersions(taskResult.taskId);
      await refreshEditorPanels(taskResult.taskId, saved.materialId, saved.courseId);
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
      await loadMaterialIntoEditor(materialId, { successMessage: true });
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
      downloadBlobFile(blob, `teaching-material-${materialId}.md`);
      message.success('Markdown exported');
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to export markdown');
    } finally {
      setExportingMarkdown(false);
    }
  };

  const handleRollbackVersion = async (materialId: number, versionNo: number) => {
    if (!taskResult?.taskId) {
      return;
    }
    setEditorLoading(true);
    try {
      const rolledBack = await uploadApi.rollbackMaterialVersion(taskResult.taskId, materialId);
      setEditorDraft(rolledBack);
      setSelectedCourseId(rolledBack.courseId ?? taskResult.courseId ?? undefined);
      await loadMaterialVersions(taskResult.taskId);
      await refreshEditorPanels(taskResult.taskId, rolledBack.materialId, rolledBack.courseId);
      const rollbackTime = new Date().toLocaleString();
      setRollbackTip(
        `Rolled back from version v${versionNo} at ${rollbackTime}. Unsaved until you click Save Draft or Save Version.`,
      );
      message.success(`Rolled back from version v${versionNo}`);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to rollback version');
    } finally {
      setEditorLoading(false);
    }
  };

  const handleSearchTraces = async () => {
    if (!taskResult?.taskId) {
      return;
    }
    await loadTraceRows(taskResult.taskId, editorDraft?.materialId || null);
  };

  const getSelectedText = () => {
    const selected = selectedLectureText.trim() || window.getSelection()?.toString().trim() || '';
    return selected.length > 4000 ? selected.slice(0, 4000) : selected;
  };

  const handleLectureSelection = (event: React.SyntheticEvent<HTMLTextAreaElement>) => {
    const target = event.currentTarget;
    const selected = target.value.slice(target.selectionStart, target.selectionEnd).trim();
    setSelectedLectureText(selected);
  };

  const handleExplainSelection = async () => {
    if (!taskResult?.taskId || !editorDraft) {
      return;
    }
    const activeUserId = userId;
    if (activeUserId === undefined || activeUserId === null) {
      message.error('Current user is unavailable');
      return;
    }
    const text = getSelectedText();
    if (!text) {
      message.warning('Select text in Lecture Notes before explaining');
      return;
    }
    setSelectionExplainLoading(true);
    try {
      const response = await chatApi.explainSelection({
        text,
        userId: activeUserId,
        courseId: editorDraft.courseId || selectedCourseId || null,
        materialId: editorDraft.materialId || null,
        parseTaskId: taskResult.taskId,
      });
      setSelectionExplainResult(response);
      await loadSelectionExplainHistory(editorDraft.materialId, editorDraft.courseId || selectedCourseId || null);
      message.success('Selection explained');
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to explain selection');
    } finally {
      setSelectionExplainLoading(false);
    }
  };

  const handleCopySelectionExplanation = async (answer?: string) => {
    const content = answer || selectionExplainResult?.answer || '';
    if (!content) {
      message.warning('No explanation to copy');
      return;
    }
    try {
      await navigator.clipboard.writeText(content);
      message.success('Explanation copied');
    } catch {
      message.error('Failed to copy explanation');
    }
  };

  const handleAppendSelectionExplanation = (answer?: string) => {
    const content = answer || selectionExplainResult?.answer || '';
    if (!content) {
      message.warning('No explanation to append');
      return;
    }
    updateEditorDraft((draft) => ({
      ...draft,
      lectureNotes: `${draft.lectureNotes || ''}\n\n## Selection Explanation\n\n${content}`.trim(),
    }));
    message.success('Explanation appended to lecture notes');
  };

  const formatFileSize = (bytes: number): string => {
    if (bytes === 0) {
      return '0 B';
    }
    const unit = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const index = Math.floor(Math.log(bytes) / Math.log(unit));
    return `${parseFloat((bytes / Math.pow(unit, index)).toFixed(1))} ${sizes[index]}`;
  };

  const getFileIcon = (fileName: string) => {
    const extension = fileName.split('.').pop()?.toLowerCase();
    switch (extension) {
      case 'doc':
      case 'docx':
        return <FileWordOutlined style={{ color: '#1677ff' }} />;
      case 'pdf':
        return <FilePdfOutlined style={{ color: '#cf1322' }} />;
      case 'xls':
      case 'xlsx':
        return <FileExcelOutlined style={{ color: '#389e0d' }} />;
      case 'ppt':
      case 'pptx':
        return <FilePptOutlined style={{ color: '#d4380d' }} />;
      default:
        return <FileUnknownOutlined style={{ color: '#8c8c8c' }} />;
    }
  };

  const draggerProps = {
    name: 'file',
    multiple: false,
    fileList: [],
    beforeUpload: (nextFile: File) => {
      const isAllowed = /\.(pdf|doc|docx|ppt|pptx|xls|xlsx)$/i.test(nextFile.name);
      if (!isAllowed) {
        message.error(`${nextFile.name} format is not supported`);
        return Upload.LIST_IGNORE;
      }
      dismissedExternalTargetKeyRef.current = resourceUploadTargetKey;
      openedExternalTargetKeyRef.current = '';
      setOpenContextWarning('');
      setFile(nextFile);
      setStatus('idle');
      setError('');
      setTaskResult(null);
      setEditorDraft(null);
      setMaterialVersions([]);
      resetTransientPanels();
      return false;
    },
    disabled: status !== 'idle',
  };

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: '32px' }}>
      <div
        style={{
          maxWidth: 880,
          margin: '0 auto',
          display: 'flex',
          flexDirection: 'column',
          gap: '24px',
        }}
      >
        <div>
          <Title level={2} style={{ margin: 0, fontFamily: "'Lexend', sans-serif" }}>
            Intelligent Material Parsing
          </Title>
          <Text type="secondary">
            Upload teaching documents, generate editable teaching materials, and manage versions in one place.
          </Text>
        </div>

        {openContextWarning && (
          <Alert
            type="warning"
            showIcon
            message={openContextWarning}
          />
        )}

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
              Selected course will be attached to the parse task, saved material versions, and trace queries.
            </Text>
          </Space>
        </Card>

        <Card bordered={false} bodyStyle={{ padding: status === 'idle' ? 32 : 16 }} style={{ borderRadius: 16 }}>
          {status === 'idle' && (
            <div style={{ marginBottom: file ? 24 : 0 }}>
              <Dragger
                {...draggerProps}
                style={{ padding: '32px 0', border: '2px dashed #e2e8f0', borderRadius: 16 }}
              >
                <p className="ant-upload-drag-icon">
                  <InboxOutlined style={{ color: '#1677ff', opacity: 0.8 }} />
                </p>
                <p className="ant-upload-text">Drag a file here, or click to browse</p>
                <p className="ant-upload-hint">Supports PDF, Word, PPT, and Excel files up to 50MB</p>
              </Dragger>
            </div>
          )}

          {file && status === 'idle' && (
            <Alert
              showIcon={false}
              type="info"
              style={{ borderRadius: 12, border: '1px solid #bae0ff', backgroundColor: '#e6f4ff' }}
              message={
                <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
                  <Space size={16}>
                    <Avatar icon={getFileIcon(file.name)} style={{ backgroundColor: '#f0f5ff' }} size="large" />
                    <Space direction="vertical" size={2}>
                      <Text strong>{file.name}</Text>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        {formatFileSize(file.size)}
                      </Text>
                    </Space>
                  </Space>
                  <Button type="primary" icon={<PlayCircleOutlined />} onClick={handleUpload}>
                    Start Parsing
                  </Button>
                </Space>
              }
            />
          )}

          {(status === 'uploading' || status === 'parsing') && (
            <div style={{ textAlign: 'center', padding: '40px 0' }}>
              <Spin size="large" style={{ marginBottom: 16 }} />
              <div style={{ marginBottom: 16 }}>
                <Text strong>{currentStep || 'Processing...'}</Text>
                <br />
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {file?.name}
                </Text>
              </div>
              <Progress percent={progress} status="active" />
            </div>
          )}

          {status === 'failed' && (
            <Alert
              message="Parsing failed"
              description={error}
              type="error"
              showIcon
              icon={<CloseCircleOutlined />}
              action={
                <Button icon={<ReloadOutlined />} onClick={handleReset} size="small" type="primary" danger ghost>
                  Upload Again
                </Button>
              }
              style={{ borderRadius: 12, padding: 24 }}
            />
          )}
        </Card>

        {status === 'completed' && taskResult && (
          <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <Alert
              message={<Text strong>Parsing completed: {taskResult.fileName}</Text>}
              type="success"
              showIcon
              icon={<CheckCircleOutlined />}
              action={
                <Button type="default" size="small" icon={<ReloadOutlined />} onClick={handleReset}>
                  Upload New File
                </Button>
              }
              style={{ borderRadius: 12, padding: '16px 20px' }}
            />

            {taskResult.parsedContent && (
              <Card
                title={<Space><FileTextOutlined style={{ color: '#1677ff' }} /> Parsed Content Summary</Space>}
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
                title={<Space><RobotOutlined style={{ color: '#ef4444' }} /> AI Analysis</Space>}
                bordered={false}
                style={{ borderRadius: 12 }}
              >
                <Paragraph style={{ whiteSpace: 'pre-wrap', color: '#475569', fontSize: 14 }}>
                  {taskResult.aiAnalysis}
                </Paragraph>
              </Card>
            )}

            <TeachingMaterialEditorCard
              editorLoading={editorLoading}
              editorDraft={editorDraft}
              savingDraft={savingDraft}
              publishingVersion={publishingVersion}
              exportingMarkdown={exportingMarkdown}
              versionsLoading={versionsLoading}
              materialVersions={materialVersions}
              rollbackTip={rollbackTip}
              selectionExplainLoading={selectionExplainLoading}
              selectionExplainResult={selectionExplainResult}
              selectionExplainHistory={selectionExplainHistory}
              selectionHistoryLoading={selectionHistoryLoading}
              traceKnowledgeFilter={traceKnowledgeFilter}
              traceIdeologyFilter={traceIdeologyFilter}
              traceUseCourseFilter={traceUseCourseFilter}
              selectedCourseId={selectedCourseId}
              traceLoading={traceLoading}
              traceTotal={traceTotal}
              traceRows={traceRows}
              onSaveDraft={handleSaveDraft}
              onPublishVersion={handlePublishVersion}
              onExportMarkdown={handleExportMarkdown}
              onRefreshVersions={() => taskResult?.taskId && loadMaterialVersions(taskResult.taskId)}
              onViewVersion={handleViewVersion}
              onRollbackVersion={handleRollbackVersion}
              onChangeTitle={(value) => updateEditorDraft((draft) => ({ ...draft, title: value }))}
              onChangeLectureNotes={(value) => {
                setSelectedLectureText('');
                updateEditorDraft((draft) => ({ ...draft, lectureNotes: value }));
              }}
              onLectureSelection={handleLectureSelection}
              onExplainSelection={handleExplainSelection}
              onRefreshSelectionHistory={() => loadSelectionExplainHistory(editorDraft?.materialId, editorDraft?.courseId)}
              onCopySelectionExplanation={handleCopySelectionExplanation}
              onAppendSelectionExplanation={handleAppendSelectionExplanation}
              onAddCase={addCaseItem}
              onUpdateCase={updateCaseItem}
              onRemoveCase={removeCaseItem}
              onAddQuestion={addQuestion}
              onUpdateQuestion={updateQuestion}
              onRemoveQuestion={removeQuestion}
              onUpdateScoringPoint={updateScoringPoint}
              onAddScoringPoint={addScoringPoint}
              onRemoveScoringPoint={removeScoringPoint}
              onChangeTraceKnowledgeFilter={setTraceKnowledgeFilter}
              onChangeTraceIdeologyFilter={setTraceIdeologyFilter}
              onToggleTraceUseCourse={() => setTraceUseCourseFilter((previous) => !previous)}
              onSearchTraces={handleSearchTraces}
            />
          </Space>
        )}
      </div>
    </div>
  );
};
