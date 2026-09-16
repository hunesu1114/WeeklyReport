/**
 * 메모장 편집 동작.
 *
 * DOM 을 모르는 순수 함수로 둔다. textarea 를 붙잡고 쓰는 부분은
 * useNotepad 컴포저블이 맡고, 여기서는 "문자열을 어떻게 바꿀지"만 다룬다.
 */

/** 탭 한 번에 들어가는 공백. 메모장 기본은 탭 문자지만 4칸이 더 예측 가능하다. */
export const TAB = '    '

export function lineStartOf(value, index) {
  return value.lastIndexOf('\n', index - 1) + 1
}

export function lineEndOf(value, index) {
  const at = value.indexOf('\n', index)
  return at === -1 ? value.length : at
}

/**
 * 고른 범위가 걸쳐 있는 줄 전체의 경계.
 *
 * 끝이 줄 첫 칸에 딱 걸려 있으면 그 줄은 고른 것으로 치지 않는다 —
 * 세 줄을 끌어 내렸는데 네 줄이 들여쓰기되면 손이 놀란다.
 */
export function selectedLineRange(value, start, end) {
  const from = lineStartOf(value, start)
  const tail = end > start && lineStartOf(value, end) === end ? end - 1 : end
  return { from, to: lineEndOf(value, tail) }
}

/** 줄 앞쪽 공백. 엔터를 쳤을 때 같은 자리에서 이어 쓰게 한다. */
export function leadingWhitespace(line) {
  return /^[ \t]*/.exec(line)[0]
}

/** 블록의 모든 줄 앞에 탭을 넣는다. */
export function indentBlock(block) {
  return block
    .split('\n')
    .map((line) => TAB + line)
    .join('\n')
}

/**
 * 블록의 모든 줄에서 들여쓰기를 한 단계 걷어낸다.
 * 공백이 네 칸보다 적으면 있는 만큼만, 탭 문자면 하나만 지운다.
 */
export function outdentBlock(block) {
  return block
    .split('\n')
    .map((line) => {
      if (line.startsWith('\t')) return line.slice(1)
      let removed = 0
      while (removed < TAB.length && line[removed] === ' ') removed += 1
      return line.slice(removed)
    })
    .join('\n')
}

/** 커서가 몇 번째 줄, 몇 번째 칸에 있는지. 상태 표시줄이 쓴다. 둘 다 1부터 센다. */
export function caretPosition(value, index) {
  const start = lineStartOf(value, index)
  const line = value.slice(0, start).split('\n').length
  return { line, column: index - start + 1 }
}

/** n 번째 줄(1부터)의 시작 위치. 범위를 넘으면 가장 가까운 줄로 붙인다. */
export function offsetOfLine(value, line) {
  const lines = value.split('\n')
  const target = Math.min(Math.max(line, 1), lines.length)
  let offset = 0
  for (let i = 0; i < target - 1; i += 1) offset += lines[i].length + 1
  return offset
}

export function lineCount(value) {
  return value.split('\n').length
}

/**
 * 다음 찾기. 끝까지 갔으면 처음으로 돌아와 한 바퀴 돈다.
 * 찾지 못하면 null — 부르는 쪽이 "찾는 말이 없습니다" 를 띄운다.
 */
export function findNext(value, query, from, { matchCase = false, backwards = false } = {}) {
  if (!query) return null

  const haystack = matchCase ? value : value.toLowerCase()
  const needle = matchCase ? query : query.toLowerCase()

  if (backwards) {
    const at = haystack.lastIndexOf(needle, Math.max(from - needle.length - 1, -1))
    if (at !== -1) return { start: at, end: at + needle.length }
    const wrapped = haystack.lastIndexOf(needle)
    return wrapped === -1 ? null : { start: wrapped, end: wrapped + needle.length }
  }

  const at = haystack.indexOf(needle, from)
  if (at !== -1) return { start: at, end: at + needle.length }
  const wrapped = haystack.indexOf(needle)
  return wrapped === -1 ? null : { start: wrapped, end: wrapped + needle.length }
}

/** 모두 바꾸기. 바꾼 결과와 몇 군데를 바꿨는지 함께 준다. */
export function replaceAll(value, query, replacement, { matchCase = false } = {}) {
  if (!query) return { value, count: 0 }

  let result = ''
  let cursor = 0
  let count = 0

  for (;;) {
    const hit = matchCase
      ? value.indexOf(query, cursor)
      : value.toLowerCase().indexOf(query.toLowerCase(), cursor)
    if (hit === -1) break

    result += value.slice(cursor, hit) + replacement
    cursor = hit + query.length
    count += 1
  }

  return { value: result + value.slice(cursor), count }
}

/** 메모장의 F5. 로그를 적을 때 손으로 날짜를 치지 않게 해준다. */
export function timestamp(now = new Date()) {
  const pad = (n) => String(n).padStart(2, '0')
  const hour = now.getHours()
  const meridiem = hour < 12 ? '오전' : '오후'
  const hour12 = hour % 12 === 0 ? 12 : hour % 12

  return (
    `${meridiem} ${pad(hour12)}:${pad(now.getMinutes())} ` +
    `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
  )
}
