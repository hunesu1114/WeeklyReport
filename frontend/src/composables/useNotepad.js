import { computed, onBeforeUnmount, ref } from 'vue'
import {
  TAB,
  caretPosition,
  findNext,
  indentBlock,
  leadingWhitespace,
  lineCount,
  lineEndOf,
  lineStartOf,
  offsetOfLine,
  outdentBlock,
  replaceAll,
  selectedLineRange,
  timestamp,
} from '@/utils/notepad'

/**
 * textarea 를 메모장처럼 다루게 한다.
 *
 * <p>글자를 넣을 때는 값을 직접 갈아끼우지 않고 execCommand('insertText') 를 쓴다.
 * 낡은 API 지만, textarea 의 되돌리기(Ctrl+Z) 기록을 보존하는 방법은 아직 이것뿐이다.
 * value 에 직접 대입하면 탭 한 번에 되돌리기가 통째로 끊긴다.
 */
export function useNotepad(textareaRef, { onSave, onNew, onExport } = {}) {
  const caret = ref({ line: 1, column: 1 })
  const selectionLength = ref(0)

  // ── 찾기 / 바꾸기 ────────────────────────────────────────
  const finder = ref({ open: false, query: '', replacement: '', matchCase: false, message: '' })

  const el = () => textareaRef.value

  function sync() {
    const area = el()
    if (!area) return
    caret.value = caretPosition(area.value, area.selectionStart)
    selectionLength.value = area.selectionEnd - area.selectionStart
  }

  /** 되돌리기 기록을 지키며 글자를 넣는다. 지원하지 않는 브라우저면 직접 바꾼다. */
  function insert(text) {
    const area = el()
    if (!area) return
    area.focus()
    if (!document.execCommand('insertText', false, text)) {
      const { selectionStart: s, selectionEnd: e, value } = area
      area.value = value.slice(0, s) + text + value.slice(e)
      area.selectionStart = area.selectionEnd = s + text.length
      area.dispatchEvent(new Event('input', { bubbles: true }))
    }
    /*
     * 기본 동작을 막고 우리가 직접 넣었기 때문에, 브라우저가 알아서 해주던
     * "커서 따라 굴리기"도 함께 사라졌다. 엔터로 새 줄을 만들면 그 줄이
     * 화면 밖에 생겨 무엇을 치는지 보이지 않는다.
     *
     * requestAnimationFrame 으로 미루지 않는다. 화면이 그려지지 않는 동안에는
     * 콜백이 아예 오지 않아서, 탭이 뒤에 있거나 창이 가려져 있으면 스크롤이
     * 통째로 빠진다. 대신 scrollCaretIntoView 안에서 배치를 강제로 확정시킨다.
     */
    scrollCaretIntoView()
    sync()
  }

  function replaceRange(start, end, text, select) {
    const area = el()
    if (!area) return
    area.setSelectionRange(start, end)
    insert(text)
    if (select) area.setSelectionRange(select.start, select.end)
    sync()
  }

  function selectRange(start, end) {
    const area = el()
    if (!area) return
    area.focus()
    area.setSelectionRange(start, end)
    scrollCaretIntoView()
    sync()
  }

  /**
   * 커서가 세로로 어디에 있는지(px) 재는 거울 요소.
   *
   * 줄 수 × 줄 높이로 어림잡으면 자동 줄 바꿈에서 어긋난다 — 접혀서 세 줄을
   * 차지하는 문단도 \n 기준으로는 한 줄이기 때문이다. textarea 와 같은 글꼴·너비·
   * 여백을 가진 요소에 커서 앞까지의 글을 넣고 표식의 위치를 읽는다.
   */
  let mirror = null

  const MIRRORED = [
    'fontFamily', 'fontSize', 'fontWeight', 'fontStyle', 'letterSpacing', 'lineHeight',
    'textIndent', 'textTransform', 'tabSize', 'boxSizing', 'overflowWrap', 'wordBreak',
    'paddingTop', 'paddingRight', 'paddingBottom', 'paddingLeft',
    'borderTopWidth', 'borderRightWidth', 'borderBottomWidth', 'borderLeftWidth',
  ]

  function caretTop(area) {
    if (!mirror) {
      mirror = document.createElement('div')
      mirror.setAttribute('aria-hidden', 'true')
      mirror.style.position = 'absolute'
      mirror.style.top = '0'
      mirror.style.left = '-9999px'
      mirror.style.visibility = 'hidden'
      document.body.appendChild(mirror)
    }

    const computed = getComputedStyle(area)
    for (const prop of MIRRORED) mirror.style[prop] = computed[prop]
    mirror.style.width = `${area.clientWidth}px`
    mirror.style.whiteSpace = area.wrap === 'off' ? 'pre' : 'pre-wrap'

    mirror.textContent = area.value.slice(0, area.selectionStart)
    const marker = document.createElement('span')
    // 줄 끝에 커서가 있을 때도 높이를 갖도록 폭 없는 공백을 둔다
    marker.textContent = '​'
    mirror.appendChild(marker)

    return marker.offsetTop
  }

  /**
   * 커서가 화면 밖으로 나가면 따라간다.
   *
   * 아래로 붙일 때는 본문 아래 여백만큼 더 굴린다. 그러지 않으면 새로 만든 줄이
   * 화면 맨 아래 선에 딱 붙어, 글을 치고 있는데도 잘린 것처럼 보인다.
   */
  function scrollCaretIntoView() {
    const area = el()
    if (!area) return

    /*
     * 방금 넣은 글자를 배치에 반영시킨다. scrollHeight 를 읽으면 브라우저가
     * 미뤄둔 계산을 그 자리에서 끝낸다. 이걸 건너뛰면 아직 늘어나기 전의
     * 한계선에 scrollTop 이 잘려, 새로 만든 줄만큼 모자란 채로 멈춘다.
     */
    void area.scrollHeight

    const computed = getComputedStyle(area)
    const lineHeight = parseFloat(computed.lineHeight) || 20
    const padBottom = parseFloat(computed.paddingBottom) || 0
    const top = caretTop(area)

    if (top < area.scrollTop) {
      area.scrollTop = Math.max(0, top - lineHeight)
    } else if (top + lineHeight + padBottom > area.scrollTop + area.clientHeight) {
      area.scrollTop = top + lineHeight + padBottom - area.clientHeight
    }
  }

  // ── 줄 단위 동작 ─────────────────────────────────────────

  /** 커서가 걸친 줄 전체. 줄 잘라내기·복사·지우기가 모두 이걸 쓴다. */
  function currentLineRange() {
    const area = el()
    const { from, to } = selectedLineRange(area.value, area.selectionStart, area.selectionEnd)
    return { from, to, hasNewline: to < area.value.length }
  }

  function duplicateLine() {
    const area = el()
    const { from, to } = currentLineRange()
    const block = area.value.slice(from, to)
    replaceRange(to, to, '\n' + block, { start: to + 1, end: to + 1 + block.length })
  }

  function deleteLine() {
    const area = el()
    const { from, to, hasNewline } = currentLineRange()
    replaceRange(from, hasNewline ? to + 1 : from === 0 ? to : from - 1, '')
    area.setSelectionRange(Math.min(from, area.value.length), Math.min(from, area.value.length))
    sync()
  }

  function indent(outdent = false) {
    const area = el()
    const { selectionStart, selectionEnd, value } = area

    // 한 줄 안에서 커서만 있을 때는 그 자리에 탭을 넣는 게 자연스럽다
    if (!outdent && selectionStart === selectionEnd) {
      insert(TAB)
      return
    }

    const { from, to } = selectedLineRange(value, selectionStart, selectionEnd)
    const block = value.slice(from, to)
    const next = outdent ? outdentBlock(block) : indentBlock(block)
    if (next === block) return

    replaceRange(from, to, next, { start: from, end: from + next.length })
  }

  /** 엔터. 앞 줄의 들여쓰기를 이어받는다. */
  function newlineKeepingIndent() {
    const area = el()
    const start = lineStartOf(area.value, area.selectionStart)
    const line = area.value.slice(start, area.selectionStart)
    insert('\n' + leadingWhitespace(line))
  }

  // ── 찾기 / 바꾸기 ────────────────────────────────────────

  function openFinder(withReplace = false) {
    const area = el()
    const picked = area && area.selectionEnd > area.selectionStart
    finder.value = {
      ...finder.value,
      open: true,
      replace: withReplace,
      query: picked ? area.value.slice(area.selectionStart, area.selectionEnd) : finder.value.query,
      message: '',
    }
  }

  function closeFinder() {
    finder.value = { ...finder.value, open: false, message: '' }
    el()?.focus()
  }

  function search(backwards = false) {
    const area = el()
    if (!area || !finder.value.query) return

    const from = backwards ? area.selectionStart : area.selectionEnd
    const hit = findNext(area.value, finder.value.query, from, {
      matchCase: finder.value.matchCase,
      backwards,
    })

    if (!hit) {
      finder.value = { ...finder.value, message: '찾는 내용이 없습니다.' }
      return
    }
    finder.value = { ...finder.value, message: '' }
    selectRange(hit.start, hit.end)
  }

  /** 고른 글자가 찾는 말과 같으면 바꾸고, 아니면 먼저 찾아만 둔다. */
  function replaceOne() {
    const area = el()
    if (!area || !finder.value.query) return

    const picked = area.value.slice(area.selectionStart, area.selectionEnd)
    const same = finder.value.matchCase
      ? picked === finder.value.query
      : picked.toLowerCase() === finder.value.query.toLowerCase()

    if (same) insert(finder.value.replacement)
    search()
  }

  function replaceEvery() {
    const area = el()
    if (!area || !finder.value.query) return

    const { value, count } = replaceAll(area.value, finder.value.query, finder.value.replacement, {
      matchCase: finder.value.matchCase,
    })
    if (count === 0) {
      finder.value = { ...finder.value, message: '찾는 내용이 없습니다.' }
      return
    }

    replaceRange(0, area.value.length, value)
    area.setSelectionRange(0, 0)
    finder.value = { ...finder.value, message: `${count}군데를 바꿨습니다.` }
    sync()
  }

  function goToLine(line) {
    const area = el()
    if (!area) return
    const offset = offsetOfLine(area.value, Number(line))
    selectRange(offset, lineEndOf(area.value, offset))
  }

  // ── 이벤트 ───────────────────────────────────────────────

  function onKeydown(event) {
    const area = el()
    if (!area) return
    const mod = event.ctrlKey || event.metaKey

    if (event.key === 'Tab') {
      event.preventDefault()
      indent(event.shiftKey)
      return
    }
    if (event.key === 'Enter' && !mod && !event.shiftKey) {
      event.preventDefault()
      newlineKeepingIndent()
      return
    }
    if (event.key === 'F5') {
      // 메모장의 그 F5. 새로고침을 막고 시각을 넣는다.
      event.preventDefault()
      insert(timestamp())
      return
    }
    if (event.key === 'F3') {
      event.preventDefault()
      search(event.shiftKey)
      return
    }
    if (event.key === 'Escape' && finder.value.open) {
      event.preventDefault()
      closeFinder()
      return
    }
    if (!mod) return

    const key = event.key.toLowerCase()
    if (key === 's') {
      event.preventDefault()
      if (event.shiftKey) onExport?.()
      else onSave?.()
    } else if (key === 'n') {
      event.preventDefault()
      onNew?.()
    } else if (key === 'f') {
      event.preventDefault()
      openFinder(false)
    } else if (key === 'h') {
      event.preventDefault()
      openFinder(true)
    } else if (key === 'g') {
      event.preventDefault()
      const answer = window.prompt(`이동할 줄 번호 (1 ~ ${lineCount(area.value)})`, caret.value.line)
      if (answer !== null && answer.trim()) goToLine(answer.trim())
    } else if (key === 'd') {
      event.preventDefault()
      duplicateLine()
    } else if (key === 'delete' || (event.shiftKey && key === 'k')) {
      event.preventDefault()
      deleteLine()
    }
  }

  /**
   * 고른 글자가 없을 때의 잘라내기·복사는 줄 전체를 집는다.
   *
   * keydown 이 아니라 cut/copy 이벤트에서 처리한다. 클립보드에 직접 써넣을 수 있어
   * 권한을 묻지 않고, Shift+Delete 같은 다른 경로로 들어와도 똑같이 동작한다.
   */
  function onCopy(event) {
    const area = el()
    if (!area || area.selectionStart !== area.selectionEnd) return

    const { from, to } = currentLineRange()
    event.clipboardData.setData('text/plain', area.value.slice(from, to) + '\n')
    event.preventDefault()
  }

  function onCut(event) {
    const area = el()
    if (!area || area.selectionStart !== area.selectionEnd) return

    onCopy(event)
    deleteLine()
  }

  onBeforeUnmount(() => {
    mirror?.remove()
    mirror = null
  })

  const status = computed(() => ({
    line: caret.value.line,
    column: caret.value.column,
    selected: selectionLength.value,
  }))

  return {
    status,
    finder,
    sync,
    onKeydown,
    onCopy,
    onCut,
    insert,
    openFinder,
    closeFinder,
    search,
    replaceOne,
    replaceEvery,
    goToLine,
  }
}
