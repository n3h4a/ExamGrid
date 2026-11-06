/**
 * Thin fetch wrapper for the ExamGrid API. Every error response is an RFC 9457 problem
 * (`application/problem+json`) with a stable `code`; it is surfaced as {@link ApiError} so
 * screens can switch on `code` and show `detail` and field `errors`.
 */

export interface FieldError {
  field: string;
  message: string;
}

export interface Problem {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  code?: string;
  requestId?: string;
  errors?: FieldError[];
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly requestId?: string;
  readonly fieldErrors: FieldError[];

  constructor(problem: Problem) {
    super(problem.detail ?? problem.title ?? `Request failed (${problem.status})`);
    this.name = 'ApiError';
    this.status = problem.status;
    this.code = problem.code ?? 'unknown';
    this.requestId = problem.requestId;
    this.fieldErrors = problem.errors ?? [];
  }
}

type TokenProvider = () => string | null;
type UnauthorizedHandler = () => void;

let tokenProvider: TokenProvider = () => null;
let onUnauthorized: UnauthorizedHandler = () => {};

/** Wired by the auth provider so requests carry the token and a 401 logs the user out. */
export function configureClient(provider: TokenProvider, unauthorized: UnauthorizedHandler) {
  tokenProvider = provider;
  onUnauthorized = unauthorized;
}

export interface RequestOptions {
  method?: string;
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined | null>;
  signal?: AbortSignal;
}

export function buildUrl(path: string, query?: RequestOptions['query']): string {
  if (!query) return path;
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value));
  }
  const qs = params.toString();
  return qs ? `${path}?${qs}` : path;
}

function headers(json: boolean): HeadersInit {
  const h: Record<string, string> = { Accept: 'application/json' };
  if (json) h['Content-Type'] = 'application/json';
  const token = tokenProvider();
  if (token) h.Authorization = `Bearer ${token}`;
  return h;
}

async function toError(response: Response): Promise<ApiError> {
  let problem: Problem = { status: response.status, title: response.statusText };
  try {
    const text = await response.text();
    if (text) problem = { ...problem, ...JSON.parse(text) };
  } catch {
    // Not JSON (e.g. a proxy error page); keep the status line.
  }
  if (response.status === 401) onUnauthorized();
  return new ApiError(problem);
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const isForm = options.body instanceof FormData;
  const response = await fetch(buildUrl(path, options.query), {
    method: options.method ?? (options.body === undefined ? 'GET' : 'POST'),
    headers: headers(options.body !== undefined && !isForm),
    body: options.body === undefined ? undefined : isForm ? (options.body as FormData) : JSON.stringify(options.body),
    signal: options.signal,
  });
  if (!response.ok) throw await toError(response);
  if (response.status === 204) return undefined as T;
  const type = response.headers.get('Content-Type') ?? '';
  return (type.includes('json') ? response.json() : response.text()) as Promise<T>;
}

export const api = {
  get: <T>(path: string, query?: RequestOptions['query']) => request<T>(path, { query }),
  post: <T>(path: string, body?: unknown, query?: RequestOptions['query']) =>
    request<T>(path, { method: 'POST', body: body ?? {}, query }),
  put: <T>(path: string, body: unknown) => request<T>(path, { method: 'PUT', body }),
  patch: <T>(path: string, body: unknown) => request<T>(path, { method: 'PATCH', body }),
  delete: <T = void>(path: string) => request<T>(path, { method: 'DELETE' }),
  upload: <T>(path: string, file: File, query?: RequestOptions['query']) => {
    const form = new FormData();
    form.append('file', file);
    return request<T>(path, { method: 'POST', body: form, query });
  },
};

/** Downloads an authenticated file (PDF, CSV) and hands it to the browser. */
export async function download(path: string, fallbackName: string): Promise<void> {
  const response = await fetch(path, { headers: headers(false) });
  if (!response.ok) throw await toError(response);
  const blob = await response.blob();
  const disposition = response.headers.get('Content-Disposition') ?? '';
  const match = /filename="?([^";]+)"?/.exec(disposition);
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = match?.[1] ?? fallbackName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}
