import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import { memoApi } from '@/api/client'

const OPEN_KEY = 'weekly-report:memo-open-folders'
const LAST_KEY = 'weekly-report:last-memo'

/*
 * 사이드바에서 고를 수 있는 것. 폴더 id(숫자) 말고는 아래 넷이다.
 * null 은 "아무것도 안 고름"과 겹쳐 쓸 수 없어 문자열로 둔다.
 */

/** 모든 메모. 폴더와 상관없이 최근에 고친 순. 처음 열면 여기다. */
export const RECENT = 'recent'

/** 폴더 밖에 놓인 메모(미분류). */
export const ROOT = 'root'

/** 폴더가 아니라 '즐겨찾기한 것만' 이라는 뜻. */
export const FAVORITES = 'favorites'

/** 휴지통. 30일이 지나면 서버가 정리한다. */
export const TRASH = 'trash'

/** 검색어를 치다 멈추면 이만큼 기다렸다 찾는다. 한 글자마다 서버를 부르지 않게. */
const SEARCH_DELAY_MS = 220

export const useMemoStore = defineStore('memo', () => {
  const folders = ref([])
  const memos = ref([])
  const trash = ref([])
  const loading = ref(false)

  const selectedFolderId = ref(RECENT)
  const openFolderIds = ref(new Set(readOpen()))
  const query = ref('')

  // ── 트리 ─────────────────────────────────────────────────

  /** 평평한 목록을 트리로 조립한다. 서버는 부모 id 만 주고 모양은 화면이 만든다. */
  const tree = computed(() => {
    const byId = new Map(folders.value.map((f) => [f.id, { ...f, children: [] }]))
    const roots = []
    for (const node of byId.values()) {
      const parent = node.parentId == null ? null : byId.get(node.parentId)
      // 부모가 사라진 폴더는 최상위로 올려 보여준다. 안 보이면 지울 수도 없다.
      if (parent) parent.children.push(node)
      else roots.push(node)
    }
    const sort = (list) => {
      list.sort((a, b) => a.name.localeCompare(b.name, 'ko'))
      list.forEach((node) => sort(node.children))
    }
    sort(roots)
    return roots
  })

  const folderById = computed(() => new Map(folders.value.map((f) => [f.id, f])))

  function pathOf(folderId) {
    const names = []
    let cursor = folderById.value.get(folderId)
    while (cursor) {
      names.unshift(cursor.name)
      cursor = cursor.parentId == null ? null : folderById.value.get(cursor.parentId)
    }
    return names
  }

  // ── 찾기 ─────────────────────────────────────────────────

  const searchHits = ref([])
  const searchLoading = ref(false)
  const searchError = ref('')
  let searchTimer = null
  let searchCtrl = null

  const searching = computed(() => query.value.trim().length > 0)

  /**
   * 제목과 본문에서 찾는다. 서버에 묻는다 — 목록에는 본문 첫 줄밖에 없다.
   * 앞 검색이 늦게 도착해 새 결과를 덮지 않도록, 새로 찾을 때 앞 것을 끊는다.
   */
  async function runSearch() {
    const q = query.value.trim()
    searchCtrl?.abort()
    if (!q) {
      searchHits.value = []
      searchLoading.value = false
      return
    }
    const ctrl = new AbortController()
    searchCtrl = ctrl
    searchLoading.value = true
    try {
      const hits = await memoApi.search(q, ctrl.signal)
      if (ctrl.signal.aborted) return
      searchHits.value = hits
      searchError.value = ''
    } catch (error) {
      if (error.canceled || ctrl.signal.aborted) return
      searchError.value = error.message
    } finally {
      if (searchCtrl === ctrl) searchLoading.value = false
    }
  }

  watch(query, () => {
    clearTimeout(searchTimer)
    if (!query.value.trim()) {
      runSearch()
      return
    }
    searchLoading.value = true
    searchTimer = setTimeout(runSearch, SEARCH_DELAY_MS)
  })

  /** 저장해서 내용이 바뀌었으면 결과도 다시 받는다. 찾고 있지 않으면 아무 일도 없다. */
  function refreshSearch() {
    if (!searching.value) return
    clearTimeout(searchTimer)
    searchTimer = setTimeout(runSearch, SEARCH_DELAY_MS)
  }

  // ── 목록 ─────────────────────────────────────────────────

  /**
   * 가운데 칸에 띄울 메모를 묶음째로.
   *
   * 폴더를 열면 고정한 것이 맨 위에 '고정됨'으로, 나머지는 고친 날짜대로
   * 오늘 · 이번 주 · 지난주 · 달별로 묶는다. "3일 전" 같은 말을 줄마다 다는 것보다
   * 언제쯤 쓴 것인지가 한눈에 들어온다.
   */
  const groups = computed(() => {
    if (searching.value) {
      const items = searchHits.value.map((hit) => ({ ...hit.memo, snippet: hit.snippet, inTitle: hit.inTitle }))
      return [
        { key: 'title', label: '제목에 있음', items: items.filter((m) => m.inTitle) },
        { key: 'body', label: '본문에 있음', items: items.filter((m) => !m.inTitle) },
      ].filter((g) => g.items.length)
    }

    const view = selectedFolderId.value
    if (view === TRASH) {
      return trash.value.length
        ? [{ key: 'trash', label: '30일이 지나면 영구히 지워집니다', items: trash.value }]
        : []
    }
    if (view === RECENT) return byDate(memos.value)
    // 즐겨찾기는 여러 폴더에서 모아 온 것이라 '폴더 안에서 맨 위'가 뜻을 갖지 못한다
    if (view === FAVORITES) return byDate(memos.value.filter((m) => m.favorite))

    const inFolder = memos.value.filter((m) => (view === ROOT ? m.folderId == null : m.folderId === view))
    const pinned = inFolder.filter((m) => m.pinned)
    return [
      ...(pinned.length ? [{ key: 'pinned', label: '고정됨', items: pinned }] : []),
      ...byDate(inFolder.filter((m) => !m.pinned)),
    ]
  })

  /** 묶음을 펼친 순서 그대로. 위아래 화살표와 '지운 뒤 다음 메모'가 쓴다. */
  const visibleMemos = computed(() => groups.value.flatMap((g) => g.items))

  const rootCount = computed(() => memos.value.filter((m) => m.folderId == null).length)
  const favoriteCount = computed(() => memos.value.filter((m) => m.favorite).length)

  // ── 읽기 ─────────────────────────────────────────────────

  async function load() {
    loading.value = true
    try {
      const data = await memoApi.workspace()
      folders.value = data.folders ?? []
      memos.value = data.memos ?? []
      trash.value = data.trash ?? []
    } finally {
      loading.value = false
    }
    return memos.value
  }

  /** 저장·옮기기 뒤 목록만 최신으로. 편집 중인 본문은 건드리지 않는다. */
  function applySaved(saved) {
    const summary = {
      id: saved.id,
      folderId: saved.folderId,
      title: saved.title,
      preview: firstLine(saved.content),
      chars: (saved.content ?? '').length,
      pinned: saved.pinned,
      favorite: saved.favorite,
      updatedAt: saved.updatedAt,
      deletedAt: saved.deletedAt ?? null,
    }
    memos.value = memos.value.filter((m) => m.id !== saved.id)
    trash.value = trash.value.filter((m) => m.id !== saved.id)
    // 고친 순으로 서 있어야 한다. 방금 고친 것은 맨 앞이다
    if (summary.deletedAt) trash.value = [summary, ...trash.value]
    else memos.value = [summary, ...memos.value].sort(byUpdatedDesc)
  }

  /** 휴지통으로 옮겼다. 서버에 다시 묻지 않고 그 자리에서 옮겨 둔다. */
  function trashLocally(id) {
    const item = memos.value.find((m) => m.id === id)
    if (!item) return
    memos.value = memos.value.filter((m) => m.id !== id)
    trash.value = [{ ...item, deletedAt: new Date().toISOString() }, ...trash.value]
    searchHits.value = searchHits.value.filter((hit) => hit.memo.id !== id)
  }

  function forget(id) {
    memos.value = memos.value.filter((m) => m.id !== id)
    trash.value = trash.value.filter((m) => m.id !== id)
    searchHits.value = searchHits.value.filter((hit) => hit.memo.id !== id)
  }

  function clearTrashLocally() {
    trash.value = []
  }

  // ── 폴더 펼침 상태 ───────────────────────────────────────

  function isOpen(id) {
    return openFolderIds.value.has(id)
  }

  function toggleFolder(id) {
    const next = new Set(openFolderIds.value)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    openFolderIds.value = next
    write(OPEN_KEY, [...next])
  }

  /** 메모를 열 때 그 메모가 든 폴더까지 펼쳐 준다. */
  function revealFolder(folderId) {
    if (folderId == null) return
    const next = new Set(openFolderIds.value)
    let cursor = folderById.value.get(folderId)
    while (cursor) {
      next.add(cursor.id)
      cursor = cursor.parentId == null ? null : folderById.value.get(cursor.parentId)
    }
    openFolderIds.value = next
    write(OPEN_KEY, [...next])
  }

  function selectFolder(id) {
    selectedFolderId.value = id
  }

  // ── 마지막으로 보던 메모 ─────────────────────────────────

  function rememberMemo(id) {
    write(LAST_KEY, id)
  }

  function lastMemoId() {
    const raw = read(LAST_KEY)
    return typeof raw === 'number' ? raw : null
  }

  function reset() {
    folders.value = []
    memos.value = []
    trash.value = []
    query.value = ''
    searchHits.value = []
    selectedFolderId.value = RECENT
  }

  return {
    folders,
    memos,
    trash,
    loading,
    tree,
    folderById,
    groups,
    visibleMemos,
    searching,
    searchLoading,
    searchError,
    rootCount,
    favoriteCount,
    query,
    selectedFolderId,
    pathOf,
    load,
    applySaved,
    trashLocally,
    clearTrashLocally,
    forget,
    refreshSearch,
    isOpen,
    toggleFolder,
    revealFolder,
    selectFolder,
    rememberMemo,
    lastMemoId,
    reset,
  }
})

// ── 날짜 묶음 ───────────────────────────────────────────────

/**
 * 고친 날짜로 묶는다. 한 주는 월요일에 시작한다 — 주간보고와 같은 달력이다.
 * 지난주보다 오래된 것은 달 단위로 모은다. 올해가 아니면 연도를 붙인다.
 */
function byDate(list) {
  const now = new Date()
  const today = startOfDay(now)
  const thisMonday = new Date(today)
  thisMonday.setDate(today.getDate() - ((today.getDay() + 6) % 7))
  const lastMonday = new Date(thisMonday)
  lastMonday.setDate(thisMonday.getDate() - 7)

  const out = []
  const push = (key, label, memo) => {
    let group = out.find((g) => g.key === key)
    if (!group) {
      group = { key, label, items: [] }
      out.push(group)
    }
    group.items.push(memo)
  }

  for (const memo of [...list].sort(byUpdatedDesc)) {
    const at = new Date(memo.updatedAt)
    if (at >= today) push('today', '오늘', memo)
    else if (at >= thisMonday) push('week', '이번 주', memo)
    else if (at >= lastMonday) push('last-week', '지난주', memo)
    else {
      const sameYear = at.getFullYear() === now.getFullYear()
      const label = sameYear ? `${at.getMonth() + 1}월` : `${at.getFullYear()}년 ${at.getMonth() + 1}월`
      push(`m-${at.getFullYear()}-${at.getMonth()}`, label, memo)
    }
  }
  return out
}

function startOfDay(date) {
  const d = new Date(date)
  d.setHours(0, 0, 0, 0)
  return d
}

function byUpdatedDesc(a, b) {
  return new Date(b.updatedAt) - new Date(a.updatedAt)
}

function firstLine(content) {
  if (!content || !content.trim()) return ''
  const line = content.trim().split('\n')[0]
  return line.length > 80 ? line.slice(0, 80) + '…' : line
}

function read(key) {
  try {
    const raw = localStorage.getItem(key)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function readOpen() {
  const stored = read(OPEN_KEY)
  return Array.isArray(stored) ? stored : []
}

function write(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch {
    /* 저장이 막혀 있어도 이번 세션은 동작해야 한다 */
  }
}
