/** Response and request shapes of the ExamGrid API (mirrors the backend DTOs). */

export type Role = 'EXAM_CELL' | 'FACULTY' | 'STUDENT';
export type Session = 'MORNING' | 'AFTERNOON' | 'EVENING';
export const SESSIONS: Session[] = ['MORNING', 'AFTERNOON', 'EVENING'];

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface User {
  id: number;
  username: string;
  displayName: string;
  role: Role;
  studentRoll: string | null;
  facultyCode: string | null;
  enabled: boolean;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  user: User;
}

export interface CatalogSummary {
  students: number;
  courses: number;
  enrolments: number;
  rooms: number;
  activeRooms: number;
  seats: number;
  faculty: number;
  slots: number;
  locks: number;
  sameSlotGroups: number;
}

export interface Course {
  id: number;
  code: string;
  name: string;
  department: string;
  hard: boolean;
  allowedSessions: Session[];
  candidates: number;
}

export interface CourseRequest {
  code: string;
  name: string;
  department?: string;
  hard?: boolean;
  allowedSessions?: Session[];
}

export interface Student {
  id: number;
  roll: string;
  name: string;
  department: string;
  email: string | null;
}

export interface StudentDetail {
  student: Student;
  courses: string[];
}

export interface Room {
  id: number;
  code: string;
  building: string;
  rows: number;
  columns: number;
  blockedSeats: string[];
  capacity: number;
  priority: number;
  active: boolean;
}

export interface RoomRequest {
  code: string;
  building?: string;
  rows: number;
  columns: number;
  blockedSeats: string[];
  priority?: number;
  active?: boolean;
}

export interface Faculty {
  id: number;
  code: string;
  name: string;
  email: string | null;
  department: string;
  teaches: string[];
  unavailableSlots: string[];
}

export interface FacultyRequest {
  code: string;
  name: string;
  email?: string;
  department?: string;
  teaches: string[];
  unavailableSlots: string[];
}

export interface Slot {
  id: number;
  key: string;
  date: string;
  session: Session;
}

export interface GenerateSlotsRequest {
  startDate: string;
  endDate: string;
  sessions: Session[];
  skipWeekdays?: string[];
  skipDates?: string[];
}

export interface Lock {
  courseCode: string;
  slotKey: string;
  lockedBy: string;
  lockedAt: string;
}

export interface SameSlotGroup {
  id: number;
  name: string;
  courses: string[];
}

export type ImportKind = 'courses' | 'students' | 'enrolments' | 'rooms' | 'faculty';
export type ImportMode = 'MERGE' | 'REPLACE';

export interface ImportIssue {
  row: number;
  column: string | null;
  severity: 'ERROR' | 'WARNING';
  message: string;
}

export interface ImportReport {
  kind: string;
  mode: ImportMode;
  totalRows: number;
  acceptedRows: number;
  errorCount: number;
  warningCount: number;
  issues: ImportIssue[];
  issuesTruncated: boolean;
  changes: Record<string, number>;
}

export interface ImportBatch {
  id: number;
  kind: string;
  filename: string;
  sizeBytes: number;
  sha256: string;
  dryRun: boolean;
  status: 'VALIDATED' | 'REJECTED' | 'COMMITTED';
  totalRows: number;
  acceptedRows: number;
  errorCount: number;
  warningCount: number;
  createdBy: string;
  createdAt: string;
}

export interface ImportResult {
  batch: ImportBatch;
  report: ImportReport;
}

export interface AuditEntry {
  id: number;
  occurredAt: string;
  actor: string;
  action: string;
  entityType: string | null;
  entityId: string | null;
  details: Record<string, unknown>;
  requestId: string | null;
}

export interface SearchHit {
  type: string;
  id: number;
  code: string;
  label: string;
}

export interface SearchResult {
  query: string;
  students: SearchHit[];
  courses: SearchHit[];
  faculty: SearchHit[];
  rooms: SearchHit[];
}

export interface PublicationStatus {
  published: boolean;
  publishedAt: string | null;
  firstExam: string | null;
  lastExam: string | null;
  exams: number;
}

export interface SeatInfo {
  courseCode: string;
  courseName: string;
  slot: string;
  date: string;
  session: Session;
  roomCode: string;
  building: string;
  seat: string;
}

export interface SeatLookup {
  roll: string;
  exams: SeatInfo[];
}

export interface MyExams {
  roll: string;
  name: string;
  exams: SeatInfo[];
}

export interface Duty {
  facultyCode: string;
  facultyName: string;
  slot: string;
  date: string;
  session: Session;
  roomCode: string;
}

// ------------------------------------------------------------------ schedule runs

export type RunStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'DONE'
  | 'INFEASIBLE'
  | 'FAILED'
  | 'CANCELLED'
  | 'PUBLISHING'
  | 'PUBLISHED'
  | 'SUPERSEDED';

export const ACTIVE_STATUSES: RunStatus[] = ['QUEUED', 'RUNNING'];
export type Optimizer = 'ANNEALING' | 'TABU' | 'NONE';

export interface RunSummary {
  id: number;
  label: string | null;
  status: RunStatus;
  progressPct: number;
  score: number | null;
  createdBy: string;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
  parentRunId: number | null;
}

export interface RunParams {
  label?: string;
  optimizer?: Optimizer;
  maxIterations?: number;
  timeLimitSeconds?: number;
  seed?: number;
  weights?: { sameDay?: number; backToBack?: number; hardGap?: number; hardMinGapDays?: number };
  studentsPerInvigilator?: number;
  minInvigilatorsPerRoom?: number;
  diagonalAdjacency?: boolean;
}

export interface TimetableMetrics {
  clashPairs: number;
  sameDayPairs: number;
  studentsWithSameDay: number;
  backToBackPairs: number;
  studentsWithBackToBack: number;
  hardGapPairs: number;
  score: number;
  slotsUsed: number;
  maxExamsPerStudentDay: number;
}

export interface SolverMetrics {
  timetable: TimetableMetrics;
  dsaturColours: number;
  repaired: boolean;
  initialScore: number;
  optimiserIterations: number;
  optimiserStop: string;
  roomSlotsUsed: number;
  seatUtilisation: number;
  invigilationDuties: number;
  unfilledDuties: number;
  minFacultyDuties: number;
  maxFacultyDuties: number;
  elapsedMillis: number;
}

export interface SlotLoad {
  slot: string;
  exams: number;
  candidates: number;
  rooms: number;
}

export interface RunMetrics {
  solver: SolverMetrics;
  slots: SlotLoad[];
  warnings: string[];
}

export interface InfeasibilityIssue {
  type: string;
  message: string;
  courses: string[];
  students: string[];
  proof: boolean;
}

export interface InputSummary {
  courses: number;
  examinedCourses: number;
  students: number;
  enrolments: number;
  slots: number;
  rooms: number;
  seats: number;
  faculty: number;
  locks: number;
  sameSlotGroups: number;
  fingerprint: string;
}

export interface Disruption {
  lostRoom: string;
  slot: string | null;
  studentsMoved: number;
  studentsUnaffected: number;
  reserveRoomsOpened: string[];
  affectedSlots: string[];
  summary: string;
}

export interface Approval {
  username: string;
  displayName: string;
  approvedAt: string;
  comment: string | null;
}

export interface RunDetail {
  run: RunSummary;
  params: Required<Omit<RunParams, 'label'>> & { label: string | null };
  metrics: RunMetrics | null;
  infeasibility: { issues: InfeasibilityIssue[] } | null;
  inputSummary: InputSummary | null;
  disruption: Disruption | null;
  errorMessage: string | null;
  dataChanged: boolean | null;
  approvals: Approval[];
}

export interface RunEvent {
  runId: number;
  status: RunStatus;
  phase: string;
  pct: number;
  score: number | null;
  message: string;
}

export interface TimetableEntry {
  courseCode: string;
  courseName: string;
  department: string;
  slot: string;
  date: string;
  session: Session;
  candidates: number;
  rooms: string[];
}

export interface SeatView {
  row: number;
  column: number;
  label: string;
  roll: string;
  courseCode: string;
}

export interface RoomPlan {
  slot: string;
  roomCode: string;
  building: string;
  rows: number;
  columns: number;
  blockedSeats: string[];
  capacity: number;
  seats: SeatView[];
  invigilators: string[];
  courses: string[];
}

export interface InvigilationEntry {
  facultyCode: string;
  facultyName: string;
  slot: string;
  date: string;
  session: Session;
  roomCode: string;
}

export interface ApprovalResult {
  approvals: number;
  required: number;
  publishing: boolean;
}

export interface PublishedDocument {
  id: number;
  kind: 'HALL_TICKET' | 'SEATING' | 'TIMETABLE' | 'INVIGILATION';
  ref: string;
  sizeBytes: number;
  createdAt: string;
}
