/** 참여자 역할. 서버는 이름만 주고 문구는 화면이 붙인다. */
export const ROLES = ['OWNER', 'MEMBER', 'VIEWER']

export const ROLE_META = {
  OWNER: { label: '관리자', hint: '참여자 관리 · 보드 설정 · 삭제', tone: 'ok' },
  MEMBER: { label: '멤버', hint: '카드를 만들고 고치고 옮김', tone: 'info' },
  VIEWER: { label: '읽기 전용', hint: '보기만 가능', tone: 'neutral' },
}

export function canWrite(role) {
  return role === 'OWNER' || role === 'MEMBER'
}

export function isOwner(role) {
  return role === 'OWNER'
}

/** 활동 기록 한 줄을 사람이 읽는 문장으로 바꾼다. */
export function activityText(log) {
  const who = log.actor?.displayName ?? '알 수 없는 사용자'
  const card = log.cardTitle ? `'${log.cardTitle}'` : '카드'
  const target = log.targetName ?? ''

  switch (log.type) {
    case 'PROJECT_CREATED':
      return `${who} 님이 보드를 만들었습니다`
    case 'PROJECT_UPDATED':
      return `${who} 님이 보드 설정을 바꿨습니다`
    case 'MEMBER_INVITED':
      return `${who} 님이 ${target} 님을 초대했습니다`
    case 'MEMBER_JOINED':
      return `${target || who} 님이 참여했습니다`
    case 'MEMBER_LEFT':
      return `${target || who} 님이 나갔습니다`
    case 'MEMBER_REMOVED':
      return `${who} 님이 ${target} 님을 내보냈습니다`
    case 'MEMBER_ROLE_CHANGED':
      return `${who} 님이 ${target} 님의 역할을 바꿨습니다`
    case 'CARD_CREATED':
      return `${who} 님이 ${card} 카드를 추가했습니다`
    case 'CARD_UPDATED':
      return `${who} 님이 ${card} 카드를 수정했습니다`
    case 'CARD_MOVED':
      return `${who} 님이 ${card} 카드를 ${log.fromStatus} → ${log.toStatus} 로 옮겼습니다`
    case 'CARD_ASSIGNED':
      return target
        ? `${who} 님이 ${card} 카드를 ${target} 님에게 맡겼습니다`
        : `${who} 님이 ${card} 카드의 담당자를 비웠습니다`
    case 'CARD_DELETED':
      return `${who} 님이 ${card} 카드를 지웠습니다`
    default:
      return `${who} 님의 활동`
  }
}

/** 활동 종류별 색. 지운 것은 빨강, 완료로 옮긴 것은 초록. */
export function activityTone(log) {
  if (log.type === 'CARD_DELETED' || log.type === 'MEMBER_REMOVED') return 'danger'
  if (log.type === 'MEMBER_JOINED') return 'ok'
  if (log.type === 'CARD_MOVED') return log.toStatus === 'DONE' ? 'ok' : 'info'
  if (log.type === 'CARD_CREATED') return 'info'
  return 'neutral'
}

/**
 * "3분 전" 같은 상대 시각. 하루가 넘으면 날짜를 그대로 보여준다 —
 * "8일 전"보다 "9월 6일"이 훨씬 빨리 읽힌다.
 */
export function timeAgo(iso) {
  if (!iso) return ''
  const then = new Date(iso)
  if (Number.isNaN(then.getTime())) return ''

  const seconds = Math.floor((Date.now() - then.getTime()) / 1000)
  if (seconds < 60) return '방금'
  if (seconds < 3600) return `${Math.floor(seconds / 60)}분 전`
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}시간 전`
  if (seconds < 604800) return `${Math.floor(seconds / 86400)}일 전`

  const sameYear = then.getFullYear() === new Date().getFullYear()
  const date = `${then.getMonth() + 1}월 ${then.getDate()}일`
  return sameYear ? date : `${then.getFullYear()}년 ${date}`
}
