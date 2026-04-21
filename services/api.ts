/**
 * Unified API client
 */
import { BootstrapData } from '../types';

const BASE_URL = '/api';

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

const TOKEN_KEY = 'smart_edu_token';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY);
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
  getSessions: (userId = 1) => get<ChatSessionInfo[]>(`/chat/sessions?userId=${userId}`),
  // aiModel 对应 DeepSeek/OpenAI/Gemini 等提供商标识，后端据此路由到对应大模型
  createSession: (title: string, userId = 1, aiModel?: string) =>
    post<ChatSessionInfo>('/chat/sessions', { userId, title, aiModel }),
  getSessionDetail: (sessionId: number) =>
    get<{ sessionId: number; messages: ChatMessageInfo[] }>(`/chat/sessions/${sessionId}`),
  sendMessage: (sessionId: number, message: string) =>
    post<ChatResponseInfo>(`/chat/sessions/${sessionId}/message`, { message }),
  explainSelection: (request: SelectionExplainRequest | string) =>
    post<SelectionExplainResponse>(
      '/chat/explain-selection',
      typeof request === 'string' ? { text: request } : request,
    ),
  getSelectionExplainHistory: (
    params: { userId?: number; materialId?: number | null; courseId?: number | null; page?: number; size?: number } = {},
  ) => {
    const search = new URLSearchParams();
    if (params.userId !== undefined) search.set('userId', String(params.userId));
    if (params.materialId !== undefined && params.materialId !== null) search.set('materialId', String(params.materialId));
    if (params.courseId !== undefined && params.courseId !== null) search.set('courseId', String(params.courseId));
    if (params.page !== undefined) search.set('page', String(params.page));
    if (params.size !== undefined) search.set('size', String(params.size));
    const query = search.toString();
    return get<PageResultInfo<SelectionExplainHistoryInfo>>(
      `/chat/explain-selection/history${query ? `?${query}` : ''}`
    );
  },
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
  updateNodePosition: (id: number, x: number, y: number) =>
    patch<void>(`/knowledge/nodes/${id}/position`, { x, y }),
  createRelation: (fromNodeId: number, toNodeId: number, relationType: string) =>
    post<KnowledgeRelationInfo>('/knowledge/relations', { fromNodeId, toNodeId, relationType }),
};

export const courseApi = {
  getKnowledgePoints: (courseId: number) =>
    get<KnowledgeNodeInfo[]>(`/courses/${courseId}/knowledge-points`),
  create: (data: { name: string; code?: string; description?: string; semester?: string; teacherId?: number }) =>
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
  uploadFile: (file: File, userId = 1, courseId?: number) => {
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
  ) => {
    const search = new URLSearchParams();
    if (params.courseId !== undefined) search.set('courseId', String(params.courseId));
    if (params.knowledgePoint) search.set('knowledgePoint', params.knowledgePoint);
    if (params.ideologyElement) search.set('ideologyElement', params.ideologyElement);
    if (params.page !== undefined) search.set('page', String(params.page));
    if (params.size !== undefined) search.set('size', String(params.size));
    const query = search.toString();
    return get<PageResultInfo<TeachingMaterialTraceInfo>>(
      `/upload/tasks/${taskId}/traces${query ? `?${query}` : ''}`
    );
  },
  rollbackMaterialVersion: (taskId: number, materialId: number) =>
    post<TeachingMaterialDraftInfo>(`/upload/tasks/${taskId}/rollback/${materialId}`),
};

export const materialApi = {
  getById: (materialId: number) => get<TeachingMaterialViewInfo>(`/materials/${materialId}`),
  getTraces: (
    materialId: number,
    params: { knowledgePoint?: string; ideologyElement?: string; page?: number; size?: number } = {},
  ) => {
    const search = new URLSearchParams();
    if (params.knowledgePoint) search.set('knowledgePoint', params.knowledgePoint);
    if (params.ideologyElement) search.set('ideologyElement', params.ideologyElement);
    if (params.page !== undefined) search.set('page', String(params.page));
    if (params.size !== undefined) search.set('size', String(params.size));
    const query = search.toString();
    return get<PageResultInfo<TeachingMaterialTraceInfo>>(
      `/materials/${materialId}/traces${query ? `?${query}` : ''}`
    );
  },
  exportMarkdown: async (materialId: number): Promise<Blob> => {
    const token = getToken();
    const headers: Record<string, string> = {};
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
    const response = await fetch(`/api/materials/${materialId}/export/markdown`, { headers });
    if (!response.ok) {
      throw new Error('Failed to export markdown');
    }
    return response.blob();
  },
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
    const token = getToken();
    const headers: Record<string, string> = {};
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
    const response = await fetch('/api/knowledge/excel/template', { headers });
    if (!response.ok) {
      throw new Error('下载模板失败');
    }
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = '知识图谱导入模板.xlsx';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
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
  userId?: number;
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

export interface DocumentStructureInfo {
  title: string;
  documentType: 'TEXTBOOK' | 'OUTLINE' | 'PAPER' | 'UNKNOWN' | string;
  overview: string;
  chapterOutline: string[];
  teachingFocus: string[];
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
  stem: string;
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
