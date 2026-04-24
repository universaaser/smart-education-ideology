/**
 * Unified API client
 */
import { BootstrapData } from '../types';
import { downloadBlobFile } from './download';

const BASE_URL = '/api';

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

const TOKEN_KEY = 'smart_edu_token';
type QueryValue = string | number | boolean | null | undefined;

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

function buildQueryString(params: Record<string, QueryValue>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  });
  const query = search.toString();
  return query ? `?${query}` : '';
}

function buildAuthHeaders(): Record<string, string> {
  const token = getToken();
  const headers: Record<string, string> = {};
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return headers;
}

async function fetchAuthenticatedBlob(url: string, errorMessage: string): Promise<Blob> {
  const response = await fetch(url, { headers: buildAuthHeaders() });
  if (!response.ok) {
    throw new Error(errorMessage);
  }
  return response.blob();
}

async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    ...((options.headers as Record<string, string>) || {}),
  };

  if (!(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${BASE_URL}${url}`, {
    ...options,
    headers,
  });

  if (!response.ok) {
    if (response.status === 401) {
      removeToken();
    }
    throw new Error(`Request failed: ${response.status} ${response.statusText}`);
  }

  const result: ApiResponse<T> = await response.json();
  if (result.code !== 200) {
    throw new Error(result.message || 'Request failed');
  }

  return result.data;
}

export function get<T>(url: string): Promise<T> {
  return request<T>(url, { method: 'GET' });
}

export function post<T>(url: string, data?: unknown): Promise<T> {
  return request<T>(url, {
    method: 'POST',
    body: data instanceof FormData ? data : JSON.stringify(data),
  });
}

export function put<T>(url: string, data?: unknown): Promise<T> {
  return request<T>(url, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export function del<T>(url: string): Promise<T> {
  return request<T>(url, { method: 'DELETE' });
}

export function patch<T>(url: string, data?: unknown): Promise<T> {
  return request<T>(url, {
    method: 'PATCH',
    body: JSON.stringify(data),
  });
}

export const authApi = {
  login: (username: string, password: string) =>
    post<{ token: string; user: UserInfo }>('/auth/login', { username, password }),
  register: (username: string, password: string, email: string, role: string) =>
    post<{ token: string; user: UserInfo }>('/auth/register', { username, password, email, role }),
  getCurrentUser: () => get<UserInfo>('/auth/me'),
  bootstrap: () => get<BootstrapData>('/auth/bootstrap'),
};

export const dashboardApi = {
  getStats: () => get<Record<string, unknown>>('/dashboard/stats'),
  getCourses: () => get<CourseInfo[]>('/dashboard/courses'),
  getActivities: (limit = 10) => get<ActivityInfo[]>(`/dashboard/activities?limit=${limit}`),
  getTrend: () => get<TrendItem[]>('/dashboard/trend'),
};

export const chatApi = {
  getSessions: (userId: number) => get<ChatSessionInfo[]>(`/chat/sessions?userId=${userId}`),
  // aiModel 对应 DeepSeek/OpenAI/Gemini 等提供商标识，后端据此路由到对应大模型
  createSession: (title: string, userId: number, aiModel?: string) =>
    post<ChatSessionInfo>('/chat/sessions', { userId, title, aiModel }),
  getSessionDetail: (sessionId: number) =>
    get<{ sessionId: number; messages: ChatMessageInfo[] }>(`/chat/sessions/${sessionId}`),
  sendMessage: (sessionId: number, message: string) =>
    post<ChatResponseInfo>(`/chat/sessions/${sessionId}/message`, { message }),
  explainSelection: (request: SelectionExplainRequest) =>
    post<SelectionExplainResponse>('/chat/explain-selection', request),
  getSelectionExplainHistory: (
    params: { userId?: number; materialId?: number | null; courseId?: number | null; page?: number; size?: number } = {},
  ) =>
    get<PageResultInfo<SelectionExplainHistoryInfo>>(
      `/chat/explain-selection/history${buildQueryString({
        userId: params.userId,
        materialId: params.materialId,
        courseId: params.courseId,
        page: params.page,
        size: params.size,
      })}`
    ),
  deleteSession: (sessionId: number) => del<void>(`/chat/sessions/${sessionId}`),
};

export const resourceApi = {
  getAll: () => get<ResourceInfo[]>('/resources'),
  getById: (id: number) => get<ResourceInfo>(`/resources/${id}`),
  create: (resource: Partial<ResourceInfo>) => post<ResourceInfo>('/resources', resource),
  update: (id: number, resource: Partial<ResourceInfo>) => put<ResourceInfo>(`/resources/${id}`, resource),
  remove: (id: number) => del<void>(`/resources/${id}`),
  startCrawlUpdate: () => post<CrawlTaskStatusInfo>('/resources/crawl/start'),
  stopCrawlUpdate: () => post<CrawlTaskStatusInfo>('/resources/crawl/stop'),
  getCrawlStatus: () => get<CrawlTaskStatusInfo>('/resources/crawl/status'),
  // compatibility route
  triggerCrawlUpdate: (_limitPerSite = 20) => post<CrawlTaskStatusInfo>('/resources/crawl/update', {}),
};

export const knowledgeApi = {
  getGraph: () => get<{ nodes: KnowledgeNodeInfo[]; relations: KnowledgeRelationInfo[] }>('/knowledge/graph'),
  searchNodes: (keyword: string) => get<KnowledgeNodeInfo[]>(`/knowledge/nodes/search?keyword=${keyword}`),
  createNode: (node: KnowledgeNodeCreateRequest) => post<KnowledgeNodeInfo>('/knowledge/nodes', node),
  updateNodePosition: (id: number, x: number, y: number) =>
    patch<void>(`/knowledge/nodes/${id}/position`, { x, y }),
  deleteNode: (id: number) => del<void>(`/knowledge/nodes/${id}`),
  createRelation: (fromNodeId: number, toNodeId: number, relationType: string) =>
    post<KnowledgeRelationInfo>('/knowledge/relations', { fromNodeId, toNodeId, relationType }),
};

export const courseApi = {
  getKnowledgePoints: (courseId: number) =>
    get<KnowledgeNodeInfo[]>(`/courses/${courseId}/knowledge-points`),
  getMaterials: (courseId: number) =>
    get<CourseTeachingMaterialGroupInfo[]>(`/courses/${courseId}/materials`),
  create: (data: { name: string; code?: string; description?: string; semester?: string; teacherId: number }) =>
    post<CourseInfo>('/courses', data),
};

export const userApi = {
  uploadAvatar: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return post<{ avatarUrl: string }>('/users/avatar', formData);
  },
};

export const uploadApi = {
  uploadFile: (file: File, userId: number, courseId?: number) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('userId', String(userId));
    if (courseId !== undefined && courseId !== null) {
      formData.append('courseId', String(courseId));
    }
    return post<{ taskId: number; fileName: string; status: string }>('/upload/file', formData);
  },
  getTaskStatus: (taskId: number) => get<UploadTaskInfo>(`/upload/tasks/${taskId}`),
  getTaskResultDetail: (taskId: number) => get<PipelineResultInfo>(`/upload/tasks/${taskId}/result-detail`),
  regenerateTask: (taskId: number) => post<PipelineResultInfo>(`/upload/tasks/${taskId}/regenerate`),
  getCorrectionDraft: (taskId: number) =>
    get<ParseTaskCorrectionDraftInfo>(`/upload/tasks/${taskId}/correction-draft`),
  saveCorrectionDraft: (taskId: number, data: ParseTaskCorrectionSaveRequest) =>
    put<ParseTaskCorrectionDraftInfo>(`/upload/tasks/${taskId}/correction-draft`, data),
  reparseTask: (taskId: number) => post<UploadTaskInfo>(`/upload/tasks/${taskId}/reparse`),
  retryTask: (taskId: number) => post<UploadTaskInfo>(`/upload/tasks/${taskId}/retry`),
  getEditorDraft: (taskId: number) => get<TeachingMaterialDraftInfo>(`/upload/tasks/${taskId}/editor-draft`),
  saveEditorDraft: (taskId: number, data: TeachingMaterialSaveRequest) =>
    put<TeachingMaterialDraftInfo>(`/upload/tasks/${taskId}/editor-draft`, data),
  savePublishedMaterial: (taskId: number, data: TeachingMaterialSaveRequest) =>
    post<TeachingMaterialViewInfo>(`/upload/tasks/${taskId}/materials`, data),
  getTaskMaterialVersions: (taskId: number) =>
    get<MaterialVersionItemInfo[]>(`/upload/tasks/${taskId}/materials`),
  getTaskTraces: (
    taskId: number,
    params: { courseId?: number; knowledgePoint?: string; ideologyElement?: string; page?: number; size?: number } = {},
  ) =>
    get<PageResultInfo<TeachingMaterialTraceInfo>>(
      `/upload/tasks/${taskId}/traces${buildQueryString({
        courseId: params.courseId,
        knowledgePoint: params.knowledgePoint,
        ideologyElement: params.ideologyElement,
        page: params.page,
        size: params.size,
      })}`
    ),
  rollbackMaterialVersion: (taskId: number, materialId: number) =>
    post<TeachingMaterialDraftInfo>(`/upload/tasks/${taskId}/rollback/${materialId}`),
  /**
   * 增量拉取任务的 LLM 实时输出。前端按 cursor 不断累加 content。
   */
  getLiveLog: (taskId: number, offset: number = 0) =>
    get<{ content: string; cursor: number; status: string; currentStep?: string }>(
      `/upload/tasks/${taskId}/live-log${buildQueryString({ offset })}`,
    ),
  /**
   * 解析任务历史列表，userId 可选。
   */
  listTasks: (params: { userId?: number; page?: number; size?: number } = {}) =>
    get<PageResultInfo<ParseTaskListItem>>(
      `/upload/tasks${buildQueryString({
        userId: params.userId,
        page: params.page,
        size: params.size,
      })}`,
    ),
};

export const materialApi = {
  getById: (materialId: number) => get<TeachingMaterialViewInfo>(`/materials/${materialId}`),
  getTraces: (
    materialId: number,
    params: { knowledgePoint?: string; ideologyElement?: string; page?: number; size?: number } = {},
  ) =>
    get<PageResultInfo<TeachingMaterialTraceInfo>>(
      `/materials/${materialId}/traces${buildQueryString({
        knowledgePoint: params.knowledgePoint,
        ideologyElement: params.ideologyElement,
        page: params.page,
        size: params.size,
      })}`
    ),
  exportMarkdown: (materialId: number): Promise<Blob> =>
    fetchAuthenticatedBlob(`/api/materials/${materialId}/export/markdown`, 'Failed to export markdown'),
};

/**
 * 学习路径推荐 API
 */
export const pathApi = {
  /**
   * 获取个性化学习路径推荐
   * @param studentId 学生ID
   * @param masteredNodeIds 已掌握的知识点 ID 列表
   * @param interestTags 兴趣标签列表
   * @param maxLength 推荐路径最大长度，默认 8
   */
  getRecommendedPath: (
    studentId: number,
    masteredNodeIds: number[] = [],
    interestTags: string[] = [],
    maxLength = 8,
  ) =>
    post<LearningPathResult>('/path/recommend', {
      studentId,
      masteredNodeIds,
      interestTags,
      maxLength,
    }),
};

/**
 * 知识图谱 Excel 导入导出 API
 */
export const knowledgeExcelApi = {
  /**
   * 下载 Excel 模板（浏览器下载，返回 Blob）
   */
  downloadTemplate: async (): Promise<void> => {
    const templateBlob = await fetchAuthenticatedBlob('/api/knowledge/excel/template', 'Failed to download the template');
    downloadBlobFile(templateBlob, 'knowledge-graph-template.xlsx');
    return;
    const blob = await fetchAuthenticatedBlob('/api/knowledge/excel/template', '下载模板失败');
    downloadBlobFile(blob, '知识图谱导入模板.xlsx');
  },

  /**
   * 上传 Excel 文件，批量导入知识节点
   * @param file Excel 文件（.xlsx）
   */
  importFromExcel: (file: File): Promise<ExcelImportResult> => {
    const formData = new FormData();
    formData.append('file', file);
    return post<ExcelImportResult>('/knowledge/excel/import', formData);
  },
};

export interface UserInfo {
  id: number;
  username: string;
  email: string;
  role: string;
  realName?: string;
  department?: string;
  avatar?: string;
}

export interface CourseInfo {
  id: number;
  name: string;
  progress: number;
  ideologyScore: string;
  gradeColor: string;
  gradeLabel: string;
  code?: string;
  description?: string;
  semester?: string;
}

export interface ActivityInfo {
  id: number;
  title: string;
  description: string;
  type: string;
  createdAt: string;
}

export interface TrendItem {
  week: string;
  value: number;
}

export interface ChatSessionInfo {
  id: number;
  userId: number;
  title: string;
  aiModel: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessageInfo {
  id: number;
  sessionId: number;
  role: string;
  content: string;
  createdAt: string;
}

export interface ChatCitationInfo {
  itemType: string;
  referenceId?: number | null;
  title: string;
  snippet: string;
  source: string;
  sourceUrl: string;
  matchedBy: string;
  score?: number | null;
}

export interface ChatResponseInfo {
  message: ChatMessageInfo;
  citations: ChatCitationInfo[];
  retrievalStatus: 'FOUND' | 'WEAK_MATCH' | 'NO_CONTEXT' | string;
}

export interface KnowledgeContextInfo {
  itemType: string;
  referenceId: number;
  title: string;
  summary: string;
  source: string;
  sourceUrl: string;
  nodeType: string;
  snippet?: string;
  score?: number | null;
  matchedBy?: string;
}

export interface SelectionExplainRequest {
  text: string;
  userId: number;
  courseId?: number | null;
  materialId?: number | null;
  parseTaskId?: number | null;
}

export interface SelectionExplainEvidenceInfo {
  evidenceType: string;
  referenceId?: number | null;
  title: string;
  summary: string;
  source: string;
  sourceUrl: string;
}

export interface SelectionExplainResponse {
  recordId?: number | null;
  answer: string;
  modelReasoning?: string;
  hasReliableEvidence?: boolean;
  evidenceItems?: SelectionExplainEvidenceInfo[];
  contexts: KnowledgeContextInfo[];
  createdAt?: string;
}

export interface SelectionExplainHistoryInfo {
  recordId: number;
  userId: number;
  courseId?: number | null;
  materialId?: number | null;
  parseTaskId?: number | null;
  selectedText: string;
  answer: string;
  modelReasoning?: string;
  hasReliableEvidence?: boolean;
  evidenceItems?: SelectionExplainEvidenceInfo[];
  createdAt?: string;
}

export interface ResourceInfo {
  id: number;
  title: string;
  source: string;
  sourceUrl?: string;
  category: string;
  content: string;
  ideologySummary?: string;
  ideologyAnalysis?: string;
  tags: string;
  syncStatus?: string;
  status?: string;
  createdAt: string;
}

export interface CrawlSiteStatInfo {
  siteName: string;
  listUrl: string;
  fetchedCount: number;
  parsedCount: number;
  createdCount: number;
  deduplicatedCount: number;
  aiFailedCount: number;
  failedCount: number;
  error?: string;
}

export interface CrawlUpdateResultInfo {
  startedAt: string;
  finishedAt: string;
  limitPerSite: number;
  totalFetched: number;
  totalCreated: number;
  totalDeduplicated: number;
  totalAiFailed: number;
  totalFailed: number;
  durationMs: number;
  siteStats: CrawlSiteStatInfo[];
}

export interface CrawlTaskStatusInfo {
  state: 'IDLE' | 'RUNNING' | 'STOP_REQUESTED' | 'STOPPED' | 'COMPLETED' | 'FAILED';
  stopRequested: boolean;
  startedAt?: string;
  finishedAt?: string;
  currentSite?: string;
  currentUrl?: string;
  targetCreatedPerSite: number;
  candidateLimitPerSite: number;
  currentResult?: CrawlUpdateResultInfo;
  lastResult?: CrawlUpdateResultInfo;
  message?: string;
}

export interface KnowledgeNodeInfo {
  id: number;
  name: string;
  category: string;
  technicalDefinition: string;
  ideologicalValue: string;
  subject: string;
  nodeType: string;
  positionX: number;
  positionY: number;
  icon: string;
  subTitle: string;
  nodeSize: string;
  description: string;
}

export interface KnowledgeNodeCreateRequest {
  name: string;
  category?: string;
  technicalDefinition?: string;
  ideologicalValue?: string;
  subject?: string;
  icon?: string;
  subTitle?: string;
  nodeSize?: string;
  positionX?: number;
  positionY?: number;
}

export interface KnowledgeRelationInfo {
  id: number;
  fromNodeId: number;
  toNodeId: number;
  relationType: string;
  lineStyle?: string;
}

export interface UploadTaskInfo {
  taskId: number;
  fileName: string;
  status: string;
  progress: number;
  currentStep: string;
  parsedContent?: string;
  aiAnalysis?: string;
  completedAt?: string;
  errorMessage?: string;
  courseId?: number;
}

/**
 * 解析任务列表项：用于历史解析记录面板。
 */
export interface ParseTaskListItem {
  taskId: number;
  fileName: string;
  status: string;
  progress?: number;
  currentStep?: string;
  courseId?: number | null;
  parseMode?: 'MINERU' | 'FALLBACK_LLM' | 'REGENERATE' | string;
  createdAt?: string;
  completedAt?: string;
  errorMessage?: string;
}

export interface DocumentStructureInfo {
  title: string;
  documentType: 'TEXTBOOK' | 'OUTLINE' | 'PAPER' | 'UNKNOWN' | string;
  overview: string;
  chapterOutline: string[];
  teachingFocus: string[];
  // MinerU 接入后透出的原始 markdown，以及当前任务使用的解析模式标识。
  rawMarkdown?: string;
  parseMode?: 'MINERU' | 'FALLBACK_LLM' | 'REGENERATE' | string;
  // MinerU 全量结构化结果（大纲 / 分块 / 表格 / 图片 / 公式）。
  mineruContent?: MineruStructuredContentInfo;
}

export interface MineruContentBlockInfo {
  index: number;
  type: 'text' | 'image' | 'table' | 'equation' | 'chart' | 'list' | 'code'
    | 'header' | 'footer' | 'page_number' | 'aside_text' | 'page_footnote' | string;
  text?: string;
  textLevel?: number;
  imageUrl?: string;
  imagePath?: string;
  imageCaption?: string[];
  imageFootnote?: string[];
  tableCaption?: string[];
  tableFootnote?: string[];
  tableBody?: string;
  textFormat?: string;
  pageIdx?: number;
  bbox?: number[];
  subType?: string;
}

export interface MineruOutlineNodeInfo {
  title: string;
  level: number;
  blockIndex: number;
  pageIdx: number;
  children: MineruOutlineNodeInfo[];
}

export interface MineruStatsInfo {
  pageCount: number;
  headingCount: number;
  paragraphCount: number;
  imageCount: number;
  tableCount: number;
  equationCount: number;
  listCount: number;
  codeCount: number;
  wordCount: number;
}

export interface MineruStructuredContentInfo {
  batchId?: string;
  modelVersion?: string;
  assetBaseUrl?: string;
  blocks: MineruContentBlockInfo[];
  outline: MineruOutlineNodeInfo[];
  stats?: MineruStatsInfo;
  images: MineruContentBlockInfo[];
  tables: MineruContentBlockInfo[];
  equations: MineruContentBlockInfo[];
}

export interface PipelineKnowledgePointInfo {
  pointName: string;
  definition: string;
  chapter: string;
  importance: 'HIGH' | 'MEDIUM' | 'LOW' | string;
  evidenceSnippet: string;
}

export interface IdeologyMatchInfo {
  knowledgePointName: string;
  ideologyElement: string;
  matchReason: string;
  confidence: number;
}

export interface TeachingQuestionInfo {
  questionType?: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'SHORT_ANSWER' | 'CASE_ANALYSIS' | string;
  difficulty?: 'EASY' | 'MEDIUM' | 'HARD' | string;
  knowledgePointId?: number | null;
  stem: string;
  options?: string[];
  referenceAnswer: string;
  scoringPoints: string[];
}

export interface TeachingArtifactsInfo {
  lectureNotes: string;
  cases: string[];
  questions: TeachingQuestionInfo[];
}

export interface PipelineResultInfo {
  documentStructure?: DocumentStructureInfo;
  knowledgePoints: PipelineKnowledgePointInfo[];
  ideologyMatches: IdeologyMatchInfo[];
  teachingArtifacts?: TeachingArtifactsInfo;
  warnings: string[];
  inferred: boolean;
  schemaVersion: string;
}

export interface ParseTaskCorrectionDraftInfo {
  source: 'PIPELINE' | 'CORRECTION' | string;
  stale: boolean;
  savedAt?: string;
  sourceCompletedAt?: string;
  result: PipelineResultInfo;
}

export type ParseTaskCorrectionSaveRequest = PipelineResultInfo;

export interface TeachingTraceItemInfo {
  parseTaskId: number;
  knowledgePointName: string;
  knowledgePointId?: number | null;
  ideologyElement: string;
  evidenceSnippet: string;
  matchReason: string;
}

export interface TeachingMaterialSaveRequest {
  title: string;
  lectureNotes: string;
  cases: string[];
  questions: TeachingQuestionInfo[];
}

export interface TeachingMaterialDraftInfo {
  materialId?: number | null;
  parseTaskId: number;
  userId: number;
  courseId?: number | null;
  title: string;
  lectureNotes: string;
  cases: string[];
  questions: TeachingQuestionInfo[];
  traceItems: TeachingTraceItemInfo[];
  schemaVersion: string;
  versionNo: number;
  status: string;
  updatedAt?: string;
}

export interface TeachingMaterialViewInfo {
  materialId: number;
  parseTaskId: number;
  userId: number;
  courseId?: number | null;
  title: string;
  lectureNotes: string;
  cases: string[];
  questions: TeachingQuestionInfo[];
  traceItems: TeachingTraceItemInfo[];
  schemaVersion: string;
  versionNo: number;
  status: string;
  isLatest: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface MaterialVersionItemInfo {
  materialId: number;
  versionNo: number;
  status: string;
  isLatest: number;
  updatedAt?: string;
}

export interface CourseTeachingMaterialGroupInfo {
  parseTaskId: number;
  courseId: number;
  displayTitle: string;
  sourceFileName: string;
  latestMaterialId: number;
  latestVersionNo: number;
  latestStatus: string;
  updatedAt?: string;
  versions: MaterialVersionItemInfo[];
}

export interface TeachingMaterialTraceInfo {
  id: number;
  materialId: number;
  parseTaskId: number;
  courseId?: number | null;
  knowledgePointId?: number | null;
  knowledgePointName: string;
  ideologyElement: string;
  evidenceSnippet: string;
  matchReason: string;
  createdAt?: string;
}

export interface PageResultInfo<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

/** 学习路径推荐结果 */
export interface LearningPathResult {
  /** 推荐路径中的知识节点 ID 列表，按学习顺序排列 */
  nodeIds: number[];
  /** 推荐路径中的知识节点名称列表，与 nodeIds 一一对应 */
  nodeNames: string[];
  /** AI 对该路径的优化建议（可选） */
  aiSuggestion?: string;
}

/** Excel 导入建图结果 */
export interface ExcelImportResult {
  /** 成功导入的学科知识节点数量 */
  createdNodeCount: number;
  /** 成功导入的关系数量 */
  createdRelationCount: number;
  /** 导入过程中跳过的行数（格式不合法） */
  skippedRowCount: number;
  /** 导入校验或执行过程中产生的警告信息 */
  warnings: string[];
}

/** Phase 3A: semantic search request */
export interface SemanticSearchRequest {
  scope: 'knowledge_points' | 'ideology_matches' | 'selection_explain';
  text: string;
  topK?: number;
  courseId?: number | null;
}

/** Phase 3A: semantic search hit */
export interface SemanticHit {
  id: string;
  score: number;
  title: string;
  snippet: string;
  sourceType: string;
  sourceId?: number | null;
}

/** Phase 3A: semantic search API */
export const semanticApi = {
  search: (data: SemanticSearchRequest) =>
    post<SemanticHit[]>('/semantic/search', data),
};
