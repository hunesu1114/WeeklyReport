import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { memoApi } from '@/api/client'

const OPEN_KEY = 'weekly-report:memo-open-folders'
const LAST_KEY = 'weekly-report:last-memo'

/** 최상위(폴더 없음)를 가리키는 값. null 은 "아무것도 안 고름"과 겹쳐 쓸 수 없다. */
export const ROOT = 'root'

/** 폴더가 아니라 '즐겨찾기한 것만' 이라는 뜻. 트리 맨 위에 폴더처럼 놓인다. */
export const FAVORITES = 'favorites'

export const useMemoStore = defineStore('memo', () => {
  const folders = ref([])
  const memos = ref([])
  const loading = ref(false)

  /** 트리에서 고른 폴더. ROOT 면 최상위. */
  const selectedFolderId = ref(ROOT)
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

  // ── 목록 ─────────────────────────────────────────────────

  /**
   * 오른쪽 목록에 띄울 메모.
   * 검색어가 있으면 폴더를 무시하고 전체에서 찾는다 — 어느 폴더에 넣었는지
   * 기억나지 않아서 찾는 것이므로, 고른 폴더 안에서만 찾으면 쓸모가 없다.
   */
  const visibleMemos = computed(() => {
    const q = query.value.trim().toLowerCase()
    if (q) {
      // 찾을 때는 고정을 따지지 않는다. 찾는 것은 폴더 안 자리와 상관이 없다.
      return memos.value.filter(
        (m) =>
          m.title.toLowerCase().includes(q) || (m.preview ?? '').toLowerCase().includes(q),
      )
    }

    const folderId = selectedFolderId.value
    // 즐겨찾기는 여러 폴더에서 모아 온 것이라 '폴더 안에서 맨 위'가 뜻을 갖지 못한다
    if (folderId === FAVORITES) return memos.value.filter((m) => m.favorite)

    return memos.value
      .filter((m) => (folderId === ROOT ? m.folderId == null : m.folderId === folderId))
      .sort(pinnedFirst)
  })

  const searching = computed(() => query.value.trim().length > 0)

  /** 최상위(폴더 없음)에 놓인 메모 수. 트리 맨 위 줄이 쓴다. */
  const rootCount = computed(() => memos.value.filter((m) => m.folderId == null).length)

  const favoriteCount = computed(() => memos.value.filter((m) => m.favorite).length)

  // ── 읽기 ─────────────────────────────────────────────────

  async function load() {
    loading.value = true
    try {
      const data = await memoApi.workspace()
      folders.value = data.folders ?? []
      memos.value = data.memos ?? []
    } finally {
      loading.value = false
    }
    return memos.value
  }

  /** 저장·삭제 뒤 목록만 최신으로. 편집 중인 본문은 건드리지 않는다. */
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
    }
    const at = memos.value.findIndex((m) => m.id === saved.id)
    if (at === -1) memos.value.unshift(summary)
    else memos.value.splice(at, 1, summary)
  }

  function forget(id) {
    memos.value = memos.value.filter((m) => m.id !== id)
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
    query.value = ''
    selectedFolderId.value = ROOT
  }

  return {
    folders,
    memos,
    loading,
    tree,
    folderById,
    visibleMemos,
    searching,
    rootCount,
    favoriteCount,
    query,
    selectedFolderId,
    pathOf,
    load,
    applySaved,
    forget,
    isOpen,
    toggleFolder,
    revealFolder,
    selectFolder,
    rememberMemo,
    lastMemoId,
    reset,
  }
})

/**
 * 고정한 것을 맨 위로. 같은 무리 안에서는 서버가 준 최근 순을 그대로 둔다
 * (Array.sort 는 순서가 같은 것끼리 자리를 바꾸지 않는다).
 */
function pinnedFirst(a, b) {
  if (a.pinned === b.pinned) return 0
  return a.pinned ? -1 : 1
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
