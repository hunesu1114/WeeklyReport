export const THIS_WEEK = 'THIS_WEEK'
export const NEXT_WEEK = 'NEXT_WEEK'

let seq = 0
/** v-for key 용 로컬 식별자. 서버 id 와는 별개다. */
export function uid() {
  seq += 1
  return `k${Date.now().toString(36)}${seq}`
}

export function newItem(section) {
  return {
    key: uid(),
    id: null,
    section,
    taskName: '',
    detail: '',
    status: section === THIS_WEEK ? '진행' : '예정',
    hours: null,
  }
}

export function toIso(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

export function todayIso() {
  return toIso(new Date())
}

export function addDays(iso, days) {
  const [y, m, d] = iso.split('-').map(Number)
  const date = new Date(y, m - 1, d)
  date.setDate(date.getDate() + days)
  return toIso(date)
}

/**
 * 보고일 기준 기본 구간. 백엔드 WeekCalculator 와 같은 규칙이다.
 * 금주 = [D-7, D-1], 차주 = [D, D+6]
 */
export function weekDefaults(reportDate) {
  return {
    thisWeekStart: addDays(reportDate, -7),
    thisWeekEnd: addDays(reportDate, -1),
    nextWeekStart: reportDate,
    nextWeekEnd: addDays(reportDate, 6),
  }
}

/** 엑셀에 찍히는 것과 같은 표기: 2026. 05. 08 */
export function formatExcelDate(iso) {
  if (!iso) return ''
  const [y, m, d] = iso.split('-')
  return `${y}. ${m}. ${d}`
}

export function formatRange(startIso, endIso) {
  return `( ${formatExcelDate(startIso)}  ~ ${formatExcelDate(endIso)} )`
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

export function weekdayOf(iso) {
  if (!iso) return ''
  const [y, m, d] = iso.split('-').map(Number)
  return WEEKDAYS[new Date(y, m - 1, d).getDay()]
}

export function daysBetween(startIso, endIso) {
  if (!startIso || !endIso) return 0
  const [y1, m1, d1] = startIso.split('-').map(Number)
  const [y2, m2, d2] = endIso.split('-').map(Number)
  const diff = Date.UTC(y2, m2 - 1, d2) - Date.UTC(y1, m1 - 1, d1)
  return Math.round(diff / 86400000) + 1
}

/** 한글은 두 칸으로 세는 표시 폭. 백엔드 RowHeightEstimator 와 같은 규칙. */
export function displayWidth(line) {
  let width = 0
  for (const ch of line) {
    const code = ch.codePointAt(0)
    const wide =
      (code >= 0x1100 && code <= 0x115f) ||
      (code >= 0x2e80 && code <= 0xa4cf) ||
      (code >= 0xac00 && code <= 0xd7a3) ||
      (code >= 0xf900 && code <= 0xfaff) ||
      (code >= 0xfe30 && code <= 0xfe6f) ||
      (code >= 0xff00 && code <= 0xff60) ||
      (code >= 0xffe0 && code <= 0xffe6)
    width += wide ? 2 : 1
  }
  return width
}

export function lineCount(text, columnWidthChars) {
  if (!text) return 1
  const usable = Math.max(1, columnWidthChars - 1)
  let total = 0
  for (const line of String(text).split('\n')) {
    total += Math.max(1, Math.ceil(displayWidth(line) / usable))
  }
  return Math.max(1, total)
}

/** 업무상세 열(D)의 실제 너비. 엑셀에서 몇 줄이 될지 미리 보여주는 데 쓴다. */
export const DETAIL_COLUMN_WIDTH = 84.75

export function sumHours(items) {
  return items.reduce((total, item) => {
    const value = Number(item.hours)
    return Number.isFinite(value) ? total + value : total
  }, 0)
}

export function roundHours(value) {
  return Math.round(value * 100) / 100
}

/** 서버 저장 페이로드로 변환. 순서는 배열 순서를 그대로 따른다. */
export function toSavePayload(form, thisWeekItems, nextWeekItems) {
  const items = [
    ...thisWeekItems.map((item) => ({
      id: item.id,
      section: THIS_WEEK,
      taskName: item.taskName || null,
      detail: item.detail || null,
      status: item.status || null,
      hours: item.hours === '' || item.hours === null ? null : Number(item.hours),
    })),
    ...nextWeekItems.map((item) => ({
      id: item.id,
      section: NEXT_WEEK,
      taskName: item.taskName || null,
      detail: item.detail || null,
      status: item.status || null,
      hours: null,
    })),
  ]
  return {
    reportDate: form.reportDate,
    authorName: form.authorName,
    titleOverride: form.titleOverride || null,
    thisWeekStart: form.thisWeekStart,
    thisWeekEnd: form.thisWeekEnd,
    nextWeekStart: form.nextWeekStart,
    nextWeekEnd: form.nextWeekEnd,
    baseHours: Number(form.baseHours) || 0,
    note: form.note || null,
    templateKey: form.templateKey,
    items,
  }
}

export function fromServerItem(item) {
  return {
    key: uid(),
    id: item.id ?? null,
    section: item.section,
    taskName: item.taskName ?? '',
    detail: item.detail ?? '',
    status: item.status ?? '',
    hours: item.hours ?? null,
  }
}
