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

    const wrapped = new Error(message)
    wrapped.status = status
    // 남이 먼저 고쳐서 거절당했다. 서버가 함께 준 '지금 값'을 잃지 않고 올려보낸다.
    // 이게 있어야 화면이 내 것과 남의 것을 나란히 보여줄 수 있다.
    if (status === 409 && data?.current) wrapped.conflict = data.current
    return Promise.reject(wrapped)
  },
)

export const authApi = {
  setupState: () => http.get('/auth/setup-state').then((r) => r.data),
  register: (payload) => http.post('/auth/register', payload).then((r) => r.data),
  login: (payload) => http.post('/auth/login', payload).then((r) => r.data),
  me: () => http.get('/auth/me').then((r) => r.data),
  /** 세션 연장. 아직 살아 있는 토큰으로만 통한다. */
  refresh: () => http.post('/auth/refresh').then((r) => r.data),
  changePassword: (payload) => http.post('/auth/password', payload),

  /** 주인 없는 데이터 현황 */
  orphans: () => http.get('/auth/orphans').then((r) => r.data),
  /** 주인 없는 데이터를 지금 계정으로 가져오기 (관리자만) */
  claimOrphans: () => http.post('/auth/orphans/claim').then((r) => r.data),
}

export const profileApi = {
  update: (payload) => http.put('/profile', payload).then((r) => r.data),

  uploadAvatar(file) {
    const body = new FormData()
    body.append('file', file)
    // Content-Type 을 직접 쓰면 boundary 가 빠진다. axios 가 정하게 둔다.
    return http.post('/profile/avatar', body).then((r) => r.data)
  },

  removeAvatar: () => http.delete('/profile/avatar').then((r) => r.data),
}

/**
 * 프로필 사진 주소. 없으면 null 을 주고 화면은 이니셜로 그린다.
 *
 * 사진이 바뀌면 avatarVersion 이 올라 주소가 달라진다. 그래서 서버가 1년 캐시를
 * 걸어도 바뀐 사진이 바로 보인다.
 */
export function avatarUrl(user) {
  // 참여자 목록은 userId, 그 밖에는 id 로 내려온다. 둘 다 받는다.
  const id = user?.id ?? user?.userId
  if (!id || !user.hasAvatar) return null
  return `/api/users/${id}/avatar?v=${user.avatarVersion ?? 0}`
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

  /** 보드의 모든 카드를 엑셀 한 장으로 내려받는다. 파일명은 서버가 정한다. */
  async exportBoard(projectId) {
    const response = await http.get(`/kanban/projects/${projectId}/export`, {
      responseType: 'blob',
    })
    const filename =
      parseFilename(response.headers['content-disposition']) || `kanban-${projectId}.xlsx`
    saveBlob(response.data, filename)
    return filename
  },

  createCard: (payload) => http.post('/kanban/cards', payload).then((r) => r.data),
  updateCard: (id, payload) => http.put(`/kanban/cards/${id}`, payload).then((r) => r.data),
  moveCard: (id, payload) => http.put(`/kanban/cards/${id}/move`, payload).then((r) => r.data),
  removeCard: (id) => http.delete(`/kanban/cards/${id}`),

  /**
   * 완료일이 임박한 카드 (기본 3일).
   * scope='team' 이면 내가 속한 보드 전체, 아니면 내가 담당한 것만.
   */
  dueSoon: (days, scope) =>
    http.get('/kanban/cards/due-soon', { params: { days, scope } }).then((r) => r.data),

  /** 시작일이 기간 안에 있는 카드 — 주간보고 연동 */
  startedBetween: (from, to, projectId, mineOnly = true) =>
    http
      .get('/kanban/cards/started-between', { params: { from, to, projectId, mineOnly } })
      .then((r) => r.data),
}

/** 참여자 · 초대 · 알림 · 활동 기록. */
export const teamApi = {
  members: (projectId) => http.get(`/kanban/projects/${projectId}/members`).then((r) => r.data),
  changeRole: (projectId, userId, role) =>
    http.patch(`/kanban/projects/${projectId}/members/${userId}`, { role }).then((r) => r.data),
  /** 본인이면 '나가기', 남이면 '내보내기'. 서버가 구분한다. */
  removeMember: (projectId, userId) =>
    http.delete(`/kanban/projects/${projectId}/members/${userId}`),

  invite: (projectId, userId, role) =>
    http.post(`/kanban/projects/${projectId}/invitations`, { userId, role }).then((r) => r.data),
  pendingInvitations: (projectId) =>
    http.get(`/kanban/projects/${projectId}/invitations`).then((r) => r.data),
  cancelInvitation: (projectId, invitationId) =>
    http.delete(`/kanban/projects/${projectId}/invitations/${invitationId}`),

  myInvitations: () => http.get('/kanban/invitations').then((r) => r.data),
  accept: (id) => http.post(`/kanban/invitations/${id}/accept`).then((r) => r.data),
  decline: (id) => http.post(`/kanban/invitations/${id}/decline`),

  searchUsers: (query, projectId) =>
    http.get('/meta/users', { params: { query, projectId } }).then((r) => r.data),

  inbox: (limit) => http.get('/kanban/notifications', { params: { limit } }).then((r) => r.data),
  markAllRead: () => http.post('/kanban/notifications/read-all'),
  markRead: (id) => http.post(`/kanban/notifications/${id}/read`),

  projectActivities: (projectId, page = 0, size = 30) =>
    http
      .get(`/kanban/projects/${projectId}/activities`, { params: { page, size } })
      .then((r) => r.data),
  cardActivities: (projectId, cardId, page = 0, size = 20) =>
    http
      .get(`/kanban/projects/${projectId}/cards/${cardId}/activities`, { params: { page, size } })
      .then((r) => r.data),
  myActivities: (page = 0, size = 30) =>
    http.get('/kanban/activities', { params: { page, size } }).then((r) => r.data),
}

/** 메모장. 개인 것이라 별도의 권한 개념이 없다. */
export const memoApi = {
  /** 폴더 트리와 메모 목록을 한 번에 */
  workspace: () => http.get('/memos').then((r) => r.data),

  get: (id) => http.get(`/memos/${id}`).then((r) => r.data),
  create: (payload) => http.post('/memos', payload).then((r) => r.data),
  update: (id, payload) => http.put(`/memos/${id}`, payload).then((r) => r.data),
  move: (id, folderId) => http.put(`/memos/${id}/move`, { folderId }).then((r) => r.data),
  remove: (id) => http.delete(`/memos/${id}`),

  /* 깃발은 본문 저장과 따로 보낸다. 자동 저장이 끼어들어 방금 누른 것을 되돌리지 않게. */
  setPinned: (id, value) => http.put(`/memos/${id}/pin`, { value }).then((r) => r.data),
  setFavorite: (id, value) => http.put(`/memos/${id}/favorite`, { value }).then((r) => r.data),

  createFolder: (name, parentId) =>
    http.post('/memos/folders', { name, parentId }).then((r) => r.data),
  renameFolder: (id, name) => http.put(`/memos/folders/${id}`, { name }).then((r) => r.data),
  moveFolder: (id, parentId) =>
    http.put(`/memos/folders/${id}/move`, { parentId }).then((r) => r.data),
  removeFolder: (id) => http.delete(`/memos/folders/${id}`),

  /** txt 로 내려받는다. 줄 끝은 서버가 CRLF 로 바꿔 보낸다. */
  async exportText(id) {
    const response = await http.get(`/memos/${id}/export`, { responseType: 'blob' })
    const filename = parseFilename(response.headers['content-disposition']) || `memo-${id}.txt`
    saveBlob(response.data, filename)
    return filename
  },
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
