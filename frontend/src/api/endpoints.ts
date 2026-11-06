import { api } from './client';
import type {
  ApprovalResult,
  AuditEntry,
  InvigilationEntry,
  PublishedDocument,
  RoomPlan,
  RunDetail,
  RunParams,
  RunSummary,
  TimetableEntry,
  Duty,
  MyExams,
  SeatLookup,
  CatalogSummary,
  Course,
  CourseRequest,
  Faculty,
  FacultyRequest,
  GenerateSlotsRequest,
  ImportBatch,
  ImportKind,
  ImportMode,
  ImportResult,
  Lock,
  Page,
  PublicationStatus,
  Role,
  Room,
  RoomRequest,
  SameSlotGroup,
  SearchResult,
  Slot,
  Student,
  StudentDetail,
  TokenResponse,
  User,
} from './types';

export const authApi = {
  login: (username: string, password: string) =>
    api.post<TokenResponse>('/api/auth/login', { username, password }),
  me: () => api.get<User>('/api/auth/me'),
  changePassword: (currentPassword: string, newPassword: string) =>
    api.post<void>('/api/auth/password', { currentPassword, newPassword }),
};

export const catalogApi = {
  summary: () => api.get<CatalogSummary>('/api/catalog/summary'),
  courses: (q?: string, page = 0, size = 50) => api.get<Page<Course>>('/api/courses', { q, page, size }),
  createCourse: (body: CourseRequest) => api.post<Course>('/api/courses', body),
  updateCourse: (id: number, body: CourseRequest) => api.put<Course>(`/api/courses/${id}`, body),
  deleteCourse: (id: number) => api.delete(`/api/courses/${id}`),
  students: (q?: string, page = 0, size = 50) => api.get<Page<Student>>('/api/students', { q, page, size }),
  student: (id: number) => api.get<StudentDetail>(`/api/students/${id}`),
  rooms: () => api.get<Room[]>('/api/rooms'),
  createRoom: (body: RoomRequest) => api.post<Room>('/api/rooms', body),
  updateRoom: (id: number, body: RoomRequest) => api.put<Room>(`/api/rooms/${id}`, body),
  deleteRoom: (id: number) => api.delete(`/api/rooms/${id}`),
  faculty: (q?: string, page = 0, size = 50) => api.get<Page<Faculty>>('/api/faculty', { q, page, size }),
  createFaculty: (body: FacultyRequest) => api.post<Faculty>('/api/faculty', body),
  updateFaculty: (id: number, body: FacultyRequest) => api.put<Faculty>(`/api/faculty/${id}`, body),
  deleteFaculty: (id: number) => api.delete(`/api/faculty/${id}`),
  slots: () => api.get<Slot[]>('/api/slots'),
  generateSlots: (body: GenerateSlotsRequest) => api.post<Slot[]>('/api/slots/generate', body),
  deleteSlot: (id: number) => api.delete(`/api/slots/${id}`),
  locks: () => api.get<Lock[]>('/api/locks'),
  lock: (courseCode: string, slotKey: string) => api.put<Lock>(`/api/locks/${encodeURIComponent(courseCode)}`, { slotKey }),
  unlock: (courseCode: string) => api.delete(`/api/locks/${encodeURIComponent(courseCode)}`),
  groups: () => api.get<SameSlotGroup[]>('/api/same-slot-groups'),
  createGroup: (name: string, courses: string[]) => api.post<SameSlotGroup>('/api/same-slot-groups', { name, courses }),
  deleteGroup: (id: number) => api.delete(`/api/same-slot-groups/${id}`),
  search: (q: string) => api.get<SearchResult>('/api/search', { q }),
};

export const importApi = {
  upload: (kind: ImportKind, file: File, dryRun: boolean, mode: ImportMode) =>
    api.upload<ImportResult>(`/api/imports/${kind}`, file, { dryRun, mode }),
  list: (page = 0, size = 20) => api.get<Page<ImportBatch>>('/api/imports', { page, size }),
};

export const userApi = {
  list: (role?: Role, q?: string, page = 0) => api.get<Page<User>>('/api/users', { role, q, page }),
  create: (body: {
    username: string;
    displayName: string;
    password: string;
    role: Role;
    studentRoll?: string;
    facultyCode?: string;
  }) => api.post<User>('/api/users', body),
  update: (id: number, body: Partial<{ displayName: string; enabled: boolean; password: string; role: Role }>) =>
    api.patch<User>(`/api/users/${id}`, body),
};

export const auditApi = {
  search: (filters: { actor?: string; action?: string; entityType?: string; page?: number }) =>
    api.get<Page<AuditEntry>>('/api/audit', { ...filters, size: 50 }),
};

export const publicApi = {
  status: () => api.get<PublicationStatus>('/api/public/status'),
  seat: (roll: string) => api.get<SeatLookup>('/api/public/seat', { roll }),
};

export const portalApi = {
  myExams: () => api.get<MyExams>('/api/students/me/exams'),
  myDuties: () => api.get<Duty[]>('/api/faculty/me/duties'),
};

export const runsApi = {
  list: (page = 0, size = 20) => api.get<Page<RunSummary>>('/api/schedule-runs', { page, size }),
  create: (params: RunParams) => api.post<RunSummary>('/api/schedule-runs', params),
  get: (id: number) => api.get<RunDetail>(`/api/schedule-runs/${id}`),
  cancel: (id: number) => api.post<RunSummary>(`/api/schedule-runs/${id}/cancel`),
  remove: (id: number) => api.delete(`/api/schedule-runs/${id}`),
  timetable: (id: number) => api.get<TimetableEntry[]>(`/api/schedule-runs/${id}/timetable`),
  rooms: (id: number, slot: string) => api.get<RoomPlan[]>(`/api/schedule-runs/${id}/rooms`, { slot }),
  invigilation: (id: number) => api.get<InvigilationEntry[]>(`/api/schedule-runs/${id}/invigilation`),
  compare: (ids: number[]) => api.get<RunDetail[]>('/api/schedule-runs/compare', { ids: ids.join(',') }),
  approve: (id: number, comment?: string) => api.post<ApprovalResult>(`/api/schedule-runs/${id}/approvals`, { comment }),
  withdraw: (id: number) => api.delete<ApprovalResult>(`/api/schedule-runs/${id}/approvals/me`),
  retryPublish: (id: number) => api.post<ApprovalResult>(`/api/schedule-runs/${id}/publish`),
  disrupt: (id: number, roomCode: string, slot?: string) =>
    api.post<RunSummary>(`/api/schedule-runs/${id}/disruptions`, { roomCode, slot: slot || undefined }),
  documents: (id: number, kind?: string) => api.get<PublishedDocument[]>(`/api/schedule-runs/${id}/documents`, { kind }),
};
