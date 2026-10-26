import { serviceErrorMessages } from './serviceErrorMessages.ts'

const statusMessages: Record<number, string> = {
  400: '입력 내용을 확인해 주세요.', 401: '인증 정보를 확인하고 다시 접속해 주세요.',
  403: '이 작업을 수행할 권한이 없습니다.', 404: '요청한 정보를 찾을 수 없습니다.',
  405: '지원하지 않는 요청입니다.', 409: '현재 작업 상태를 확인한 뒤 다시 시도해 주세요.',
  413: '파일 크기가 업로드 허용 용량을 초과했습니다. 파일 크기를 줄여 주세요.',
  415: '지원하지 않는 요청 형식입니다.', 429: '요청이 많습니다. 잠시 후 다시 시도해 주세요.',
  422: '자료의 내용이나 크기를 확인해 주세요.',
  502: '처리 서비스에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.',
  503: '현재 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해 주세요.',
  504: '응답 대기 시간이 초과되었습니다. 처리 상태를 확인한 뒤 다시 시도해 주세요.',
}
export class ApiError extends Error {
  code: string
  status?: number
  traceId?: string
  constructor(code: string, message: string, status?: number, traceId?: string) { super(message); this.name = 'ApiError'; this.code = code; this.status = status; this.traceId = traceId }
}
export function responseError(status: number, body: unknown): ApiError {
  const value = body && typeof body === 'object' ? body as Record<string, unknown> : {}
  const code = typeof value.code === 'string' && /^[A-Z][A-Z0-9_]{0,99}$/.test(value.code) ? value.code : `HTTP_${status}`
  const trace = typeof value.traceId === 'string' && /^[0-9a-f-]{36}$/i.test(value.traceId) ? value.traceId : undefined
  // Never show arbitrary backend exception text or HTML (including proxy error pages).
  const message = serviceErrorMessages[code] || statusMessages[status] || '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
  return new ApiError(code, message, status, trace)
}
export async function apiFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  let response: Response
  try { response = await globalThis.fetch(input, init) }
  catch (error) {
    if (error instanceof Error && error.name === 'AbortError') throw error
    if (error instanceof Error && error.name === 'TimeoutError') throw new ApiError('NETWORK_TIMEOUT', '응답 대기 시간이 초과되었습니다. 처리 상태를 확인한 뒤 다시 시도해 주세요.')
    throw new ApiError('NETWORK_UNAVAILABLE', '서버에 연결할 수 없습니다. 서버 실행 상태 또는 네트워크를 확인해 주세요.')
  }
  if (!response.ok) {
    let body: unknown
    try { body = await response.json() } catch { body = undefined }
    throw responseError(response.status, body)
  }
  return response
}
