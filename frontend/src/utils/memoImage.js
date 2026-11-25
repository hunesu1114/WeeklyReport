/*
 * 본문 속 그림 토큰 ![설명](img:아이디).
 *
 * 서버의 MemoImageTokens 와 같은 규칙이다. 설명에 ] 와 줄 바꿈은 들어갈 수 없고,
 * 아이디는 영문·숫자 10자다. 모양이 어긋나면 토큰이 아니라 그냥 글로 본다.
 */

const TOKEN_SOURCE = String.raw`!\[([^\]\n]*)\]\(img:([A-Za-z0-9]{10})\)`

/** 한 장이 몇 장까지인지, 얼마나 큰지 등 서버와 맞춰 둔 한도. 서버가 최종으로 한 번 더 본다. */
export const IMAGE_LIMITS = {
  perMemo: 50,
  bytes: 5 * 1024 * 1024,
  /** 줄이기 전 원본 상한. 이보다 크면 브라우저가 풀다가 멈출 수 있다. */
  sourceBytes: 40 * 1024 * 1024,
}

export const IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/webp', 'image/gif']

const ID_CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'

/**
 * 그림 아이디. 붙여넣는 순간 최종 토큰을 넣고 올리기는 뒤에서 하므로 브라우저가 정한다.
 * 62¹⁰ 가지라 겹칠 일은 사실상 없고, 겹치면 서버가 거절한다.
 */
export function newImageId() {
  const bytes = new Uint8Array(10)
  crypto.getRandomValues(bytes)
  // 248 = 62 × 4. 그 위 값을 버려야 앞쪽 글자가 더 자주 나오지 않는다
  let out = ''
  while (out.length < 10) {
    for (const b of bytes) {
      if (b < 248 && out.length < 10) out += ID_CHARS[b % 62]
    }
    crypto.getRandomValues(bytes)
  }
  return out
}

/** 설명에 쓸 수 없는 글자(], 줄 바꿈)를 걷어 낸다. */
export function cleanAlt(alt) {
  return String(alt ?? '')
    .replace(/[\]\r\n]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 80)
}

export function tokenOf(alt, id) {
  return `![${cleanAlt(alt)}](img:${id})`
}

/** 본문의 토큰 전부. 같은 그림이 두 번 나오면 두 번 다 준다. [{ id, alt, start, end }] */
export function findTokens(text) {
  const source = text ?? ''
  if (!source.includes('(img:')) return []
  const re = new RegExp(TOKEN_SOURCE, 'g')
  const out = []
  for (let m = re.exec(source); m; m = re.exec(source)) {
    out.push({ id: m[2], alt: m[1].trim(), start: m.index, end: m.index + m[0].length })
  }
  return out
}

/** 그림 줄에 놓을 것. 본문에 처음 나온 순서대로, 같은 그림은 한 번만. */
export function uniqueTokens(text) {
  const seen = new Set()
  return findTokens(text).filter((t) => (seen.has(t.id) ? false : seen.add(t.id)))
}

export function imageCount(text) {
  return uniqueTokens(text).length
}

/** 토큰을 [그림: 설명] 으로. 서버가 목록 미리보기와 검색 토막에 쓰는 것과 같은 모양이다. */
export function toPlain(text) {
  if (!text || !text.includes('(img:')) return text
  return text.replace(new RegExp(TOKEN_SOURCE, 'g'), (_, alt) => (alt.trim() ? `[그림: ${alt.trim()}]` : '[그림]'))
}

/** 미리보기 글에서 [그림: …] 자리를 갈라 꼬리표로 그릴 수 있게 한다. [{ text, image }] */
export function splitImageTags(text) {
  const source = text ?? ''
  if (!source.includes('[그림')) return [{ text: source, image: false }]
  const re = /\[그림(?:: [^\]\n]*)?\]/g
  const out = []
  let i = 0
  for (let m = re.exec(source); m; m = re.exec(source)) {
    if (m.index > i) out.push({ text: source.slice(i, m.index), image: false })
    out.push({ text: m[0], image: true })
    i = m.index + m[0].length
  }
  if (i < source.length) out.push({ text: source.slice(i), image: false })
  return out
}

/** 파일 이름에서 설명을 지어낸다. 클립보드에서 온 그림은 이름이 'image.png' 라 쓸 말이 없다. */
export function altFromFile(file) {
  const name = (file?.name ?? '').replace(/\.[^.]+$/, '')
  if (!name || /^(image|img|clipboard|스크린샷|screenshot)$/i.test(name)) return ''
  return cleanAlt(name)
}

export function formatBytes(bytes) {
  if (!Number.isFinite(bytes)) return ''
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)}MB`
  return `${Math.max(1, Math.round(bytes / 1024))}KB`
}

export function typeLabel(contentType) {
  return { 'image/png': 'png', 'image/jpeg': 'jpg', 'image/webp': 'webp', 'image/gif': 'gif' }[contentType] ?? ''
}
