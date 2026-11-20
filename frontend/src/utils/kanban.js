export const STATUSES = ['BACKLOG', 'TODO', 'ING', 'DONE']

/*
 * label 은 서버와 엑셀이 쓰는 이름이라 그대로 두고, 보드 칸 머리에는 name 을 크게 쓴다.
 * 칸마다 색 점을 찍지 않는다 — 진행 중 하나만 키컬러 선으로 짚는다.
 */
export const STATUS_META = {
  BACKLOG: { label: 'BACKLOG', name: '쌓아 둔 일', hint: '언젠가 할 일', tone: 'neutral' },
  TODO: { label: 'TODO', name: '할 일', hint: '이번에 할 일', tone: 'info' },
  ING: { label: 'ING', name: '진행 중', hint: '진행 중', tone: 'violet' },
  DONE: { label: 'DONE', name: '완료', hint: '완료', tone: 'ok' },
}

export const PRIORITIES = ['LOW', 'NORMAL', 'HIGH', 'URGENT']

/* level 은 카드에 그리는 막대 수(1~3). 색은 높음부터만 쓴다 */
export const PRIORITY_META = {
  LOW: { label: '낮음', tone: 'neutral', level: 1 },
  NORMAL: { label: '보통', tone: 'info', level: 2 },
  HIGH: { label: '높음', tone: 'warn', level: 3 },
  URGENT: { label: '긴급', tone: 'danger', level: 3 },
}

/** 'D-2' · 'D-day' · 'D+3'. 카드처럼 좁은 곳에 쓴다. */
export function dueShort(daysUntilDue) {
  if (daysUntilDue === null || daysUntilDue === undefined) return ''
  if (daysUntilDue === 0) return 'D-day'
  return daysUntilDue > 0 ? `D-${daysUntilDue}` : `D+${Math.abs(daysUntilDue)}`
}

/** '2026-11-22' → '11.22' */
export function shortDate(iso) {
  if (!iso) return ''
  const [, m, d] = String(iso).split('-')
  return m && d ? `${Number(m)}.${d}` : String(iso)
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

export function emptyCard(projectId, status = 'BACKLOG', assigneeId = null) {
  return {
    id: null,
    projectId,
    status,
    title: '',
    content: '',
    priority: 'NORMAL',
    startDate: todayIso(),
    dueDate: null,
    assigneeId,
    version: null,
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
    assigneeId: card.assignee?.id ?? null,
    // 이 카드를 읽었을 때의 버전. 저장할 때 그대로 돌려보내
    // 그 사이 남이 고쳤는지 서버가 판단하게 한다.
    version: card.version ?? null,
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
    assigneeId: form.assigneeId ?? null,
    // 새 카드에는 비교할 버전이 없다. 보내면 서버가 괜히 충돌로 본다.
    version: form.id ? form.version ?? null : null,
  }
}
