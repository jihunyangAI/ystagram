// 백엔드 API 호출 도우미.
// - 세션 쿠키로 로그인 상태를 유지한다 (같은 주소라 credentials 기본값으로 충분)
// - POST/PUT/PATCH/DELETE에는 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 보낸다 (CSRF 보호)

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: Record<string, string>

  constructor(status: number, code: string, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

function readCookie(name: string): string | null {
  const match = document.cookie.split('; ').find((c) => c.startsWith(name + '='))
  return match ? decodeURIComponent(match.split('=')[1]) : null
}

let csrfLoading: Promise<void> | null = null

/** CSRF 쿠키가 없으면 받아 온다. */
async function ensureCsrf(force = false): Promise<void> {
  if (!force && readCookie('XSRF-TOKEN')) return
  if (!csrfLoading) {
    csrfLoading = fetch('/api/auth/csrf').then(() => undefined).finally(() => {
      csrfLoading = null
    })
  }
  await csrfLoading
}

async function toError(res: Response): Promise<ApiError> {
  try {
    const body = await res.json()
    return new ApiError(res.status, body.code ?? `HTTP_${res.status}`, body.message ?? '요청에 실패했습니다.', body.fieldErrors ?? {})
  } catch {
    const message =
      res.status === 401 ? '로그인이 필요합니다.' : res.status === 403 ? '권한이 없습니다.' : '서버에 연결할 수 없습니다.'
    return new ApiError(res.status, `HTTP_${res.status}`, message)
  }
}

async function request<T>(method: string, url: string, body?: unknown, retried = false): Promise<T> {
  const unsafe = method !== 'GET'
  if (unsafe) await ensureCsrf()

  const form = body instanceof FormData
  const headers: Record<string, string> = { Accept: 'application/json' }
  // FormData(파일 업로드)는 브라우저가 multipart 경계값을 붙여 주므로 Content-Type을 직접 쓰지 않는다
  if (body !== undefined && !form) headers['Content-Type'] = 'application/json'
  if (unsafe) headers['X-XSRF-TOKEN'] = readCookie('XSRF-TOKEN') ?? ''

  const res = await fetch(url, {
    method,
    headers,
    body: body === undefined ? undefined : form ? body : JSON.stringify(body),
  })

  // 로그아웃·로그인 뒤에는 CSRF 토큰이 바뀔 수 있다 → 새로 받아 한 번만 다시 시도
  if (res.status === 403 && unsafe && !retried) {
    await ensureCsrf(true)
    return request<T>(method, url, body, true)
  }
  if (!res.ok) throw await toError(res)
  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const api = {
  get: <T>(url: string) => request<T>('GET', url),
  post: <T>(url: string, body?: unknown) => request<T>('POST', url, body),
  put: <T>(url: string, body?: unknown) => request<T>('PUT', url, body),
  patch: <T>(url: string, body?: unknown) => request<T>('PATCH', url, body),
  delete: <T>(url: string) => request<T>('DELETE', url),
  /** multipart 업로드 (FormData) */
  upload: <T>(method: 'POST' | 'PUT', url: string, form: FormData) => request<T>(method, url, form),
}
