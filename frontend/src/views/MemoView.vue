<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import BaseModal from '@/components/BaseModal.vue'
import MemoTree from '@/components/MemoTree.vue'
import { memoApi } from '@/api/client'
import { useMemoStore, ROOT, FAVORITES, RECENT, TRASH } from '@/stores/memo'
import { useToast } from '@/composables/useToast'
import { useNotepad } from '@/composables/useNotepad'
import { lineCount } from '@/utils/notepad'

const store = useMemoStore()
const toast = useToast()

/** 저장하지 않은 채 이만큼 쉬면 알아서 저장한다. 메모장과 달리 잃을 이유가 없다. */
const AUTOSAVE_MS = 1200
/** 저장에 실패하면 이만큼 뒤에 다시 해 본다. 잠깐 끊긴 네트워크는 대개 돌아온다. */
const RETRY_MS = 5000
const DEFAULT_SIZE = 15

/*
 * 글꼴은 세 갈래만 고르게 한다. 예전에 고른 굴림·돋움 같은 값은 그대로 남아 보이고,
 * 고르는 칸에서는 가장 가까운 갈래에 불이 들어온다.
 */
const FONTS = [
  { key: 'sans', label: '고딕', value: '' },
  { key: 'serif', label: '명조', value: "'Batang', 'AppleMyungjo', 'Noto Serif KR', serif" },
  { key: 'mono', label: '고정폭', value: "'D2Coding', 'IBM Plex Mono', 'Consolas', monospace" },
]

/** 글자 색은 이름으로 저장한다. 테마마다 읽히는 값이 main.css 에 있다. */
const COLORS = [
  { key: null, label: '기본', css: 'var(--text)' },
  { key: 'gray', label: '회색', css: 'var(--ink-gray)' },
  { key: 'blue', label: '파랑', css: 'var(--ink-blue)' },
  { key: 'green', label: '초록', css: 'var(--ink-green)' },
  { key: 'brown', label: '갈색', css: 'var(--ink-brown)' },
]

const SHORTCUTS = [
  ['/', '메모 검색으로'],
  ['Ctrl+N', '새 메모'],
  ['Ctrl+S', '바로 저장 (멈추면 알아서 저장된다)'],
  ['Ctrl+Shift+S', 'txt 로 내보내기'],
  ['Ctrl+F / Ctrl+H', '찾기 / 바꾸기'],
  ['F3 / Shift+F3', '다음 찾기 / 이전 찾기'],
  ['Ctrl+G', '줄 번호로 이동'],
  ['Tab / Shift+Tab', '들여쓰기 / 내어쓰기 (4칸)'],
  ['Enter', '앞 줄 들여쓰기 이어받기'],
  ['Ctrl+D', '줄 복제'],
  ['Ctrl+Delete', '줄 삭제'],
  ['Ctrl+X / Ctrl+C', '고른 것 없으면 한 줄 잘라내기 / 복사'],
  ['F5', '날짜와 시각 넣기'],
  ['↑ / ↓', '목록에서 위아래 메모 열기'],
]

const editor = ref(null)
const backdrop = ref(null)
const titleInput = ref(null)
const findInput = ref(null)
const searchInput = ref(null)
const listRef = ref(null)

const memo = ref(null)
/*
 * 고친 횟수와 저장한 횟수. 둘이 다르면 저장할 것이 남았다.
 * 참/거짓 하나로 두면, 저장 요청이 가는 사이에 친 글자까지 '저장됨'으로 덮인다.
 */
const editVersion = ref(0)
const savedVersion = ref(0)
const saving = ref(false)
const saveError = ref('')
const busy = ref(false)

/** 열려 있는 작은 창. 'view'(Aa) · 'more'(⋯) · 'move'(폴더 옮기기) */
const pop = ref(null)
const shortcutsOpen = ref(false)
/** 폴더 이름을 고치거나 새로 만드는 중. { mode: 'rename'|'create', id?, parentId? } */
const folderEdit = ref(null)
const folderMenu = ref(null)
/** 사이드바 칸 위로 메모를 끌고 왔다. 'favorites' · 'root' · 'trash' */
const dropTarget = ref(null)
/** 좁은 화면에서는 목록과 편집기 중 하나만 보인다 */
const mobilePane = ref('list')

let autosaveTimer = null
let retryTimer = null
let saveAgain = false
let inflight = null

const dirty = computed(() => editVersion.value !== savedVersion.value)
const trashed = computed(() => Boolean(memo.value?.deletedAt))

/* 사이드바 맨 위 새 폴더 칸. 나타나자마자 커서가 들어가 있어야 바로 친다 */
const vFocusSelect = {
  mounted(el) {
    el.focus()
    el.select()
  },
}

const notepad = useNotepad(editor, {
  onSave: () => save(),
  onNew: () => createMemo(),
  onExport: () => exportText(),
})
const { status, finder, selection } = notepad

// ── 목록 머리 ───────────────────────────────────────────

/** 사이드바에서 불이 들어올 칸. 찾는 중에는 어느 칸도 아니다. */
const activeView = computed(() => (store.searching ? null : store.selectedFolderId))

const listTitle = computed(() => {
  if (store.searching) return '검색 결과'
  const view = store.selectedFolderId
  if (view === RECENT) return '모든 메모'
  if (view === FAVORITES) return '즐겨찾기'
  if (view === ROOT) return '미분류'
  if (view === TRASH) return '휴지통'
  return store.folderById.get(view)?.name ?? '폴더'
})

const listCrumb = computed(() => {
  if (store.searching) return '모든 폴더에서 제목과 본문을 찾았습니다'
  const view = store.selectedFolderId
  if (typeof view !== 'number') return '보기'
  const path = store.pathOf(view)
  return path.length > 1 ? path.slice(0, -1).join(' / ') : '폴더'
})

const emptyText = computed(() => {
  if (store.searching) {
    return store.searchLoading ? '찾는 중…' : `‘${store.query.trim()}’이(가) 들어간 메모가 없습니다.`
  }
  const view = store.selectedFolderId
  if (view === FAVORITES) return '즐겨찾기한 메모가 없습니다. 메모를 열고 별을 누르세요.'
  if (view === TRASH) return '휴지통이 비어 있습니다.'
  if (view === RECENT) return '아직 메모가 없습니다.'
  return '이 폴더에 메모가 없습니다.'
})

/** 여러 폴더에서 모아 보여줄 때만 줄마다 폴더를 단다 */
const showFolderOnItems = computed(
  () => store.searching || [RECENT, FAVORITES, TRASH].includes(store.selectedFolderId),
)

const folderOptions = computed(() =>
  store.folders
    .map((f) => ({ id: f.id, label: store.pathOf(f.id).join(' / ') }))
    .sort((a, b) => a.label.localeCompare(b.label, 'ko')),
)

function folderLabel(folderId) {
  return folderId == null ? '미분류' : store.pathOf(folderId).join(' / ')
}

const editorCrumb = computed(() => (memo.value ? folderLabel(memo.value.folderId).replaceAll(' / ', ' › ') : ''))

// ── 날짜 말 ─────────────────────────────────────────────

const pad2 = (n) => String(n).padStart(2, '0')
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

/** 목록 오른쪽 시각. 오늘은 시각만, 이번 주는 요일, 그 전은 날짜. */
function whenLabel(iso) {
  if (!iso) return ''
  const at = new Date(iso)
  const now = new Date()
  const hm = `${pad2(at.getHours())}:${pad2(at.getMinutes())}`
  if (at.toDateString() === now.toDateString()) return hm
  const days = (now - at) / 86400000
  if (days < 6) return `${WEEKDAYS[at.getDay()]} ${hm}`
  if (at.getFullYear() === now.getFullYear()) return `${at.getMonth() + 1}.${pad2(at.getDate())}`
  return `${at.getFullYear()}.${pad2(at.getMonth() + 1)}.${pad2(at.getDate())}`
}

function fullDate(iso) {
  if (!iso) return ''
  const at = new Date(iso)
  const now = new Date()
  const year = at.getFullYear() === now.getFullYear() ? '' : `${at.getFullYear()}년 `
  return `${year}${at.getMonth() + 1}월 ${at.getDate()}일 ${pad2(at.getHours())}:${pad2(at.getMinutes())}`
}

function clock(iso) {
  if (!iso) return ''
  const at = new Date(iso)
  return `${pad2(at.getHours())}:${pad2(at.getMinutes())}`
}

/** 찾는 말이 든 자리를 갈라 <mark> 로 칠할 수 있게 한다 */
function splitHits(text, query) {
  const needle = (query ?? '').trim().toLowerCase()
  const source = text ?? ''
  if (!needle) return [{ text: source, hit: false }]
  const low = source.toLowerCase()
  const out = []
  let i = 0
  for (;;) {
    const j = low.indexOf(needle, i)
    if (j < 0) break
    if (j > i) out.push({ text: source.slice(i, j), hit: false })
    out.push({ text: source.slice(j, j + needle.length), hit: true })
    i = j + needle.length
  }
  if (i < source.length) out.push({ text: source.slice(i), hit: false })
  return out.length ? out : [{ text: '', hit: false }]
}

function previewOf(item) {
  if (store.searching) return item.snippet || item.preview || '내용 없음'
  return item.preview || '내용 없음'
}

// ── 편집기 모양 ─────────────────────────────────────────

const fontKey = computed(() => {
  const value = memo.value?.fontFamily ?? ''
  if (!value) return 'sans'
  if (/monospace|D2Coding|Consolas|Courier/i.test(value)) return 'mono'
  if (/serif/i.test(value.replace(/sans-serif/gi, '')) || /Batang|Myungjo/i.test(value)) return 'serif'
  return 'sans'
})

const colorCss = computed(() => {
  const value = memo.value?.fontColor
  if (!value) return 'var(--text)'
  // 예전에 고른 색 코드는 그대로 칠한다
  return COLORS.find((c) => c.key === value)?.css ?? value
})

const fontSize = computed(() => memo.value?.fontSize || DEFAULT_SIZE)

const editorStyle = computed(() => ({
  fontFamily: memo.value?.fontFamily || 'var(--font)',
  fontSize: `${fontSize.value}px`,
  color: colorCss.value,
}))

const viewLabel = computed(() => {
  const font = FONTS.find((f) => f.key === fontKey.value)?.label ?? '고딕'
  return `${font} ${fontSize.value}px · ${memo.value?.wordWrap === false ? '줄 바꿈 없음' : '자동 줄 바꿈'}`
})

const stats = computed(() => {
  const text = memo.value?.content ?? ''
  return { chars: text.length.toLocaleString('ko-KR'), lines: lineCount(text).toLocaleString('ko-KR') }
})

const saveState = computed(() => {
  if (trashed.value) return { tone: 'muted', text: '휴지통에 있음' }
  if (saveError.value) return { tone: 'bad', text: '저장하지 못했습니다' }
  if (saving.value || dirty.value) return { tone: 'muted', text: '저장 중…' }
  return { tone: 'ok', text: `저장됨 · ${clock(memo.value?.updatedAt)}` }
})

// ── 찾은 자리 칠하기 ────────────────────────────────────

/** 본문에서 칠할 말. 찾기 막대가 열려 있으면 그 말, 아니면 목록 검색어. */
const highlightTerm = computed(() => {
  if (finder.value.open) return finder.value.query ?? ''
  return store.searching ? store.query.trim() : ''
})

const hits = computed(() => {
  const term = highlightTerm.value
  const text = memo.value?.content ?? ''
  if (!term) return []
  const matchCase = finder.value.open && finder.value.matchCase
  const hay = matchCase ? text : text.toLowerCase()
  const needle = matchCase ? term : term.toLowerCase()
  const out = []
  let i = 0
  // 한 글자로 수만 군데가 걸리면 칠하는 것만으로 느려진다. 그 정도면 위치보다 개수가 중요하다
  while (out.length < 2000) {
    const j = hay.indexOf(needle, i)
    if (j < 0) break
    out.push({ start: j, end: j + needle.length })
    i = j + needle.length
  }
  return out
})

const currentHit = computed(() =>
  hits.value.findIndex((h) => h.start === selection.value.start && h.end === selection.value.end),
)

const findCount = computed(() => {
  if (!highlightTerm.value) return ''
  if (!hits.value.length) return '없음'
  return `${currentHit.value >= 0 ? currentHit.value + 1 : '–'} / ${hits.value.length}`
})

/*
 * textarea 는 글자 일부만 칠할 수 없다. 똑같은 글꼴·여백·줄 바꿈을 가진 판을
 * 뒤에 깔고, 그 판에서 찾은 자리만 노랗게 칠한 뒤 textarea 를 투명하게 얹는다.
 */
const backdropSegs = computed(() => {
  const text = memo.value?.content ?? ''
  const out = []
  let i = 0
  hits.value.forEach((h, index) => {
    if (h.start > i) out.push({ text: text.slice(i, h.start), hit: false })
    out.push({ text: text.slice(h.start, h.end), hit: true, cur: index === currentHit.value })
    i = h.end
  })
  // 끝이 줄 바꿈이면 판의 높이가 한 줄 모자라 스크롤이 어긋난다
  out.push({ text: text.slice(i) + '\u200b', hit: false })
  return out
})

const showBackdrop = computed(() => hits.value.length > 0)

function syncBackdrop() {
  if (!backdrop.value || !editor.value) return
  backdrop.value.scrollTop = editor.value.scrollTop
  backdrop.value.scrollLeft = editor.value.scrollLeft
}

watch([showBackdrop, () => memo.value?.content], () => nextTick(syncBackdrop))

// ── 불러오기 ────────────────────────────────────────────

onMounted(async () => {
  document.addEventListener('keydown', onDocKey)
  document.addEventListener('pointerdown', onDocPointer)
  window.addEventListener('beforeunload', onBeforeUnload)
  document.addEventListener('visibilitychange', onVisibility)

  try {
    await store.load()
  } catch (error) {
    toast.error(`메모를 불러오지 못했습니다. ${error.message}`)
    return
  }
  const remembered = store.lastMemoId()
  const target =
    store.memos.find((m) => m.id === remembered) ?? store.visibleMemos[0] ?? store.memos[0]
  if (target) await openMemo(target.id, { focus: false })
  mobilePane.value = 'list'
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onDocKey)
  document.removeEventListener('pointerdown', onDocPointer)
  window.removeEventListener('beforeunload', onBeforeUnload)
  document.removeEventListener('visibilitychange', onVisibility)
  clearTimeout(autosaveTimer)
  clearTimeout(retryTimer)
})

// 다른 화면으로 나가도 쓰던 것은 남아야 한다. 저장이 끝날 때까지 기다렸다 나간다
onBeforeRouteLeave(async () => {
  clearTimeout(autosaveTimer)
  if (dirty.value) await save()
})

/**
 * 탭을 닫는다. 보통의 요청은 페이지와 함께 끊기므로 keepalive 로 보낸다.
 * 너무 길어 그렇게 못 보내면 브라우저의 "나가시겠습니까?"로 붙잡는다.
 */
function onBeforeUnload(event) {
  if (!memo.value || !dirty.value || trashed.value) return
  const sent = memoApi.saveOnExit(memo.value.id, payloadOf(memo.value))
  if (!sent) {
    event.preventDefault()
    event.returnValue = ''
  }
}

/** 다른 앱으로 넘어가면 바로 저장한다. 휴대폰은 탭을 닫지 않고 그냥 치워 버린다. */
function onVisibility() {
  if (document.visibilityState === 'hidden' && dirty.value) save()
}

async function openMemo(id, { focus = true } = {}) {
  if (memo.value?.id === id) {
    mobilePane.value = 'editor'
    return
  }
  clearTimeout(autosaveTimer)
  if (dirty.value) await save()

  busy.value = true
  try {
    const loaded = await memoApi.get(id)
    memo.value = loaded
    editVersion.value = 0
    savedVersion.value = 0
    saveError.value = ''
    pop.value = null
    store.rememberMemo(id)
    store.revealFolder(loaded.folderId)
    mobilePane.value = 'editor'
    await nextTick()
    notepad.sync()
    if (store.searching && !finder.value.open) jumpToFirstHit(focus)
  } catch (error) {
    toast.error(error.message)
  } finally {
    busy.value = false
  }
}

/** 검색 결과에서 열었다. 찾은 첫 자리로 커서를 옮겨 보여준다. */
function jumpToFirstHit(focus) {
  const area = editor.value
  if (!area || !focus) return
  finder.value = { ...finder.value, query: store.query.trim() }
  area.setSelectionRange(0, 0)
  notepad.search(false)
}

// ── 쓰기 ────────────────────────────────────────────────

function touched() {
  if (trashed.value) return
  editVersion.value += 1
  clearTimeout(autosaveTimer)
  autosaveTimer = setTimeout(() => save(), AUTOSAVE_MS)
}

/**
 * 저장한다. 저장이 가는 중에 또 부르면 끝난 뒤 한 번 더 보낸다 —
 * 그 사이에 친 글자가 빠지지 않는다.
 */
async function save() {
  if (!memo.value || trashed.value) return
  clearTimeout(autosaveTimer)
  clearTimeout(retryTimer)
  if (saving.value) {
    saveAgain = true
    return inflight
  }
  if (!dirty.value && !saveError.value) return

  const target = memo.value
  const version = editVersion.value
  saving.value = true
  inflight = (async () => {
    try {
      const saved = await memoApi.update(target.id, payloadOf(target))
      if (memo.value?.id === target.id) {
        // 저장이 가는 사이에 제목을 더 고쳤으면 고친 쪽을 남긴다
        const title = editVersion.value === version ? saved.title : memo.value.title
        memo.value = { ...memo.value, title, updatedAt: saved.updatedAt }
        savedVersion.value = version
      }
      store.applySaved(saved)
      store.refreshSearch()
      saveError.value = ''
    } catch (error) {
      saveError.value = error.message
      retryTimer = setTimeout(() => save(), RETRY_MS)
    } finally {
      saving.value = false
      inflight = null
    }
  })()
  await inflight

  if (saveAgain) {
    saveAgain = false
    if (dirty.value) await save()
  }
}

function payloadOf(m) {
  return {
    title: m.title,
    content: m.content,
    folderId: m.folderId,
    fontFamily: m.fontFamily,
    fontSize: m.fontSize,
    fontColor: m.fontColor,
    wordWrap: m.wordWrap,
  }
}

async function createMemo(folderId = undefined) {
  clearTimeout(autosaveTimer)
  if (dirty.value) await save()

  // 모아 보는 칸(전체·즐겨찾기·휴지통)에서 만들면 폴더 밖에 놓는다
  const selected = store.selectedFolderId
  const target = folderId !== undefined ? folderId : typeof selected === 'number' ? selected : null

  busy.value = true
  try {
    const created = await memoApi.create({ title: '제목 없음', content: '', folderId: target })
    if (store.selectedFolderId === TRASH) store.selectFolder(RECENT)
    store.query = ''
    store.applySaved(created)
    memo.value = created
    editVersion.value = 0
    savedVersion.value = 0
    saveError.value = ''
    store.rememberMemo(created.id)
    store.revealFolder(created.folderId)
    mobilePane.value = 'editor'
    await nextTick()
    titleInput.value?.focus()
    titleInput.value?.select()
  } catch (error) {
    toast.error(error.message)
  } finally {
    busy.value = false
  }
}

/** 지운 메모 대신 열 것. 목록에서 바로 아래, 없으면 바로 위. */
function neighborOf(id) {
  const list = store.visibleMemos
  const at = list.findIndex((m) => m.id === id)
  if (at === -1) return list[0] ?? null
  return list[at + 1] ?? list[at - 1] ?? null
}

/** 휴지통으로. 바로 '되돌리기'를 띄운다 — 확인 창을 한 번 더 띄우는 것보다 빠르고 안전하다. */
async function trashMemo(item = memo.value) {
  if (!item) return
  pop.value = null
  const isOpen = memo.value?.id === item.id
  if (isOpen) {
    clearTimeout(autosaveTimer)
    if (dirty.value) await save()
  }
  const next = neighborOf(item.id)

  try {
    await memoApi.remove(item.id)
    store.trashLocally(item.id)
    if (isOpen) {
      memo.value = null
      if (next) await openMemo(next.id, { focus: false })
    }
    toast.undoable(`‘${item.title}’을(를) 휴지통으로 옮겼습니다`, {
      label: '되돌리기',
      run: () => restoreMemo(item.id, { reopen: true }),
    })
  } catch (error) {
    toast.error(error.message)
  }
}

async function restoreMemo(id, { reopen = false } = {}) {
  try {
    const restored = await memoApi.restore(id)
    store.applySaved(restored)
    if (memo.value?.id === id) {
      memo.value = restored
    } else if (reopen) {
      if (store.selectedFolderId === TRASH) store.selectFolder(restored.folderId ?? ROOT)
      await openMemo(id, { focus: false })
    }
    toast.success('메모를 되살렸습니다.')
  } catch (error) {
    toast.error(error.message)
  }
}

async function purgeMemo() {
  if (!memo.value) return
  const target = memo.value
  if (!window.confirm(`‘${target.title}’을(를) 영구히 삭제할까요?\n되돌릴 수 없습니다.`)) return
  const next = neighborOf(target.id)
  try {
    await memoApi.purge(target.id)
    store.forget(target.id)
    memo.value = null
    if (next) await openMemo(next.id, { focus: false })
  } catch (error) {
    toast.error(error.message)
  }
}

async function emptyTrash() {
  const count = store.trash.length
  if (!count) return
  if (!window.confirm(`휴지통의 메모 ${count}개를 영구히 삭제할까요?\n되돌릴 수 없습니다.`)) return
  try {
    await memoApi.emptyTrash()
    store.clearTrashLocally()
    if (trashed.value) memo.value = null
    toast.success('휴지통을 비웠습니다.')
  } catch (error) {
    toast.error(error.message)
  }
}

async function exportText() {
  if (!memo.value) return
  pop.value = null
  if (dirty.value) await save()
  try {
    const filename = await memoApi.exportText(memo.value.id)
    toast.success(`${filename} 파일을 내려받았습니다.`)
  } catch (error) {
    toast.error(`내보내지 못했습니다. ${error.message}`)
  }
}

// ── 고정과 즐겨찾기 ─────────────────────────────────────

/**
 * 깃발을 뒤집는다.
 *
 * 본문 저장과 따로 보낸다. 편집 중이라도 바로 눌리고, 고친 시각도 오르지 않아
 * 목록이 제자리에 있는다. 편집기에 열려 있는 메모라면 그 깃발만 갈아 끼운다 —
 * 아직 저장하지 않은 본문은 그대로 둬야 한다.
 */
async function toggleFlag(item, flag) {
  const send = flag === 'pinned' ? memoApi.setPinned : memoApi.setFavorite
  try {
    const saved = await send(item.id, !item[flag])
    store.applySaved({ ...saved, content: memo.value?.id === saved.id ? memo.value.content : saved.content })
    if (memo.value?.id === saved.id) memo.value = { ...memo.value, [flag]: saved[flag] }
  } catch (error) {
    toast.error(error.message)
  }
}

const togglePin = (item) => toggleFlag(item, 'pinned')
const toggleFavorite = (item) => toggleFlag(item, 'favorite')

/** 폴더를 바꾸면 곧바로 옮긴다. 목록에서 제자리를 찾아야 하기 때문이다. */
async function changeFolder(folderId) {
  if (!memo.value) return
  pop.value = null
  await dropMemo({ memoId: memo.value.id, folderId })
}

// ── 보기 설정 (Aa) ──────────────────────────────────────

function setStyle(field, value) {
  if (!memo.value || trashed.value) return
  memo.value[field] = value
  touched()
}

function stepSize(delta) {
  setStyle('fontSize', Math.min(24, Math.max(11, fontSize.value + delta)))
}

// ── 폴더 ────────────────────────────────────────────────

function startCreateFolder(parent = null) {
  folderMenu.value = null
  if (parent) store.revealFolder(parent.id)
  if (parent && !store.isOpen(parent.id)) store.toggleFolder(parent.id)
  folderEdit.value = { mode: 'create', parentId: parent?.id ?? null }
}

function startRenameFolder(node) {
  folderMenu.value = null
  folderEdit.value = { mode: 'rename', id: node.id, name: node.name }
}

/** 이름 칸을 확정했다. Enter 와 칸 벗어나기가 둘 다 부를 수 있어 한 번만 처리한다. */
async function commitFolderEdit(raw) {
  const edit = folderEdit.value
  if (!edit) return
  folderEdit.value = null
  const name = (raw ?? '').trim()
  if (!name) return

  try {
    if (edit.mode === 'create') {
      const created = await memoApi.createFolder(name, edit.parentId)
      await store.load()
      store.revealFolder(created.parentId)
      store.selectFolder(created.id)
    } else if (name !== edit.name) {
      await memoApi.renameFolder(edit.id, name)
      await store.load()
    }
  } catch (error) {
    toast.error(error.message)
  }
}

async function removeFolder(node) {
  folderMenu.value = null
  const message =
    `‘${node.name}’ 폴더를 삭제할까요?\n` +
    '하위 폴더도 함께 사라집니다. 안에 있던 메모는 지워지지 않고 미분류로 나옵니다.'
  if (!window.confirm(message)) return

  try {
    await memoApi.removeFolder(node.id)
    if (store.selectedFolderId === node.id) store.selectFolder(RECENT)
    await store.load()
    // 지운 폴더에 있던 메모는 미분류로 나왔다. 쓰던 본문과 제목은 건드리지 않고 자리만 맞춘다
    if (memo.value) {
      const fresh = await memoApi.get(memo.value.id)
      memo.value = { ...memo.value, folderId: fresh.folderId }
    }
  } catch (error) {
    toast.error(error.message)
  }
}

function selectView(id) {
  folderMenu.value = null
  store.query = ''
  store.selectFolder(id)
  mobilePane.value = 'list'
  // 고른 칸에 지금 메모가 없으면 첫 메모를 연다. 목록과 편집기가 따로 놀지 않게
  const list = store.visibleMemos
  if (list.length && !list.some((m) => m.id === memo.value?.id)) openMemo(list[0].id, { focus: false })
}

async function dropMemo({ memoId, folderId }) {
  try {
    const moved = await memoApi.move(memoId, folderId)
    store.applySaved({ ...moved, content: memo.value?.id === memoId ? memo.value.content : moved.content })
    if (memo.value?.id === memoId) memo.value = { ...memo.value, folderId: moved.folderId }
    await store.load()
    store.revealFolder(folderId)
  } catch (error) {
    toast.error(error.message)
  }
}

async function dropFolder({ folderId, parentId }) {
  try {
    await memoApi.moveFolder(folderId, parentId)
    await store.load()
    store.revealFolder(parentId)
  } catch (error) {
    toast.error(error.message)
  }
}

function onDragMemo(event, item) {
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('application/x-memo-id', String(item.id))
}

function onSideDragOver(event, target) {
  if (!event.dataTransfer.types.includes('application/x-memo-id')) {
    // 폴더는 '미분류'에만 놓을 수 있다 — 최상위로 꺼내는 것
    if (!(target === 'root' && event.dataTransfer.types.includes('application/x-folder-id'))) return
  }
  event.preventDefault()
  dropTarget.value = target
}

/** 사이드바 칸에 떨어뜨렸다. 즐겨찾기는 별, 미분류는 폴더 밖, 휴지통은 지우기. */
function onSideDrop(event, target) {
  event.preventDefault()
  dropTarget.value = null
  const memoId = Number(event.dataTransfer.getData('application/x-memo-id'))
  const folderId = Number(event.dataTransfer.getData('application/x-folder-id'))

  if (target === 'root' && folderId) {
    dropFolder({ folderId, parentId: null })
    return
  }
  if (!memoId) return
  const item = store.memos.find((m) => m.id === memoId)
  if (!item) return

  // 이미 별이 붙어 있으면 아무 일도 없다. 끌어다 놓아 풀리면 놀란다
  if (target === 'favorites' && !item.favorite) toggleFavorite(item)
  else if (target === 'root') dropMemo({ memoId, folderId: null })
  else if (target === 'trash') trashMemo(item)
}

// ── 키보드 ──────────────────────────────────────────────

function isTyping(el) {
  return el instanceof HTMLElement && (el.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName))
}

/**
 * 화면 어디서든 듣는 단축키. 본문 칸이 먼저 받아 처리한 것(defaultPrevented)은 건너뛴다.
 * 제목 칸에서 Ctrl+S 를 눌렀는데 브라우저의 '페이지 저장'이 뜨면 안 된다.
 */
function onDocKey(event) {
  if (event.defaultPrevented || shortcutsOpen.value) return
  const mod = event.ctrlKey || event.metaKey
  const key = event.key.toLowerCase()

  if (mod && key === 's') {
    event.preventDefault()
    if (event.shiftKey) exportText()
    else save()
    return
  }
  if (mod && key === 'n') {
    event.preventDefault()
    createMemo()
    return
  }
  if (mod && (key === 'f' || key === 'h') && memo.value && !trashed.value) {
    event.preventDefault()
    notepad.openFinder(key === 'h')
    return
  }
  if (event.key === 'Escape') {
    if (pop.value || folderMenu.value) {
      pop.value = null
      folderMenu.value = null
      event.preventDefault()
    }
    return
  }
  if (isTyping(event.target) || mod || event.altKey) return
  if (event.key === '/') {
    event.preventDefault()
    searchInput.value?.focus()
    searchInput.value?.select()
  } else if (event.key === '?') {
    shortcutsOpen.value = true
  }
}

function onSearchKey(event) {
  if (event.key === 'Escape' && store.query) {
    event.preventDefault()
    store.query = ''
  } else if (event.key === 'ArrowDown' || event.key === 'Enter') {
    // 검색창에서 바로 결과로 내려간다
    const first = store.visibleMemos[0]
    if (!first) return
    event.preventDefault()
    openMemo(first.id, { focus: event.key === 'Enter' })
    if (event.key === 'ArrowDown') nextTick(() => focusListItem(first.id))
  }
}

/** 목록에서 위아래 화살표. 메일함처럼 옮기는 대로 연다. */
function onListKey(event) {
  if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return
  const list = store.visibleMemos
  const at = list.findIndex((m) => m.id === memo.value?.id)
  const next = list[at + (event.key === 'ArrowDown' ? 1 : -1)]
  if (!next) return
  event.preventDefault()
  openMemo(next.id, { focus: false })
  nextTick(() => focusListItem(next.id))
}

function focusListItem(id) {
  listRef.value?.querySelector(`[data-memo-id="${id}"]`)?.focus()
}

/** 제목에서 Enter 를 치면 본문으로 내려간다 */
function onTitleEnter() {
  editor.value?.focus()
}

/** 바깥을 누르면 열린 작은 창을 닫는다. 창과 그것을 여는 버튼은 data-pop 으로 표시한다 */
function onDocPointer(event) {
  const owner = event.target.closest?.('[data-pop]')?.dataset.pop
  if (pop.value && owner !== pop.value) pop.value = null
  if (folderMenu.value && !event.target.closest?.('.side__tree')) folderMenu.value = null
}

function togglePop(name) {
  pop.value = pop.value === name ? null : name
}

watch(
  () => finder.value.open,
  async (open) => {
    if (!open) return
    await nextTick()
    findInput.value?.focus()
    findInput.value?.select()
  },
)
</script>

<template>
  <div class="memo" :class="`memo--${mobilePane}`">
    <!-- ── 1단: 폴더 ── -->
    <aside class="side" aria-label="메모 폴더">
      <label class="side__search" :class="{ 'side__search--on': store.searching }">
        <AppIcon name="search" :size="15" />
        <input
          ref="searchInput"
          v-model="store.query"
          type="text"
          placeholder="제목·본문 검색"
          aria-label="메모 검색"
          @keydown="onSearchKey"
        />
        <span v-if="store.searching && store.searchLoading" class="spinner side__spin" aria-hidden="true"></span>
        <button
          v-if="store.searching"
          class="side__clear"
          type="button"
          aria-label="검색 지우기 (Esc)"
          @click="store.query = ''"
        >
          <AppIcon name="close" :size="12" />
        </button>
        <kbd v-else aria-hidden="true">/</kbd>
      </label>

      <nav class="side__views" aria-label="보기">
        <button
          class="side__row"
          :class="{ 'side__row--on': activeView === RECENT }"
          type="button"
          :aria-current="activeView === RECENT ? 'true' : undefined"
          @click="selectView(RECENT)"
        >
          <AppIcon name="notes" :size="15" />
          <span>모든 메모</span>
          <span class="side__count">{{ store.memos.length }}</span>
        </button>
        <button
          class="side__row"
          :class="{ 'side__row--on': activeView === FAVORITES, 'side__row--drop': dropTarget === 'favorites' }"
          type="button"
          :aria-current="activeView === FAVORITES ? 'true' : undefined"
          @click="selectView(FAVORITES)"
          @dragover="onSideDragOver($event, 'favorites')"
          @dragleave="dropTarget = null"
          @drop="onSideDrop($event, 'favorites')"
        >
          <AppIcon name="star" :size="15" />
          <span>즐겨찾기</span>
          <span class="side__count">{{ store.favoriteCount || '' }}</span>
        </button>
      </nav>

      <div class="side__head">
        <span class="eyebrow">폴더</span>
        <button class="btn btn--icon side__add" type="button" title="새 폴더" aria-label="새 폴더" @click="startCreateFolder(null)">
          <AppIcon name="plus" :size="14" />
        </button>
      </div>

      <div class="side__tree">
        <!-- 맨 위에 새 폴더를 만드는 중 -->
        <div v-if="folderEdit?.mode === 'create' && folderEdit.parentId == null" class="side__newrow">
          <AppIcon name="folder" :size="15" />
          <input
            v-focus-select
            class="side__input"
            type="text"
            maxlength="100"
            placeholder="새 폴더"
            aria-label="새 폴더 이름"
            @keydown.enter.prevent="commitFolderEdit($event.target.value)"
            @keydown.esc.prevent="folderEdit = null"
            @blur="commitFolderEdit($event.target.value)"
          />
        </div>

        <MemoTree
          :nodes="store.tree"
          :selected-id="activeView"
          :is-open="store.isOpen"
          :edit="folderEdit"
          :menu-id="folderMenu"
          @select="selectView"
          @toggle="store.toggleFolder"
          @menu="folderMenu = $event"
          @rename="startRenameFolder"
          @remove="removeFolder"
          @add-child="startCreateFolder"
          @commit-edit="commitFolderEdit"
          @cancel-edit="folderEdit = null"
          @drop-memo="dropMemo"
          @drop-folder="dropFolder"
        />

        <p v-if="!store.folders.length && !folderEdit" class="side__empty tiny muted">
          폴더가 없습니다. 위의 <AppIcon name="plus" :size="11" class="side__inline" /> 로 만드세요.
        </p>

        <button
          class="side__row side__row--root"
          :class="{ 'side__row--on': activeView === ROOT, 'side__row--drop': dropTarget === 'root' }"
          type="button"
          title="폴더 밖에 놓인 메모. 여기에 끌어다 놓으면 폴더에서 꺼낸다"
          :aria-current="activeView === ROOT ? 'true' : undefined"
          @click="selectView(ROOT)"
          @dragover="onSideDragOver($event, 'root')"
          @dragleave="dropTarget = null"
          @drop="onSideDrop($event, 'root')"
        >
          <AppIcon name="inbox" :size="15" />
          <span>미분류</span>
          <span class="side__count">{{ store.rootCount || '' }}</span>
        </button>
      </div>

      <div class="side__foot">
        <button
          class="side__row"
          :class="{ 'side__row--on': activeView === TRASH, 'side__row--drop': dropTarget === 'trash' }"
          type="button"
          :aria-current="activeView === TRASH ? 'true' : undefined"
          @click="selectView(TRASH)"
          @dragover="onSideDragOver($event, 'trash')"
          @dragleave="dropTarget = null"
          @drop="onSideDrop($event, 'trash')"
        >
          <AppIcon name="trash" :size="15" />
          <span>휴지통</span>
          <span class="side__count">{{ store.trash.length || '' }}</span>
        </button>
        <button class="side__row" type="button" @click="shortcutsOpen = true">
          <AppIcon name="keyboard" :size="15" />
          <span>단축키</span>
          <kbd aria-hidden="true">?</kbd>
        </button>
      </div>
    </aside>

    <!-- ── 2단: 목록 ── -->
    <section class="list" aria-label="메모 목록">
      <header class="list__head">
        <div class="list__crumb tiny">{{ listCrumb }}</div>
        <div class="list__titlebar">
          <h1 class="list__title">{{ listTitle }}</h1>
          <span class="list__count num">{{ store.visibleMemos.length }}</span>
          <span class="list__spacer"></span>
          <button
            v-if="activeView === TRASH && store.trash.length"
            class="btn btn--sm btn--ghost btn--danger"
            type="button"
            @click="emptyTrash"
          >
            비우기
          </button>
          <button
            v-else
            class="btn btn--sm btn--primary list__new"
            type="button"
            title="새 메모 (Ctrl+N)"
            :disabled="busy"
            @click="createMemo()"
          >
            <AppIcon name="plus" :size="14" />
            새 메모
          </button>
        </div>
      </header>

      <div ref="listRef" class="list__body" @keydown="onListKey">
        <p v-if="store.searchError && store.searching" class="list__empty">{{ store.searchError }}</p>
        <p v-else-if="!store.visibleMemos.length" class="list__empty">{{ emptyText }}</p>

        <section v-for="group in store.groups" :key="group.key" class="group">
          <h2 class="group__label eyebrow">{{ group.label }}</h2>
          <button
            v-for="item in group.items"
            :key="item.id"
            class="note"
            :class="{ 'note--on': memo?.id === item.id }"
            type="button"
            :data-memo-id="item.id"
            :aria-current="memo?.id === item.id ? 'true' : undefined"
            :draggable="activeView !== TRASH"
            @dragstart="onDragMemo($event, item)"
            @click="openMemo(item.id)"
          >
            <span class="note__top">
              <span class="note__title">
                <template v-for="(part, i) in splitHits(item.title, store.searching ? store.query : '')" :key="i">
                  <mark v-if="part.hit">{{ part.text }}</mark>
                  <template v-else>{{ part.text }}</template>
                </template>
              </span>
              <AppIcon v-if="item.pinned && !showFolderOnItems" name="pin" :size="12" filled class="note__flag" label="고정됨" />
              <AppIcon v-if="item.favorite" name="star" :size="12" filled class="note__star" label="즐겨찾기" />
              <span class="note__when num">{{ whenLabel(activeView === TRASH ? item.deletedAt : item.updatedAt) }}</span>
            </span>
            <span class="note__preview">
              <template v-for="(part, i) in splitHits(previewOf(item), store.searching ? store.query : '')" :key="i">
                <mark v-if="part.hit">{{ part.text }}</mark>
                <template v-else>{{ part.text }}</template>
              </template>
            </span>
            <span v-if="showFolderOnItems" class="note__folder">
              <AppIcon :name="item.folderId == null ? 'inbox' : 'folder'" :size="12" />
              {{ folderLabel(item.folderId) }}
            </span>
          </button>
        </section>
      </div>
    </section>

    <!-- ── 3단: 편집기 ── -->
    <section v-if="memo" class="pad" aria-label="메모 편집">
      <div class="pad__bar">
        <button class="btn btn--icon pad__back" type="button" aria-label="목록으로" @click="mobilePane = 'list'">
          <AppIcon name="chevron-left" :size="16" />
        </button>

        <div class="pad__where" data-pop="move">
          <button
            class="pad__crumb"
            type="button"
            title="다른 폴더로 옮기기"
            :disabled="trashed"
            :aria-expanded="pop === 'move'"
            @click="togglePop('move')"
          >
            <AppIcon :name="memo.folderId == null ? 'inbox' : 'folder'" :size="14" />
            <span>{{ editorCrumb }}</span>
            <AppIcon v-if="!trashed" name="chevron-down" :size="12" />
          </button>
          <div v-if="pop === 'move'" class="popover popover--left pad__moves" role="menu">
            <p class="popover__label eyebrow">옮길 곳</p>
            <button
              class="popover__item"
              :class="{ 'popover__item--on': memo.folderId == null }"
              type="button"
              role="menuitem"
              @click="changeFolder(null)"
            >
              <AppIcon name="inbox" :size="14" />
              미분류
            </button>
            <button
              v-for="option in folderOptions"
              :key="option.id"
              class="popover__item"
              :class="{ 'popover__item--on': memo.folderId === option.id }"
              type="button"
              role="menuitem"
              @click="changeFolder(option.id)"
            >
              <AppIcon name="folder" :size="14" />
              {{ option.label }}
            </button>
          </div>
        </div>

        <span class="pad__spacer"></span>

        <span class="pad__state" :class="`pad__state--${saveState.tone}`" role="status">
          <AppIcon v-if="saveState.tone === 'ok'" name="check" :size="13" />
          {{ saveState.text }}
          <button v-if="saveError" class="pad__retry" type="button" :title="saveError" @click="save">다시 시도</button>
        </span>

        <template v-if="!trashed">
          <span class="pad__divider" aria-hidden="true"></span>
          <button
            class="btn btn--icon"
            type="button"
            title="찾기 (Ctrl+F)"
            aria-label="찾기 (Ctrl+F)"
            :aria-pressed="finder.open"
            @click="finder.open ? notepad.closeFinder() : notepad.openFinder(false)"
          >
            <AppIcon name="search" :size="16" />
          </button>

          <div class="pad__popwrap" data-pop="view">
            <button
              class="btn btn--icon"
              :class="{ 'is-on': pop === 'view' }"
              type="button"
              title="보기 설정: 글꼴 · 크기 · 줄 바꿈 · 색"
              aria-label="보기 설정"
              :aria-expanded="pop === 'view'"
              @click="togglePop('view')"
            >
              <AppIcon name="type" :size="17" />
            </button>

            <div v-if="pop === 'view'" class="popover view" role="dialog" aria-label="보기 설정">
              <p class="popover__label eyebrow">글꼴</p>
              <div class="view__fonts">
                <button
                  v-for="font in FONTS"
                  :key="font.key"
                  class="view__font"
                  :class="{ 'view__font--on': fontKey === font.key }"
                  type="button"
                  :aria-pressed="fontKey === font.key"
                  @click="setStyle('fontFamily', font.value || null)"
                >
                  <span class="view__sample" :style="{ fontFamily: font.value || 'var(--font)' }">가</span>
                  <span class="view__name">{{ font.label }}</span>
                </button>
              </div>

              <div class="view__row">
                <span>글자 크기</span>
                <span class="view__stepper">
                  <button type="button" aria-label="작게" @click="stepSize(-1)"><AppIcon name="minus" :size="12" /></button>
                  <span class="num">{{ fontSize }}px</span>
                  <button type="button" aria-label="크게" @click="stepSize(1)"><AppIcon name="plus" :size="12" /></button>
                </span>
              </div>

              <div class="view__row">
                <span id="wrap-label">자동 줄 바꿈</span>
                <button
                  class="switch"
                  :class="{ 'switch--on': memo.wordWrap !== false }"
                  type="button"
                  role="switch"
                  aria-labelledby="wrap-label"
                  :aria-checked="memo.wordWrap !== false"
                  @click="setStyle('wordWrap', memo.wordWrap === false)"
                ></button>
              </div>

              <div class="view__row">
                <span>글자 색</span>
                <span class="view__colors">
                  <button
                    v-for="color in COLORS"
                    :key="color.label"
                    class="view__color"
                    :class="{ 'view__color--on': (memo.fontColor ?? null) === color.key }"
                    type="button"
                    :title="color.label"
                    :aria-label="color.label"
                    :aria-pressed="(memo.fontColor ?? null) === color.key"
                    :style="{ background: color.css }"
                    @click="setStyle('fontColor', color.key)"
                  ></button>
                </span>
              </div>

              <p class="view__note">이 메모에만 적용됩니다. 색은 다크 모드에서도 읽히는 것만 고를 수 있습니다.</p>
            </div>
          </div>

          <button
            class="btn btn--icon"
            type="button"
            :title="memo.pinned ? '고정 해제' : '이 폴더 맨 위에 고정'"
            :aria-label="memo.pinned ? '고정 해제' : '이 폴더 맨 위에 고정'"
            :aria-pressed="memo.pinned"
            @click="togglePin(memo)"
          >
            <AppIcon name="pin" :size="16" :filled="memo.pinned" />
          </button>
          <button
            class="btn btn--icon pad__star"
            :class="{ 'pad__star--on': memo.favorite }"
            type="button"
            :title="memo.favorite ? '즐겨찾기 해제' : '즐겨찾기에 담기'"
            :aria-label="memo.favorite ? '즐겨찾기 해제' : '즐겨찾기에 담기'"
            :aria-pressed="memo.favorite"
            @click="toggleFavorite(memo)"
          >
            <AppIcon name="star" :size="16" :filled="memo.favorite" />
          </button>

          <div class="pad__popwrap" data-pop="more">
            <button
              class="btn btn--icon"
              :class="{ 'is-on': pop === 'more' }"
              type="button"
              title="더 보기"
              aria-label="더 보기: txt 내보내기, 휴지통으로"
              :aria-expanded="pop === 'more'"
              @click="togglePop('more')"
            >
              <AppIcon name="more" :size="16" />
            </button>
            <div v-if="pop === 'more'" class="popover" role="menu">
              <button class="popover__item" type="button" role="menuitem" @click="exportText">
                <AppIcon name="download" :size="14" />
                txt 로 내보내기
                <kbd class="popover__kbd">Ctrl+Shift+S</kbd>
              </button>
              <button class="popover__item popover__item--danger" type="button" role="menuitem" @click="trashMemo()">
                <AppIcon name="trash" :size="14" />
                휴지통으로
              </button>
            </div>
          </div>
        </template>
      </div>

      <!-- 찾기 / 바꾸기 -->
      <div v-if="finder.open" class="find" role="search">
        <div class="find__row">
          <label class="find__field">
            <AppIcon name="search" :size="14" />
            <input
              ref="findInput"
              v-model="finder.query"
              type="text"
              placeholder="이 메모에서 찾기"
              aria-label="이 메모에서 찾기"
              @keydown.enter.prevent="notepad.search($event.shiftKey)"
              @keydown.esc.prevent="notepad.closeFinder()"
            />
            <span class="find__count num">{{ findCount }}</span>
          </label>
          <button class="btn btn--icon" type="button" aria-label="이전 찾기 (Shift+F3)" title="이전 (Shift+F3)" @click="notepad.search(true)">
            <AppIcon name="arrow-up" :size="15" />
          </button>
          <button class="btn btn--icon" type="button" aria-label="다음 찾기 (F3)" title="다음 (F3)" @click="notepad.search(false)">
            <AppIcon name="arrow-down" :size="15" />
          </button>
          <span class="pad__divider" aria-hidden="true"></span>
          <button
            class="find__toggle"
            :class="{ 'find__toggle--on': finder.matchCase }"
            type="button"
            title="대소문자 구분"
            :aria-pressed="finder.matchCase"
            @click="finder.matchCase = !finder.matchCase"
          >
            Aa
          </button>
          <button
            class="btn btn--sm btn--ghost"
            type="button"
            :aria-expanded="Boolean(finder.replace)"
            @click="finder.replace = !finder.replace"
          >
            바꾸기
            <AppIcon :name="finder.replace ? 'chevron-down' : 'chevron-right'" :size="11" />
          </button>
          <span v-if="finder.message" class="find__msg tiny">{{ finder.message }}</span>
          <span class="pad__spacer"></span>
          <span class="find__hint tiny">Esc 로 닫기</span>
          <button class="btn btn--icon" type="button" aria-label="찾기 닫기" @click="notepad.closeFinder()">
            <AppIcon name="close" :size="13" />
          </button>
        </div>
        <div v-if="finder.replace" class="find__row">
          <label class="find__field">
            <AppIcon name="pencil" :size="14" />
            <input
              v-model="finder.replacement"
              type="text"
              placeholder="바꿀 내용"
              aria-label="바꿀 내용"
              @keydown.enter.prevent="notepad.replaceOne()"
              @keydown.esc.prevent="notepad.closeFinder()"
            />
          </label>
          <button class="btn btn--sm" type="button" @click="notepad.replaceOne()">바꾸기</button>
          <button class="btn btn--sm" type="button" @click="notepad.replaceEvery()">모두 바꾸기</button>
        </div>
      </div>

      <div v-if="trashed" class="pad__trash">
        <AppIcon name="trash" :size="15" />
        <span>휴지통에 있는 메모입니다. 고치려면 먼저 되살리세요.</span>
        <span class="pad__spacer"></span>
        <button class="btn btn--sm" type="button" @click="restoreMemo(memo.id)">
          <AppIcon name="restore" :size="14" />
          되살리기
        </button>
        <button class="btn btn--sm btn--ghost btn--danger" type="button" @click="purgeMemo">영구 삭제</button>
      </div>

      <div class="pad__doc">
        <input
          ref="titleInput"
          v-model="memo.title"
          class="pad__title"
          type="text"
          maxlength="200"
          placeholder="제목"
          aria-label="제목"
          :readonly="trashed"
          @input="touched"
          @keydown.enter.prevent="onTitleEnter"
        />
        <p class="pad__meta">
          고친 날 <span class="num">{{ fullDate(memo.updatedAt) }}</span>
          <template v-if="memo.createdAt"> · 만든 날 {{ fullDate(memo.createdAt).replace(/ \d\d:\d\d$/, '') }}</template>
        </p>

        <div class="pad__body">
          <div
            v-if="showBackdrop"
            ref="backdrop"
            class="pad__text pad__backdrop"
            :class="{ 'pad__text--nowrap': memo.wordWrap === false }"
            :style="editorStyle"
            aria-hidden="true"
          ><template v-for="(seg, i) in backdropSegs" :key="i"><mark v-if="seg.hit" :class="{ cur: seg.cur }">{{ seg.text }}</mark><template v-else>{{ seg.text }}</template></template></div>
          <textarea
            ref="editor"
            v-model="memo.content"
            class="pad__text pad__area"
            :class="{ 'pad__text--nowrap': memo.wordWrap === false }"
            :style="editorStyle"
            :wrap="memo.wordWrap === false ? 'off' : 'soft'"
            :readonly="trashed"
            spellcheck="false"
            aria-label="본문"
            placeholder="여기에 적습니다. Tab 은 4칸, F5 는 날짜와 시각."
            @input="touched"
            @scroll="syncBackdrop"
            @keydown="notepad.onKeydown"
            @copy="notepad.onCopy"
            @cut="notepad.onCut"
            @click="notepad.sync"
            @keyup="notepad.sync"
            @select="notepad.sync"
          ></textarea>
        </div>
      </div>

      <footer class="pad__status num">
        <span>줄 {{ status.line }}, 칸 {{ status.column }}</span>
        <span v-if="status.selected">고름 {{ status.selected }}자</span>
        <span>{{ stats.chars }}자 · {{ stats.lines }}줄</span>
        <span class="pad__spacer"></span>
        <span>{{ viewLabel }}</span>
      </footer>
    </section>

    <section v-else class="pad pad--empty">
      <AppIcon name="notes" :size="28" class="pad__emptyicon" />
      <p class="muted">왼쪽에서 메모를 고르거나 새로 만드세요.</p>
      <button class="btn btn--primary" type="button" @click="createMemo()">
        <AppIcon name="plus" :size="14" />
        새 메모
      </button>
    </section>

    <BaseModal v-if="shortcutsOpen" title="단축키" subtitle="메모장 단축키를 그대로 씁니다." width="520px" @close="shortcutsOpen = false">
      <dl class="keys">
        <template v-for="[keys, what] in SHORTCUTS" :key="keys">
          <dt><kbd v-for="k in keys.split(' / ')" :key="k">{{ k }}</kbd></dt>
          <dd>{{ what }}</dd>
        </template>
      </dl>
    </BaseModal>
  </div>
</template>

<style scoped>
/*
 * 머리글 아래를 가장자리까지 쓴다. 세 칸은 면의 밝기로만 나뉜다:
 * 폴더(바탕) → 목록(한 단 앞) → 편집기(맨 앞).
 */
.memo {
  display: grid;
  grid-template-columns: 232px 320px minmax(0, 1fr);
  height: calc(100vh - var(--header-h));
  min-height: 480px;
  background: var(--bg);
}

/* ---------- 1단: 폴더 ---------- */
.side {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 12px 10px 10px;
  border-right: 1px solid var(--line);
  background: var(--bg);
}

.side__search {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 6px 0 10px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text-3);
  transition: border-color 0.12s, box-shadow 0.12s;
}

.side__search:focus-within,
.side__search--on {
  border-color: var(--brand);
  box-shadow: var(--ring);
}

.side__search input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--text);
  font-size: 13px;
}

.side__search input::placeholder {
  color: var(--text-3);
}

.side__spin {
  width: 12px;
  height: 12px;
}

.side__clear {
  display: inline-grid;
  place-items: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: 0;
  border-radius: var(--radius-xs);
  background: var(--surface-sel);
  color: var(--text-2);
}

.side__views,
.side__foot {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.side__views {
  margin-top: 12px;
}

.side__row {
  display: flex;
  align-items: center;
  gap: 9px;
  height: 30px;
  padding: 0 8px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--text-2);
  font-size: 13px;
  font-weight: 500;
  text-align: left;
}

.side__row :deep(.icon) {
  color: var(--text-3);
}

.side__row > span:nth-of-type(1) {
  flex: 1;
}

.side__row:hover {
  background: var(--surface-hover);
}

.side__row--on,
.side__row--on:hover {
  background: var(--surface-sel);
  color: var(--text);
  font-weight: 600;
}

.side__row--on :deep(.icon) {
  color: var(--text);
}

.side__row--drop,
.side__row--drop:hover {
  background: var(--brand-soft);
  box-shadow: inset 0 0 0 1px var(--brand);
  color: var(--brand-strong);
}

.side__row--root {
  margin-top: 6px;
  padding-left: 26px;
}

.side__count {
  color: var(--text-3);
  font-family: var(--font-num);
  font-size: 11.5px;
  font-weight: 400;
}

.side__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 2px 4px 8px;
}

.side__add {
  width: 24px;
  height: 24px;
}

.side__tree {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-bottom: 8px;
}

.side__newrow {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 30px;
  padding: 0 4px 0 20px;
  color: var(--text-3);
}

.side__input {
  flex: 1;
  min-width: 0;
  height: 24px;
  padding: 0 6px;
  border: 1px solid var(--brand);
  border-radius: var(--radius-xs);
  outline: none;
  background: var(--surface);
  box-shadow: var(--ring);
  color: var(--text);
  font-size: 13px;
}

.side__empty {
  margin: 0;
  padding: 10px 8px;
  line-height: 1.6;
}

.side__inline {
  display: inline-block;
  vertical-align: -1px;
}

.side__foot {
  padding-top: 8px;
  border-top: 1px solid var(--line);
}

/* ---------- 2단: 목록 ---------- */
.list {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  border-right: 1px solid var(--line);
  background: var(--surface-2);
}

.list__head {
  flex: none;
  padding: 14px 14px 6px 18px;
}

.list__crumb {
  min-height: 18px;
  overflow: hidden;
  color: var(--text-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.list__titlebar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.list__title {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  font-size: 17px;
  font-weight: 600;
  line-height: 1.35;
  letter-spacing: -0.02em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.list__count {
  flex: none;
  color: var(--text-3);
}

.list__spacer,
.pad__spacer {
  flex: 1;
}

.list__new {
  flex: none;
  padding-left: 8px;
}

.list__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 8px 16px;
}

.list__empty {
  margin: 0;
  padding: 48px 20px;
  color: var(--text-3);
  text-align: center;
  line-height: 1.6;
}

.group__label {
  margin: 0;
  padding: 14px 10px 5px;
}

.note {
  display: block;
  width: 100%;
  margin-bottom: 1px;
  padding: 9px 10px 10px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  text-align: left;
}

.note:hover {
  background: var(--surface-hover);
}

.note:focus-visible {
  outline: none;
  box-shadow: var(--ring);
}

/* 고른 메모만 초록 바탕. 사이드바의 고른 칸(회색)보다 한 단계 앞이다 */
.note--on,
.note--on:hover {
  background: var(--brand-soft);
}

.note__top {
  display: flex;
  align-items: center;
  gap: 6px;
}

.note__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--text);
  font-size: 13.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note--on .note__title {
  color: var(--brand-strong);
}

.note__flag {
  color: var(--text-3);
}

.note__star {
  color: var(--star);
}

.note__when {
  flex: none;
  color: var(--text-3);
  font-size: 11px;
}

.note__preview {
  display: -webkit-box;
  margin-top: 3px;
  overflow: hidden;
  color: var(--text-3);
  font-size: 12.5px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  word-break: break-all;
}

.note__folder {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 6px;
  color: var(--text-3);
  font-size: 11.5px;
}

.note--on .note__when,
.note--on .note__preview,
.note--on .note__folder,
.note--on .note__flag {
  color: var(--brand-strong);
  opacity: 0.85;
}

/* ---------- 3단: 편집기 ---------- */
.pad {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: var(--surface);
}

.pad--empty {
  align-items: center;
  justify-content: center;
  gap: 12px;
  text-align: center;
}

.pad__emptyicon {
  color: var(--text-4);
}

.pad__bar {
  display: flex;
  flex: none;
  align-items: center;
  gap: 4px;
  height: 44px;
  padding: 0 12px 0 10px;
  border-bottom: 1px solid var(--line);
}

.pad__back {
  display: none;
}

.pad__where,
.pad__popwrap {
  position: relative;
  min-width: 0;
}

.pad__crumb {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  height: 28px;
  padding: 0 8px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--text-3);
  font-size: 12.5px;
}

.pad__crumb span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pad__crumb:hover:not(:disabled) {
  background: var(--surface-hover);
  color: var(--text);
}

.pad__crumb:disabled {
  cursor: default;
}

.pad__state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-right: 6px;
  color: var(--text-3);
  font-size: 12px;
  white-space: nowrap;
}

.pad__state--ok :deep(.icon) {
  color: var(--brand);
}

.pad__state--bad {
  color: var(--danger);
  font-weight: 600;
}

.pad__retry {
  padding: 2px 6px;
  border: 1px solid var(--danger-border);
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--danger);
  font-size: 12px;
}

.pad__divider {
  flex: none;
  width: 1px;
  height: 16px;
  margin: 0 4px;
  background: var(--line);
}

/* 별은 켜졌는지를 색으로 말한다. 다른 켜진 버튼처럼 회색 바탕을 깔면 별이 묻힌다 */
.btn--icon.pad__star--on,
.btn--icon.pad__star--on:hover:not(:disabled) {
  background: transparent;
  color: var(--star);
}

/* ---------- 작은 창 ---------- */
.popover {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 30;
  min-width: 200px;
  padding: 4px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: var(--shadow-3);
}

.popover--left {
  right: auto;
  left: 0;
}

.popover__label {
  margin: 0;
  padding: 8px 8px 6px;
}

.popover__item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 7px 8px;
  border: 0;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--text);
  font-size: 13px;
  text-align: left;
  white-space: nowrap;
}

.popover__item:hover {
  background: var(--surface-hover);
}

.popover__item--on {
  background: var(--surface-sel);
  font-weight: 600;
}

.popover__item--danger {
  color: var(--danger);
}

.popover__item--danger:hover {
  background: var(--danger-soft);
}

.popover__kbd {
  margin-left: auto;
}

.pad__moves {
  max-height: 340px;
  min-width: 240px;
  overflow-y: auto;
}

.view {
  width: 296px;
  padding: 14px 14px 12px;
}

.view .popover__label {
  padding: 0 0 8px;
}

.view__fonts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}

.view__font {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 8px 0 7px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text);
}

.view__font:hover {
  background: var(--surface-hover);
}

.view__font--on,
.view__font--on:hover {
  border-color: var(--brand);
  background: var(--brand-soft);
}

.view__sample {
  font-size: 20px;
  line-height: 1.2;
}

.view__name {
  color: var(--text-3);
  font-size: 11.5px;
}

.view__font--on .view__name {
  color: var(--brand-strong);
}

.view__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 30px;
  margin-top: 8px;
  color: var(--text-2);
  font-size: 12.5px;
}

.view__fonts + .view__row {
  margin-top: 14px;
}

.view__stepper {
  display: inline-flex;
  align-items: center;
  overflow: hidden;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-sm);
}

.view__stepper button {
  display: inline-grid;
  place-items: center;
  width: 28px;
  height: 26px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-2);
}

.view__stepper button:hover {
  background: var(--surface-hover);
}

.view__stepper .num {
  min-width: 46px;
  border-right: 1px solid var(--line);
  border-left: 1px solid var(--line);
  color: var(--text);
  line-height: 26px;
  text-align: center;
}

.switch {
  position: relative;
  width: 34px;
  height: 20px;
  padding: 0;
  border: 0;
  border-radius: 999px;
  background: var(--line-strong);
  transition: background 0.12s;
}

.switch::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
  transition: transform 0.12s;
}

.switch--on {
  background: var(--brand);
}

.switch--on::after {
  transform: translateX(14px);
}

.view__colors {
  display: inline-flex;
  gap: 8px;
}

.view__color {
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.12);
}

.view__color--on {
  box-shadow: 0 0 0 2px var(--surface), 0 0 0 3.5px var(--text);
}

.view__note {
  margin: 12px 0 0;
  padding-top: 10px;
  border-top: 1px solid var(--line);
  color: var(--text-3);
  font-size: 11.5px;
  line-height: 1.55;
}

/* ---------- 찾기 ---------- */
.find {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: 6px;
  padding: 7px 12px;
  border-bottom: 1px solid var(--line);
  background: var(--surface-2);
}

.find__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.find__field {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 280px;
  max-width: 100%;
  height: 28px;
  margin-right: 4px;
  padding: 0 8px;
  border: 1px solid var(--line-strong);
  border-radius: 5px;
  background: var(--surface);
  color: var(--text-3);
}

.find__field:focus-within {
  border-color: var(--brand);
  box-shadow: var(--ring);
}

.find__field input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--text);
  font-size: 13px;
}

.find__count {
  flex: none;
  color: var(--text-3);
  font-size: 11.5px;
}

.find__toggle {
  height: 26px;
  padding: 0 8px;
  border: 1px solid var(--line-strong);
  border-radius: 5px;
  background: var(--surface);
  color: var(--text-2);
  font-size: 12px;
}

.find__toggle--on {
  border-color: var(--text);
  background: var(--surface-sel);
  color: var(--text);
  font-weight: 600;
}

.find__msg {
  margin-left: 6px;
  color: var(--text-2);
  font-weight: 600;
}

.find__hint {
  color: var(--text-3);
}

/* ---------- 휴지통 띠 ---------- */
.pad__trash {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--line);
  background: var(--surface-2);
  color: var(--text-2);
  font-size: 12.5px;
}

.pad__trash > :first-child {
  color: var(--text-3);
}

/* ---------- 본문 ---------- */
/*
 * 글은 가운데 760px 단에 놓고, 스크롤 막대는 편집기 오른쪽 끝에 둔다.
 * 단을 상자로 감싸면 막대가 글 옆에 붙어 화면 한가운데 떠 있게 된다 —
 * 대신 좌우 여백을 넓혀 가운데로 모은다.
 */
.pad__doc {
  --gutter: max(28px, calc((100% - 760px) / 2));
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding-top: 32px;
}

.pad__title {
  width: 100%;
  padding: 0 var(--gutter);
  border: 0;
  outline: none;
  background: transparent;
  color: var(--text);
  font-size: 24px;
  font-weight: 600;
  line-height: 1.3;
  letter-spacing: -0.025em;
}

.pad__title::placeholder {
  color: var(--text-4);
}

.pad__meta {
  margin: 6px 0 14px;
  padding: 0 var(--gutter);
  color: var(--text-3);
  font-size: 12px;
}

.pad__body {
  position: relative;
  flex: 1;
  min-height: 200px;
}

/* 본문 칸과 뒤에 까는 판은 글자 하나 어긋나면 안 된다. 같은 값을 한 곳에서 준다 */
.pad__text {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 10px var(--gutter) 64px;
  border: 0;
  font-weight: 400;
  line-height: 1.75;
  letter-spacing: 0;
  tab-size: 4;
  white-space: pre-wrap;
  overflow-wrap: break-word;
  word-break: normal;
  scrollbar-gutter: stable;
}

.pad__text--nowrap {
  white-space: pre;
  overflow-wrap: normal;
}

.pad__area {
  overflow: auto;
  background: transparent;
  outline: none;
  resize: none;
}

.pad__area[readonly] {
  color: var(--text-3) !important;
}

/* 뒤판이 깔리면 본문 칸 바탕은 비워 둔다. 글자는 본문 칸이, 칠은 뒤판이 맡는다 */
.pad__backdrop {
  overflow: hidden;
  color: transparent !important;
  pointer-events: none;
}

.pad__backdrop mark {
  color: transparent;
}

.pad__backdrop mark.cur {
  background: var(--mark-current);
  box-shadow: 0 0 0 1px var(--mark-current);
}

.pad__status {
  display: flex;
  flex: none;
  align-items: center;
  gap: 16px;
  height: 28px;
  overflow: hidden;
  padding: 0 16px;
  border-top: 1px solid var(--line);
  color: var(--text-3);
  font-size: 11px;
  white-space: nowrap;
}

/* ---------- 단축키 ---------- */
.keys {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 8px 18px;
  margin: 0;
  font-size: 13px;
}

.keys dt {
  display: flex;
  gap: 4px;
  white-space: nowrap;
}

.keys dd {
  margin: 0;
  color: var(--text-2);
}

/* ---------- 좁은 화면 ---------- */
/* 세 칸을 늘어놓을 수 없다. 폴더+목록과 편집기를 번갈아 보여준다 */
@media (max-width: 1100px) {
  .memo {
    grid-template-columns: 210px 280px minmax(0, 1fr);
  }
}

@media (max-width: 900px) {
  .memo {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: auto minmax(0, 1fr);
    height: auto;
    min-height: calc(100vh - var(--header-h));
  }

  .side {
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }

  .side__tree {
    max-height: 30vh;
  }

  .list {
    min-height: 50vh;
    border-right: 0;
  }

  .pad {
    min-height: calc(100vh - var(--header-h));
  }

  .memo--list .pad {
    display: none;
  }

  .memo--editor .side,
  .memo--editor .list {
    display: none;
  }

  .pad__back {
    display: inline-flex;
  }

  .pad__state {
    display: none;
  }
}
</style>
