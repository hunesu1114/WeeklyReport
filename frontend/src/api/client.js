import axios from 'axios'

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

/** 토큰이 만료되면 화면이 로그인으로 돌아가야 한다. main.js 가 콜백을 심는다. */
let onUnauthorized = null

export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler
}

http.interceptors.request.use((config) => {
  if (authToken) {
    config.headers.Authorization = `Bearer ${authToken}`
  }
  return config
})

/** 서버가 내려주는 message 를 사람이 읽을 수 있는 에러로 바꾼다. */
http.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    const data = error.response?.data

    // 토큰이 없거나 만료됐다. 로그인 시도 자체가 실패한 경우는 제외한다.
    if (status === 401 && !error.config?.url?.includes('/auth/')) {
      onUnauthorized?.()
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
