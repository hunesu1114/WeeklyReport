import axios from 'axios'
import { isExpired } from '@/utils/jwt'

const http = axios.create({
  baseURL: '/api',
  timeout: 20000,
})

/**
 * 로그인 토큰. 스토어가 값을 넣어주고, 여기서는 헤더에 싣기만 한다.
 * 스토어를 직접 import 하면 순환 참조가 생긴다.
 */
let authToken = null

export function setAuthToken(token) {
  authToken = token || null
}

/** 세션이 끝났을 때 화면이 할 일. main.js 가 콜백을 심는다. */
let onUnauthorized = null

export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler
}

/**
 * 토큰 없이 부르는 경로. 만료 검사와 자동 로그아웃에서 뺀다.
 *
 * '/auth/' 로 뭉뚱그리면 안 된다. /auth/me, /auth/orphans 처럼 로그인이
 * 필요한 것까지 빠져서, 정작 세션이 끝났을 때 아무 안내도 못 하게 된다.
 */
const PUBLIC_PATHS = ['/auth/login', '/auth/register', '/auth/setup-state']

function isPublicCall(url) {
  return Boolean(url && PUBLIC_PATHS.some((path) => url.startsWith(path)))
}

export const SESSION_EXPIRED_MESSAGE = '로그인 세션이 만료되었습니다. 다시 로그인해 주세요.'

http.interceptors.request.use((config) => {
  if (!authToken) return config

  // 이미 지난 토큰이면 서버에 물어볼 것도 없다. 보내지 않고 바로 끊는다.
  // 그래야 사용자가 버튼을 누른 즉시 안내가 뜬다.
  if (!isPublicCall(config.url) && isExpired(authToken)) {
    onUnauthorized?.()
    const error = new Error(SESSION_EXPIRED_MESSAGE)
    error.sessionExpired = true
    return Promise.reject(error)
  }

  config.headers.Authorization = `Bearer ${authToken}`
  return config
})

/** 서버가 내려주는 message 를 사람이 읽을 수 있는 에러로 바꾼다. */
http.interceptors.response.use(
  (response) => response,
  (error) => {
    // 요청 인터셉터에서 이미 처리해 끊은 것. 그대로 올려보낸다.
    if (error.sessionExpired) return Promise.reject(error)

    const status = error.response?.status
    const data = error.response?.data

    // 서버가 토큰을 거절했다. 로그인 시도 자체가 실패한 경우는 제외한다.
    if (status === 401 && !isPublicCall(error.config?.url)) {
      onUnauthorized?.()
      const expired = new Error(SESSION_EXPIRED_MESSAGE)
      expired.sessionExpired = true
      return Promise.reject(expired)
    }

    let message = data?.message || error.message || '알 수 없는 오류가 발생했습니다.'
    if (data?.fields) {
      message += ' (' + Object.entries(data.fields).map(([k, v]) => `${k}: ${v}`).join(', ') + ')'
    }
    return Promise.reject(new Error(message))
  },
)

export const authApi = {
  setupState: () => http.get('/auth/setup-state').then((r) => r.data),
  register: (payload) => http.post('/auth/register', payload).then((r) => r.data),
  login: (payload) => http.post('/auth/login', payload).then((r) => r.data),
  me: () => http.get('/auth/me').then((r) => r.data),
  changePassword: (payload) => http.post('/auth/password', payload),

  /** 주인 없는 데이터 현황 */
  orphans: () => http.get('/auth/orphans').then((r) => r.data),
  /** 주인 없는 데이터를 지금 계정으로 가져오기 (관리자만) */
  claimOrphans: () => http.post('/auth/orphans/claim').then((r) => r.data),
}

export const reportApi = {
  list: (params) => http.get('/reports', { params }).then((r) => r.data),
  defaults: (params) => http.get('/reports/defaults', { params }).then((r) => r.data),
  get: (id) => http.get(`/reports/${id}`).then((r) => r.data),
  create: (payload) => http.post('/reports', payload).then((r) => r.data),
  update: (id, payload) => http.put(`/reports/${id}`, payload).then((r) => r.data),
  remove: (id) => http.delete(`/reports/${id}`),
  followUp: (id) => http.post(`/reports/${id}/follow-up`).then((r) => r.data),

  /** 엑셀을 내려받는다. 파일명은 Content-Disposition 에서 뽑아 쓴다. */
  async download(id, templateKey) {
    const response = await http.get(`/reports/${id}/export`, {
      params: templateKey ? { templateKey } : undefined,
      responseType: 'blob',
    })
    const filename =
      parseFilename(response.headers['content-disposition']) || `weekly-report-${id}.xlsx`
    saveBlob(response.data, filename)
    return filename
  },
}

export const kanbanApi = {
  projects: (activeOnly = false) =>
    http.get('/kanban/projects', { params: { activeOnly } }).then((r) => r.data),
  createProject: (payload) => http.post('/kanban/projects', payload).then((r) => r.data),
  updateProject: (id, payload) => http.put(`/kanban/projects/${id}`, payload).then((r) => r.data),
  removeProject: (id) => http.delete(`/kanban/projects/${id}`),

  board: (projectId) => http.get(`/kanban/projects/${projectId}/board`).then((r) => r.data),

  createCard: (payload) => http.post('/kanban/cards', payload).then((r) => r.data),
  updateCard: (id, payload) => http.put(`/kanban/cards/${id}`, payload).then((r) => r.data),
  moveCard: (id, payload) => http.put(`/kanban/cards/${id}/move`, payload).then((r) => r.data),
  removeCard: (id) => http.delete(`/kanban/cards/${id}`),

  /** 완료일이 임박한 카드 (기본 3일) */
  dueSoon: (days) => http.get('/kanban/cards/due-soon', { params: { days } }).then((r) => r.data),

  /** 시작일이 기간 안에 있는 카드 — 주간보고 연동 */
  startedBetween: (from, to, projectId) =>
    http
      .get('/kanban/cards/started-between', { params: { from, to, projectId } })
      .then((r) => r.data),
}

export const metaApi = {
  templates: () => http.get('/meta/templates').then((r) => r.data),
  statuses: () => http.get('/meta/statuses').then((r) => r.data),
  taskNames: (query) => http.get('/meta/task-names', { params: { query } }).then((r) => r.data),
  authors: () => http.get('/meta/authors').then((r) => r.data),
}

function parseFilename(disposition) {
  if (!disposition) return null
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  if (utf8) return decodeURIComponent(utf8[1])
  const plain = /filename="?([^";]+)"?/i.exec(disposition)
  return plain ? plain[1] : null
}

function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
