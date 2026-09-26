/**
 * API 단일 창구.
 *
 * 🔴 왜 만들었나 (2026-09-26)
 * 종전 프론트는 `fetch()` 를 27곳에서 직접 불렀다. 그때마다
 *   headers: { 'X-User-Id': userId }
 * 를 손으로 붙였고(17번 중복), 에러 처리는 곳마다 달랐다 — 어떤 곳은 throw,
 * 어떤 곳은 console.error 로 삼키고, 어떤 곳은 아무것도 안 했다.
 * `api.js` 에 fetchApi/postApi/putApi/deleteApi 가 이미 있었는데 **쓰는 파일이 0개**였다.
 *
 * 규칙: 네트워크 호출은 전부 이 파일을 지난다. 여기 없는 기능이 필요하면 여기에 추가한다.
 */

/** 서버가 발급/기억하는 사용자 식별자. 헤더에 실어 보낸다. */
const USER_ID_KEY = 'userId'

function readUserId(): string {
  try {
    let id = localStorage.getItem(USER_ID_KEY)
    if (!id) {
      id = crypto.randomUUID()
      localStorage.setItem(USER_ID_KEY, id)
    }
    return id
  } catch {
    // 사생활 보호 모드 등에서 localStorage 가 막히면 세션 한정 임시 id 로 동작한다.
    return 'anonymous'
  }
}

export const userId = readUserId()

/**
 * 모든 경로는 `/myapi/` 기준 상대경로로 적는다(`api/dashboard/data`).
 * 게이트웨이 뒤 서브패스라 절대경로를 박으면 포트·스킴에 묶인다.
 */
const BASE = 'api/'

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly path: string
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

interface RequestOptions {
  /** 실패해도 조용히 넘어갈지. 기본은 throw. */
  readonly silent?: boolean
  readonly signal?: AbortSignal
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  options: RequestOptions = {}
): Promise<T> {
  const url = path.startsWith('api/') ? path : BASE + path.replace(/^\/+/, '')

  const headers: Record<string, string> = { 'X-User-Id': userId }
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  const response = await fetch(url, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: options.signal
  })

  if (!response.ok) {
    throw new ApiError(`${method} ${url} 실패`, response.status, url)
  }

  // 204 No Content 등 본문이 없는 응답을 JSON 으로 파싱하면 터진다.
  if (response.status === 204) return undefined as T
  const text = await response.text()
  if (!text) return undefined as T
  return JSON.parse(text) as T
}

export const api = {
  get: <T>(path: string, options?: RequestOptions) => request<T>('GET', path, undefined, options),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>('POST', path, body, options),
  put: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>('PUT', path, body, options),
  patch: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>('PATCH', path, body, options),
  delete: <T>(path: string, options?: RequestOptions) =>
    request<T>('DELETE', path, undefined, options)
}

/**
 * 실패를 삼키고 기본값을 돌려준다 — 대시보드 위젯처럼 "하나 실패해도 화면 전체가
 * 죽으면 안 되는" 자리에서만 쓴다. 어떤 것이 실패했는지는 콘솔에 남긴다.
 */
export async function tryGet<T>(path: string, fallback: T): Promise<T> {
  try {
    return await api.get<T>(path)
  } catch (error) {
    console.warn(`[api] ${path} 실패 — 기본값으로 진행합니다`, error)
    return fallback
  }
}
