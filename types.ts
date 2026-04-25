export enum View {
  DASHBOARD = 'DASHBOARD',
  STUDENT_HOME = 'STUDENT_HOME',
  KNOWLEDGE_GRAPH = 'KNOWLEDGE_GRAPH',
  AI_ASSISTANT = 'AI_ASSISTANT',
  RESOURCE_UPLOAD = 'RESOURCE_UPLOAD',
  COURSE_LIBRARY = 'COURSE_LIBRARY',
  ALERTS = 'ALERTS',
  SOURCE_MANAGEMENT = 'SOURCE_MANAGEMENT',
  KEYWORD_TASKS = 'KEYWORD_TASKS',
  MATCH_REVIEW = 'MATCH_REVIEW',
  COURSE_MANAGEMENT = 'COURSE_MANAGEMENT',
  MODEL_SETTINGS = 'MODEL_SETTINGS',
  ADMIN_CONSOLE = 'ADMIN_CONSOLE',
  SETTINGS = 'SETTINGS',
  AUTH = 'AUTH'
}

export enum Role {
  TEACHER = 'TEACHER',
  STUDENT = 'STUDENT',
  ADMIN = 'ADMIN'
}

export interface NavItem {
  id: View;
  label: string;
  icon: string;
  isBeta?: boolean;
}

export interface ResourceUploadTarget {
  taskId: number;
  materialId?: number;
}

export interface ViewChangeOptions {
  highlightNodeIds?: number[];
  resourceUploadTarget?: ResourceUploadTarget;
  courseId?: number;
}

export type ViewChangeHandler = (view: View, options?: ViewChangeOptions) => void;

/** Current logged-in user info. */
export interface CurrentUser {
  id: number;
  username: string;
  email: string;
  role: Role | string;
  realName?: string;
  department?: string;
  avatar?: string;
}

export interface Capabilities {
  canViewDashboard: boolean;
  canUseAiAssistant: boolean;
  canViewCourseLibrary: boolean;
  canViewKnowledgeGraph: boolean;
  canEditKnowledgeGraph: boolean;
  canUploadResource: boolean;
  canTriggerCrawlUpdate: boolean;
  canCreateCourse: boolean;
  canManageUsers: boolean;
  canViewStudentAlerts: boolean;
  canManageSources: boolean;
  canManageKeywordTasks: boolean;
  canReviewMatches: boolean;
  canManageAiProviders: boolean;
  canManageAdminConsole: boolean;
  canSubmitLearningActivity: boolean;
}

export interface RoleUi {
  role: Role;
  roleLabel: string;
  defaultView: View;
  allowedViews: View[];
  capabilities: Capabilities;
}

export interface BootstrapData {
  user: CurrentUser;
  roleUi: RoleUi;
}
