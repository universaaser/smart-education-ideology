import React, { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Avatar,
  Button,
  Card,
  List,
  message,
  notification,
  Select,
  Space,
  Spin,
  Tag,
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
  ParseTaskCorrectionDraftInfo,
  ParseTaskListItem,
  PipelineResultInfo,
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
import { ParseResultCorrectionCard } from '../components/ParseResultCorrectionCard';
import { TeachingMaterialEditorCard } from '../components/TeachingMaterialEditorCard';
import { MarkdownView } from '../components/MarkdownView';
import { MineruStructuredView } from '../components/MineruStructuredView';
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
  const [correctionDraft, setCorrectionDraft] = useState<ParseTaskCorrectionDraftInfo | null>(null);
  const [correctionLoading, setCorrectionLoading] = useState(false);
  const [savingCorrection, setSavingCorrection] = useState(false);
  const [reparsingTask, setReparsingTask] = useState(false);
  const [retryingTask, setRetryingTask] = useState(false);
  const [correctionSyncNotice, setCorrectionSyncNotice] = useState('');
  // 实时 LLM 输出日志：取代原进度条展示
  const [liveLog, setLiveLog] = useState('');
  // 历史解析记录
  const [historyTasks, setHistoryTasks] = useState<ParseTaskListItem[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const pollTimerRef = useRef<number | null>(null);
  const liveLogTimerRef = useRef<number | null>(null);
  const liveLogBoxRef = useRef<HTMLPreElement | null>(null);
  // 用 ref 保存 cursor：轮询闭包里需要读到最新值，state 异步更新不可靠。
  const liveLogCursorRef = useRef(0);
  const isUnmountedRef = useRef(false);
  // 历史列表并发/节流守卫：避免上游任何误触发导致的重复请求刷爆后端日志。
  const historyInFlightRef = useRef(false);
  const historyLastLoadAtRef = useRef(0);
  const openedExternalTargetKeyRef = useRef('');
  const dismissedExternalTargetKeyRef = useRef('');
  const resourceUploadTargetKey = resourceUploadTarget
    ? `${resourceUploadTarget.taskId}:${resourceUploadTarget.materialId ?? 'latest'}`
    : '';
  const hasActiveUser = userId !== undefined && userId !== null;

  useEffect(() => {
    // StrictMode dev 下首次会经历 mount -> unmount -> remount：
    // 若只在 cleanup 把 isUnmountedRef 置 true，第二次 mount 后仍残留 true，
    // 会导致之后所有 setState 被静默丢弃。这里在 mount 时显式重置。
    isUnmountedRef.current = false;
    return () => {
      /**
       * 页面卸载时必须清理轮询，避免任务轮询在视图切走后继续回写状态。
       */
      isUnmountedRef.current = true;
      if (pollTimerRef.current !== null) {
        window.clearTimeout(pollTimerRef.current);
        pollTimerRef.current = null;
      }
      if (liveLogTimerRef.current !== null) {
        window.clearTimeout(liveLogTimerRef.current);
        liveLogTimerRef.current = null;
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

  const loadHistoryTasks = async () => {
    // 守卫 1：已有请求在途时直接忽略，避免重入。
    if (historyInFlightRef.current) {
      return;
    }
    // 守卫 2：1 秒内的重复触发直接忽略。防止上游任何异常的 re-render/StrictMode/父级轮询
    // 把 effect 反复驱动，避免 parse_tasks 查询在后端日志里被刷屏。
    const now = Date.now();
    if (now - historyLastLoadAtRef.current < 1000) {
      return;
    }
    historyInFlightRef.current = true;
    historyLastLoadAtRef.current = now;
    setHistoryLoading(true);
    try {
      const response = await uploadApi.listTasks({ userId, page: 1, size: 20 });
      if (!isUnmountedRef.current) {
        setHistoryTasks(response.records || []);
      }
    } catch {
      if (!isUnmountedRef.current) {
        message.error('Failed to load parse history');
      }
    } finally {
      historyInFlightRef.current = false;
      historyLastLoadAtRef.current = Date.now();
      if (!isUnmountedRef.current) {
        setHistoryLoading(false);
      }
    }
  };

  useEffect(() => {
    loadHistoryTasks();
    // userId 变化时需要重新加载自己的任务列表
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId]);

  /**
   * 页面底部自动滚动：每次 liveLog 增量写入后把日志框滚到底部。
   */
  useEffect(() => {
    const box = liveLogBoxRef.current;
    if (box) {
      box.scrollTop = box.scrollHeight;
    }
  }, [liveLog]);

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

  const normalizePipelineResult = (result?: PipelineResultInfo | null): PipelineResultInfo => ({
    documentStructure: {
      title: result?.documentStructure?.title || '',
      documentType: result?.documentStructure?.documentType || 'UNKNOWN',
      overview: result?.documentStructure?.overview || '',
      chapterOutline: result?.documentStructure?.chapterOutline || [],
      teachingFocus: result?.documentStructure?.teachingFocus || [],
      rawMarkdown: result?.documentStructure?.rawMarkdown,
      parseMode: result?.documentStructure?.parseMode,
      mineruContent: result?.documentStructure?.mineruContent,
    },
    knowledgePoints: result?.knowledgePoints || [],
    ideologyMatches: result?.ideologyMatches || [],
    teachingArtifacts: {
      lectureNotes: result?.teachingArtifacts?.lectureNotes || '',
      cases: result?.teachingArtifacts?.cases || [],
      questions: result?.teachingArtifacts?.questions || [],
    },
    warnings: result?.warnings || [],
    inferred: result?.inferred || false,
    schemaVersion: result?.schemaVersion || 'v1',
  });

  const buildCorrectionDraft = (draft: ParseTaskCorrectionDraftInfo): ParseTaskCorrectionDraftInfo => ({
    ...draft,
    result: normalizePipelineResult(draft.result),
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
    if (liveLogTimerRef.current !== null) {
      window.clearTimeout(liveLogTimerRef.current);
      liveLogTimerRef.current = null;
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
    setSavingCorrection(false);
    setPublishingVersion(false);
    setMaterialVersions([]);
    setVersionsLoading(false);
    setExportingMarkdown(false);
    setCorrectionDraft(null);
    setCorrectionLoading(false);
    setReparsingTask(false);
    setRetryingTask(false);
    setCorrectionSyncNotice('');
    setSelectedCourseId(undefined);
    setLiveLog('');
    liveLogCursorRef.current = 0;
    resetTransientPanels();
    if (!options?.preserveWarning) {
      setOpenContextWarning('');
    }
  };

  /**
   * 定时拉取 LLM 实时输出。任务进入终态（COMPLETED/FAILED）后自动停止。
   */
  const pollLiveLog = (taskId: number) => {
    const loop = async () => {
      try {
        const slice = await uploadApi.getLiveLog(taskId, liveLogCursorRef.current);
        if (isUnmountedRef.current) {
          return;
        }
        if (slice.content) {
          setLiveLog((prev) => prev + slice.content);
          liveLogCursorRef.current = slice.cursor;
        }
        // 后端已进入终态就不再继续轮询；UI 后续由 pollTaskStatus 接管收尾。
        if (slice.status === 'COMPLETED' || slice.status === 'FAILED') {
          return;
        }
      } catch {
        // 网络抖动时静默继续，避免日志轮询本身把前端打挂
      }
      liveLogTimerRef.current = window.setTimeout(loop, 800);
    };
    loop();
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

  const loadCorrectionDraft = async (taskId: number) => {
    setCorrectionLoading(true);
    try {
      const draft = await uploadApi.getCorrectionDraft(taskId);
      if (!isUnmountedRef.current) {
        setCorrectionDraft(buildCorrectionDraft(draft));
      }
    } catch (err: unknown) {
      if (!isUnmountedRef.current) {
        setCorrectionDraft(null);
        message.error(err instanceof Error ? err.message : 'Failed to load correction draft');
      }
    } finally {
      if (!isUnmountedRef.current) {
        setCorrectionLoading(false);
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
      setCorrectionSyncNotice('');
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
      await loadCorrectionDraft(taskInfo.taskId);

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

  const beginTaskProcessing = (
    task: Pick<UploadTaskInfo, 'taskId' | 'fileName' | 'courseId' | 'status' | 'progress' | 'currentStep'>,
    fallbackStep: string,
  ) => {
    if (pollTimerRef.current !== null) {
      window.clearTimeout(pollTimerRef.current);
      pollTimerRef.current = null;
    }
    if (liveLogTimerRef.current !== null) {
      window.clearTimeout(liveLogTimerRef.current);
      liveLogTimerRef.current = null;
    }

    setFile(null);
    setStatus('parsing');
    setProgress(task.progress ?? 10);
    setCurrentStep(task.currentStep || fallbackStep);
    setTaskResult({
      taskId: task.taskId,
      fileName: task.fileName,
      status: task.status || 'UPLOADING',
      progress: task.progress ?? 10,
      currentStep: task.currentStep || fallbackStep,
      courseId: task.courseId,
    });
    setSelectedCourseId(task.courseId ?? selectedCourseId);
    setError('');
    setEditorDraft(null);
    setEditorLoading(false);
    setMaterialVersions([]);
    setVersionsLoading(false);
    setCorrectionDraft(null);
    setCorrectionLoading(false);
    setCorrectionSyncNotice('');
    setLiveLog('');
    liveLogCursorRef.current = 0;
    resetTransientPanels();

    pollTaskStatus(task.taskId);
    pollLiveLog(task.taskId);
  };

  const pollTaskStatus = (taskId: number) => {
    const poll = async () => {
      try {
        const taskInfo = await uploadApi.getTaskStatus(taskId);
        if (isUnmountedRef.current) {
          return;
        }

        setProgress(taskInfo.progress);
        setCurrentStep(taskInfo.currentStep);
        setTaskResult(taskInfo);

        if (taskInfo.status === 'COMPLETED') {
          notification.success({
            message: 'Parsing completed',
            description: taskInfo.fileName,
            duration: 4,
          });
          await loadTaskIntoEditor(taskInfo);
          // 刷新历史列表，让刚完成的任务出现在顶部
          loadHistoryTasks();
          return;
        }

        if (taskInfo.status === 'FAILED') {
          setTaskResult(taskInfo);
          setStatus('failed');
          setError(taskInfo.errorMessage || 'Failed to parse file');
          loadHistoryTasks();
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
      beginTaskProcessing(
        {
          taskId: result.taskId,
          fileName: result.fileName,
          courseId: selectedCourseId,
          status: result.status,
          progress: 10,
          currentStep: 'File uploaded, parsing in progress...',
        },
        'File uploaded, parsing in progress...',
      );
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

  /**
   * 从历史解析记录列表打开一个旧任务。COMPLETED 直接加载编辑器查看对应关系；
   * 其余状态给出明确提示，不做中间态。
   */
  const handleOpenHistoryTask = async (item: ParseTaskListItem) => {
    if (item.status === 'COMPLETED') {
      try {
        const taskInfo = await uploadApi.getTaskStatus(item.taskId);
        await loadTaskIntoEditor(taskInfo);
      } catch (err: unknown) {
        message.error(err instanceof Error ? err.message : 'Failed to open task');
      }
      return;
    }
    message.info(`Task ${item.taskId} status: ${item.status}`);
  };

  const handleRetryTask = async (taskId: number, fileName: string, courseId?: number | null) => {
    setRetryingTask(true);
    try {
      const taskInfo = await uploadApi.retryTask(taskId);
      if (!isUnmountedRef.current) {
        message.success('Retry started');
        beginTaskProcessing(
          {
            taskId: taskInfo.taskId,
            fileName: taskInfo.fileName || fileName,
            courseId: taskInfo.courseId ?? courseId ?? undefined,
            status: taskInfo.status,
            progress: taskInfo.progress,
            currentStep: taskInfo.currentStep,
          },
          'Retry requested',
        );
      }
    } catch (err: unknown) {
      if (!isUnmountedRef.current) {
        message.error(err instanceof Error ? err.message : 'Failed to retry task');
      }
    } finally {
      if (!isUnmountedRef.current) {
        setRetryingTask(false);
      }
    }
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

  const createEmptyQuestion = (): TeachingQuestionInfo => ({
    questionType: 'SHORT_ANSWER',
    difficulty: 'MEDIUM',
    knowledgePointId: null,
    stem: '',
    options: [],
    referenceAnswer: '',
    scoringPoints: [''],
  });

  const updateQuestion = (index: number, field: keyof TeachingQuestionInfo, value: string | string[] | number | null) => {
    updateEditorDraft((draft) => {
      const questions = [...draft.questions];
      const current = questions[index] || createEmptyQuestion();
      questions[index] = { ...current, [field]: value };
      return { ...draft, questions };
    });
  };

  const addQuestion = () => {
    updateEditorDraft((draft) => ({
      ...draft,
      questions: [...draft.questions, createEmptyQuestion()],
    }));
  };

  const removeQuestion = (index: number) => {
    updateEditorDraft((draft) => ({
      ...draft,
      questions: draft.questions.filter((_, currentIndex) => currentIndex !== index),
    }));
  };

  const updateQuestionOption = (questionIndex: number, optionIndex: number, value: string) => {
    updateEditorDraft((draft) => {
      const questions = [...draft.questions];
      const target = questions[questionIndex] || createEmptyQuestion();
      const options = [...(target.options || [])];
      options[optionIndex] = value;
      questions[questionIndex] = { ...target, options };
      return { ...draft, questions };
    });
  };

  const addQuestionOption = (questionIndex: number) => {
    updateEditorDraft((draft) => {
      const questions = [...draft.questions];
      const target = questions[questionIndex] || createEmptyQuestion();
      questions[questionIndex] = {
        ...target,
        options: [...(target.options || []), ''],
      };
      return { ...draft, questions };
    });
  };

  const removeQuestionOption = (questionIndex: number, optionIndex: number) => {
    updateEditorDraft((draft) => {
      const questions = [...draft.questions];
      const target = questions[questionIndex] || createEmptyQuestion();
      questions[questionIndex] = {
        ...target,
        options: (target.options || []).filter((_, currentIndex) => currentIndex !== optionIndex),
      };
      return { ...draft, questions };
    });
  };

  const updateScoringPoint = (questionIndex: number, pointIndex: number, value: string) => {
    updateEditorDraft((draft) => {
      const questions = [...draft.questions];
      const target = questions[questionIndex] || createEmptyQuestion();
      const scoringPoints = [...(target.scoringPoints || [])];
      scoringPoints[pointIndex] = value;
      questions[questionIndex] = { ...target, scoringPoints };
      return { ...draft, questions };
    });
  };

  const addScoringPoint = (questionIndex: number) => {
    updateEditorDraft((draft) => {
      const questions = [...draft.questions];
      const target = questions[questionIndex] || createEmptyQuestion();
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
      const target = questions[questionIndex] || createEmptyQuestion();
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
      questionType: question.questionType || 'SHORT_ANSWER',
      difficulty: question.difficulty || 'MEDIUM',
      knowledgePointId: question.knowledgePointId ?? null,
      stem: question.stem?.trim() || '',
      options: (question.options || []).map((option) => option.trim()).filter(Boolean),
      referenceAnswer: question.referenceAnswer?.trim() || '',
      scoringPoints: (question.scoringPoints || []).map((point) => point.trim()).filter(Boolean),
    })),
  });

  const handleCorrectionChange = (next: ParseTaskCorrectionDraftInfo) => {
    setCorrectionDraft(buildCorrectionDraft(next));
  };

  const handleSaveCorrectionDraft = async () => {
    if (!taskResult?.taskId || !correctionDraft) {
      return;
    }
    setSavingCorrection(true);
    try {
      const saved = await uploadApi.saveCorrectionDraft(taskResult.taskId, correctionDraft.result);
      const normalizedDraft = buildCorrectionDraft(saved);
      setCorrectionDraft(normalizedDraft);

      const hasMaterialSnapshot = Boolean(editorDraft?.materialId) || materialVersions.length > 0;
      if (!hasMaterialSnapshot) {
        const refreshedEditorDraft = await uploadApi.getEditorDraft(taskResult.taskId);
        if (!isUnmountedRef.current) {
          setEditorDraft(refreshedEditorDraft);
          setSelectedCourseId(refreshedEditorDraft.courseId ?? taskResult.courseId ?? undefined);
          await refreshEditorPanels(taskResult.taskId, refreshedEditorDraft.materialId, refreshedEditorDraft.courseId);
          setCorrectionSyncNotice(
            'Correction saved. Teaching editor baseline was refreshed because no material snapshot exists yet.',
          );
        }
      } else if (!isUnmountedRef.current) {
        setCorrectionSyncNotice(
          'Correction saved. Existing teaching material snapshot was kept unchanged.',
        );
      }

      if (!isUnmountedRef.current) {
        message.success('Correction draft saved');
      }
    } catch (err: unknown) {
      if (!isUnmountedRef.current) {
        message.error(err instanceof Error ? err.message : 'Failed to save correction draft');
      }
    } finally {
      if (!isUnmountedRef.current) {
        setSavingCorrection(false);
      }
    }
  };

  const handleReparseTask = async () => {
    if (!taskResult?.taskId || !taskResult.fileName) {
      return;
    }
    setReparsingTask(true);
    try {
      const restarted = await uploadApi.reparseTask(taskResult.taskId);
      if (!isUnmountedRef.current) {
        message.success('Reparse started');
        beginTaskProcessing(
          {
            taskId: restarted.taskId,
            fileName: restarted.fileName || taskResult.fileName,
            courseId: restarted.courseId ?? taskResult.courseId,
            status: restarted.status,
            progress: restarted.progress,
            currentStep: restarted.currentStep,
          },
          'Reparse requested',
        );
      }
    } catch (err: unknown) {
      if (!isUnmountedRef.current) {
        message.error(err instanceof Error ? err.message : 'Failed to reparse task');
      }
    } finally {
      if (!isUnmountedRef.current) {
        setReparsingTask(false);
      }
    }
  };

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

  const getParseModeTag = (parseMode?: string) => {
    if (parseMode === 'MINERU') {
      return <Tag color="geekblue">MINERU</Tag>;
    }
    if (parseMode === 'FALLBACK_LLM') {
      return <Tag color="orange">FALLBACK</Tag>;
    }
    if (parseMode === 'REGENERATE') {
      return <Tag color="purple">REGENERATE</Tag>;
    }
    return null;
  };

  const draggerProps = {
    name: 'file',
    multiple: false,
    fileList: [],
    beforeUpload: (nextFile: File) => {
      const isAllowed = /\.(pdf|doc|docx|ppt|pptx|xls|xlsx|md|markdown)$/i.test(nextFile.name);
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
      setCorrectionDraft(null);
      setCorrectionSyncNotice('');
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

        <Card
          bordered={false}
          style={{ borderRadius: 12 }}
          title={
            <Space style={{ justifyContent: 'space-between', width: '100%' }}>
              <Text strong>Parse History</Text>
              <Button
                size="small"
                type="link"
                icon={<ReloadOutlined />}
                onClick={loadHistoryTasks}
                loading={historyLoading}
              >
                Refresh
              </Button>
            </Space>
          }
        >
          <List
            size="small"
            loading={historyLoading}
            locale={{ emptyText: 'No parse task yet' }}
            dataSource={historyTasks}
            renderItem={(item) => {
              const statusColor =
                item.status === 'COMPLETED'
                  ? 'success'
                  : item.status === 'FAILED'
                    ? 'error'
                    : 'processing';
              const createdLabel = item.createdAt ? new Date(item.createdAt).toLocaleString() : '';
              const actions =
                item.status === 'COMPLETED'
                  ? [
                      <Button
                        key="open"
                        size="small"
                        type="link"
                        onClick={() => handleOpenHistoryTask(item)}
                      >
                        Open
                      </Button>,
                    ]
                  : item.status === 'FAILED'
                    ? [
                        <Button
                          key="retry"
                          size="small"
                          type="link"
                          loading={retryingTask}
                          onClick={() => handleRetryTask(item.taskId, item.fileName, item.courseId)}
                        >
                          Retry
                        </Button>,
                      ]
                    : [
                        <Button key="view" size="small" type="link" disabled>
                          View
                        </Button>,
                      ];
              return (
                <List.Item
                  actions={actions}
                >
                  <List.Item.Meta
                    avatar={getFileIcon(item.fileName)}
                    title={
                      <Space>
                        <Text ellipsis style={{ maxWidth: 360 }}>{item.fileName}</Text>
                        <Tag color={statusColor}>{item.status}</Tag>
                        {getParseModeTag(item.parseMode)}
                      </Space>
                    }
                    description={
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        {createdLabel}
                        {item.status === 'FAILED' && item.errorMessage
                          ? ` · ${item.errorMessage.slice(0, 80)}`
                          : ''}
                      </Text>
                    }
                  />
                </List.Item>
              );
            }}
          />
        </Card>

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
            <div style={{ padding: '12px 0' }}>
              <Space align="center" style={{ marginBottom: 12 }}>
                <Spin size="small" />
                <Text strong>{currentStep || 'Processing...'}</Text>
                {file?.name && (
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    · {file.name}
                  </Text>
                )}
              </Space>
              <pre
                ref={liveLogBoxRef}
                style={{
                  maxHeight: 280,
                  minHeight: 160,
                  overflowY: 'auto',
                  backgroundColor: '#0f172a',
                  color: '#e2e8f0',
                  padding: 12,
                  borderRadius: 8,
                  fontSize: 12,
                  lineHeight: 1.5,
                  fontFamily: "'JetBrains Mono', 'Consolas', monospace",
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                  margin: 0,
                }}
              >
                {liveLog || 'Waiting for LLM output...'}
              </pre>
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
                <Space>
                  <Button
                    icon={<ReloadOutlined />}
                    onClick={() => taskResult?.taskId && handleRetryTask(taskResult.taskId, taskResult.fileName)}
                    size="small"
                    type="primary"
                    loading={retryingTask}
                    disabled={!taskResult?.taskId}
                    danger
                    ghost
                  >
                    Retry Task
                  </Button>
                  <Button onClick={handleReset} size="small">
                    Upload New File
                  </Button>
                </Space>
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

            {/*
              解析结果展示优先走 MineruStructuredView：当 MinerU 返回结构化内容时，
              提供“大纲 / 全文 / 图片 / 表格 / 公式 / 分块”六个 Tab 的富视图；
              其余情况（回退到本地抽取器）退化到 Markdown 预览卡片，避免破坏老流程。
            */}
            {correctionDraft?.result.documentStructure?.mineruContent ? (
              <MineruStructuredView
                content={correctionDraft.result.documentStructure.mineruContent}
                rawMarkdown={
                  correctionDraft.result.documentStructure.rawMarkdown ||
                  taskResult.parsedContent
                }
                title={`Parsed Content: ${taskResult.fileName}`}
                parseMode={correctionDraft.result.documentStructure.parseMode}
              />
            ) : (
              taskResult.parsedContent && (
                <Card
                  title={(
                    <Space size={8}>
                      <FileTextOutlined style={{ color: '#1677ff' }} />
                      <span>Parsed Content Summary</span>
                      {getParseModeTag(correctionDraft?.result.documentStructure?.parseMode)}
                    </Space>
                  )}
                  bordered={false}
                  style={{
                    borderRadius: 12,
                    boxShadow: '0 1px 2px rgba(15, 23, 42, 0.04)',
                  }}
                  headStyle={{
                    background: 'linear-gradient(90deg, #e6f4ff 0%, #ffffff 100%)',
                    borderBottom: '1px solid #e2e8f0',
                    borderRadius: '12px 12px 0 0',
                  }}
                  bodyStyle={{ padding: '18px 22px' }}
                >
                  <MarkdownView content={taskResult.parsedContent} />
                </Card>
              )
            )}

            {taskResult.aiAnalysis && (
              <Card
                title={(
                  <Space size={8}>
                    <RobotOutlined style={{ color: '#ef4444' }} />
                    <span>AI Analysis</span>
                  </Space>
                )}
                bordered={false}
                style={{
                  borderRadius: 12,
                  boxShadow: '0 1px 2px rgba(15, 23, 42, 0.04)',
                }}
                headStyle={{
                  background: 'linear-gradient(90deg, #fff1f0 0%, #ffffff 100%)',
                  borderBottom: '1px solid #fee2e2',
                  borderRadius: '12px 12px 0 0',
                }}
                bodyStyle={{ padding: '18px 22px' }}
              >
                <MarkdownView content={taskResult.aiAnalysis} />
              </Card>
            )}

            <ParseResultCorrectionCard
              draft={correctionDraft}
              loading={correctionLoading}
              saving={savingCorrection}
              reparsing={reparsingTask}
              syncNotice={correctionSyncNotice}
              onChange={handleCorrectionChange}
              onSave={handleSaveCorrectionDraft}
              onReparse={handleReparseTask}
            />

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
              onUpdateQuestionOption={updateQuestionOption}
              onAddQuestionOption={addQuestionOption}
              onRemoveQuestionOption={removeQuestionOption}
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
