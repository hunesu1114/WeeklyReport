export const STATUSES = ['BACKLOG', 'TODO', 'ING', 'DONE']

export const STATUS_META = {
  BACKLOG: { label: 'BACKLOG', hint: '언젠가 할 일', tone: 'neutral' },
  TODO: { label: 'TODO', hint: '이번에 할 일', tone: 'info' },
  ING: { label: 'ING', hint: '진행 중', tone: 'violet' },
  DONE: { label: 'DONE', hint: '완료', tone: 'ok' },
}

export const PRIORITIES = ['LOW', 'NORMAL', 'HIGH', 'URGENT']

export const PRIORITY_META = {
  LOW: { label: '낮음', tone: 'neutral' },
  NORMAL: { label: '보통', tone: 'info' },
  HIGH: { label: '높음', tone: 'warn' },
  URGENT: { label: '긴급', tone: 'danger' },
}

/** 완료일까지 남은 날을 사람이 읽는 말로. */
export function dueLabel(daysUntilDue) {
  if (daysUntilDue === null || daysUntilDue === undefined) return ''
  if (daysUntilDue < 0) return `${Math.abs(daysUntilDue)}일 지남`
  if (daysUntilDue === 0) return '오늘까지'
  if (daysUntilDue === 1) return '내일까지'
  return `${daysUntilDue}일 남음`
}

/** 임박 정도에 따른 배지 색. 지났거나 오늘이면 빨강. */
export function dueTone(daysUntilDue) {
  if (daysUntilDue === null || daysUntilDue === undefined) return 'neutral'
  if (daysUntilDue <= 0) return 'danger'
  if (daysUntilDue <= 3) return 'warn'
  return 'neutral'
}

export function todayIso() {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

export function emptyCard(projectId, status = 'BACKLOG') {
  return {
    id: null,
    projectId,
    status,
    title: '',
    content: '',
    priority: 'NORMAL',
    startDate: todayIso(),
    dueDate: null,
  }
}

/** 서버 카드 -> 편집 폼. null 을 빈 문자열로 눌러 input 이 경고를 내지 않게 한다. */
export function toForm(card) {
  return {
    id: card.id,
    projectId: card.projectId,
    status: card.status,
    title: card.title ?? '',
    content: card.content ?? '',
    priority: card.priority ?? 'NORMAL',
    startDate: card.startDate ?? '',
    dueDate: card.dueDate ?? '',
  }
}

/** 편집 폼 -> 서버 페이로드. 빈 문자열은 null 로 되돌린다. */
export function toPayload(form) {
  return {
    projectId: form.projectId,
    status: form.status,
    title: form.title?.trim() ?? '',
    content: form.content?.trim() || null,
    priority: form.priority,
    startDate: form.startDate || null,
    dueDate: form.dueDate || null,
  }
}
