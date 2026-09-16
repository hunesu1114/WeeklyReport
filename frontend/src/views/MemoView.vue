<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import MemoTree from '@/components/MemoTree.vue'
import { memoApi } from '@/api/client'
import { useMemoStore, ROOT } from '@/stores/memo'
import { useToast } from '@/composables/useToast'
import { useNotepad } from '@/composables/useNotepad'
import { lineCount } from '@/utils/notepad'
import { timeAgo } from '@/utils/team'

const store = useMemoStore()
const toast = useToast()

/** 저장하지 않은 채 이만큼 쉬면 알아서 저장한다. 메모장과 달리 잃을 이유가 없다. */
const AUTOSAVE_MS = 1200

const FONTS = [
  { value: '', label: '기본 (맑은 고딕)' },
  { value: "'Malgun Gothic', sans-serif", label: '맑은 고딕' },
  { value: "'Gulim', sans-serif", label: '굴림' },
  { value: "'Batang', serif", label: '바탕' },
  { value: "'Dotum', sans-serif", label: '돋움' },
  { value: "'NanumGothic', 'Malgun Gothic', sans-serif", label: '나눔고딕' },
  { value: "'D2Coding', 'Consolas', monospace", label: 'D2Coding (고정폭)' },
  { value: "'Consolas', monospace", label: 'Consolas (고정폭)' },
  { value: "'Courier New', monospace", label: 'Courier New (고정폭)' },
]
const SIZES = [11, 12, 13, 14, 16, 18, 20, 24]

const editor = ref(null)
const titleInput = ref(null)
const findInput = ref(null)

const memo = ref(null)
const dirty = ref(false)
const saving = ref(false)
const busy = ref(false)
const shortcutsOpen = ref(false)
let autosaveTimer = null

const notepad = useNotepad(editor, {
  onSave: () => save(),
  onNew: () => createMemo(),
  onExport: () => exportText(),
})
const { status, finder } = notepad

const folderOptions = computed(() =>
  store.folders
    .map((f) => ({ id: f.id, label: store.pathOf(f.id).join(' / ') }))
    .sort((a, b) => a.label.localeCompare(b.label, 'ko')),
)

const breadcrumb = computed(() => {
  if (!memo.value) return []
  return memo.value.folderId == null ? ['폴더 없음'] : store.pathOf(memo.value.folderId)
})

const stats = computed(() => {
  const text = memo.value?.content ?? ''
  return { chars: text.length, lines: lineCount(text) }
})

const editorStyle = computed(() => ({
  fontFamily: memo.value?.fontFamily || 'var(--font)',
  fontSize: `${memo.value?.fontSize || 14}px`,
  color: memo.value?.fontColor || 'var(--text)',
}))

// ── 불러오기 ────────────────────────────────────────────

onMounted(async () => {
  try {
    await store.load()
  } catch (error) {
    toast.error(`메모를 불러오지 못했습니다. ${error.message}`)
    return
  }
  const remembered = store.lastMemoId()
  const target = store.memos.find((m) => m.id === remembered) ?? store.memos[0]
  if (target) await openMemo(target.id)
})

onBeforeUnmount(() => {
  clearTimeout(autosaveTimer)
  if (dirty.value) save()
})

// 다른 화면으로 나가도 쓰던 것은 남아야 한다
onBeforeRouteLeave(() => {
  clearTimeout(autosaveTimer)
  if (dirty.value) save()
})

async function openMemo(id) {
  if (memo.value?.id === id) return
  if (dirty.value) await save()

  busy.value = true
  try {
    memo.value = await memoApi.get(id)
    dirty.value = false
    store.rememberMemo(id)
    store.revealFolder(memo.value.folderId)
    await nextTick()
    notepad.sync()
  } catch (error) {
    toast.error(error.message)
  } finally {
    busy.value = false
  }
}

// ── 쓰기 ────────────────────────────────────────────────

function touched() {
  dirty.value = true
  clearTimeout(autosaveTimer)
  autosaveTimer = setTimeout(() => save(), AUTOSAVE_MS)
}

async function save() {
  if (!memo.value || saving.value) return
  clearTimeout(autosaveTimer)

  saving.value = true
  try {
    const saved = await memoApi.update(memo.value.id, payloadOf(memo.value))
    memo.value = { ...memo.value, title: saved.title, updatedAt: saved.updatedAt }
    store.applySaved(saved)
    dirty.value = false
  } catch (error) {
    toast.error(`저장하지 못했습니다. ${error.message}`)
  } finally {
    saving.value = false
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
  if (dirty.value) await save()

  const target =
    folderId !== undefined
      ? folderId
      : store.selectedFolderId === ROOT
        ? null
        : store.selectedFolderId

  busy.value = true
  try {
    const created = await memoApi.create({ title: '제목 없음', content: '', folderId: target })
    store.applySaved(created)
    memo.value = created
    dirty.value = false
    store.rememberMemo(created.id)
    store.revealFolder(created.folderId)
    await nextTick()
    titleInput.value?.select()
  } catch (error) {
    toast.error(error.message)
  } finally {
    busy.value = false
  }
}

async function removeMemo() {
  if (!memo.value) return
  if (!window.confirm(`"${memo.value.title}" 메모를 삭제할까요?\n되돌릴 수 없습니다.`)) return

  const id = memo.value.id
  try {
    clearTimeout(autosaveTimer)
    dirty.value = false
    await memoApi.remove(id)
    store.forget(id)
    memo.value = null
    toast.success('메모를 삭제했습니다.')
    const next = store.visibleMemos[0] ?? store.memos[0]
    if (next) await openMemo(next.id)
  } catch (error) {
    toast.error(error.message)
  }
}

async function exportText() {
  if (!memo.value) return
  if (dirty.value) await save()
  try {
    const filename = await memoApi.exportText(memo.value.id)
    toast.success(`${filename} 파일을 내려받았습니다.`)
  } catch (error) {
    toast.error(`내보내지 못했습니다. ${error.message}`)
  }
}

/** 폴더를 바꾸면 곧바로 저장한다. 목록에서 제자리를 찾아야 하기 때문이다. */
async function changeFolder(value) {
  if (!memo.value) return
  memo.value.folderId = value === '' ? null : Number(value)
  await save()
}

// ── 폴더 ────────────────────────────────────────────────

async function addFolder(parent = null) {
  const name = window.prompt(parent ? `'${parent.name}' 안에 만들 폴더 이름` : '새 폴더 이름', '')
  if (name === null || !name.trim()) return
  try {
    await memoApi.createFolder(name.trim(), parent?.id ?? null)
    await store.load()
    if (parent) store.revealFolder(parent.id)
  } catch (error) {
    toast.error(error.message)
  }
}

async function renameFolder(node) {
  const name = window.prompt('폴더 이름', node.name)
  if (name === null || !name.trim() || name.trim() === node.name) return
  try {
    await memoApi.renameFolder(node.id, name.trim())
    await store.load()
  } catch (error) {
    toast.error(error.message)
  }
}

async function removeFolder(node) {
  const message =
    `'${node.name}' 폴더를 삭제할까요?\n` +
    '하위 폴더도 함께 사라집니다. 안에 있던 메모는 지워지지 않고 최상위로 나옵니다.'
  if (!window.confirm(message)) return

  try {
    await memoApi.removeFolder(node.id)
    if (store.selectedFolderId === node.id) store.selectFolder(ROOT)
    await store.load()
    if (memo.value) memo.value = await memoApi.get(memo.value.id)
  } catch (error) {
    toast.error(error.message)
  }
}

async function dropMemo({ memoId, folderId }) {
  try {
    const moved = await memoApi.move(memoId, folderId)
    store.applySaved(moved)
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

/** 트리 맨 위 '전체'. 여기에 떨어뜨리면 폴더 밖으로 나온다. */
function onDropRoot(event) {
  const memoId = event.dataTransfer.getData('application/x-memo-id')
  const folderId = event.dataTransfer.getData('application/x-folder-id')
  event.preventDefault()
  if (memoId) dropMemo({ memoId: Number(memoId), folderId: null })
  else if (folderId) dropFolder({ folderId: Number(folderId), parentId: null })
}

watch(
  () => finder.value.open,
  async (open) => {
    if (!open) return
    await nextTick()
    findInput.value?.select()
  },
)
</script>

<template>
  <div class="memo">
    <header class="memo__head">
      <div>
        <h1>메모</h1>
        <p class="tiny muted">
          폴더로 정리하고 txt 로 내보냅니다. 메모장 단축키를 그대로 씁니다.
        </p>
      </div>
      <div class="memo__actions">
        <button class="btn btn--sm" type="button" @click="shortcutsOpen = !shortcutsOpen">
          단축키
        </button>
        <button class="btn btn--sm" type="button" @click="addFolder(null)">+ 새 폴더</button>
        <button class="btn btn--sm btn--primary" type="button" :disabled="busy" @click="createMemo()">
          + 새 메모
        </button>
      </div>
    </header>

    <div v-if="shortcutsOpen" class="keys card">
      <div class="keys__grid">
        <span><kbd>Tab</kbd> / <kbd>Shift+Tab</kbd></span><span>들여쓰기 / 내어쓰기 (4칸)</span>
        <span><kbd>Ctrl+X</kbd> / <kbd>Ctrl+C</kbd></span><span>고른 것 없으면 한 줄 잘라내기 / 복사</span>
        <span><kbd>Ctrl+D</kbd></span><span>줄 복제</span>
        <span><kbd>Ctrl+Delete</kbd></span><span>줄 삭제</span>
        <span><kbd>Enter</kbd></span><span>앞 줄 들여쓰기 이어받기</span>
        <span><kbd>Ctrl+F</kbd> / <kbd>Ctrl+H</kbd></span><span>찾기 / 바꾸기</span>
        <span><kbd>F3</kbd> / <kbd>Shift+F3</kbd></span><span>다음 찾기 / 이전 찾기</span>
        <span><kbd>Ctrl+G</kbd></span><span>줄 번호로 이동</span>
        <span><kbd>F5</kbd></span><span>날짜와 시각 넣기</span>
        <span><kbd>Ctrl+S</kbd> / <kbd>Ctrl+Shift+S</kbd></span><span>저장 / txt 내보내기</span>
        <span><kbd>Ctrl+N</kbd></span><span>새 메모</span>
        <span><kbd>Ctrl+Z</kbd> / <kbd>Ctrl+Y</kbd></span><span>되돌리기 / 다시 실행</span>
      </div>
    </div>

    <div class="memo__grid">
      <!-- ── LNB ── -->
      <aside class="lnb card">
        <div class="lnb__search">
          <input
            v-model="store.query"
            class="control"
            type="search"
            placeholder="메모 검색 (제목·첫 줄)"
          />
        </div>

        <div class="lnb__tree">
          <div
            class="root"
            :class="{ 'root--on': store.selectedFolderId === ROOT }"
            @dragover.prevent
            @drop="onDropRoot"
          >
            <button class="root__btn" type="button" @click="store.selectFolder(ROOT)">
              <span aria-hidden="true">🗂</span>
              폴더 없음
              <span v-if="store.rootCount" class="root__count">{{ store.rootCount }}</span>
            </button>
          </div>

          <MemoTree
            :nodes="store.tree"
            :selected-id="store.selectedFolderId"
            :is-open="store.isOpen"
            @select="store.selectFolder"
            @toggle="store.toggleFolder"
            @rename="renameFolder"
            @remove="removeFolder"
            @add-child="addFolder"
            @drop-memo="dropMemo"
            @drop-folder="dropFolder"
          />

          <p v-if="!store.folders.length" class="lnb__empty tiny muted">
            폴더가 없습니다. 위의 <strong>+ 새 폴더</strong>로 만드세요.
          </p>
        </div>

        <div class="lnb__list">
          <div class="lnb__label tiny muted">
            <template v-if="store.searching">검색 결과 {{ store.visibleMemos.length }}</template>
            <template v-else>메모 {{ store.visibleMemos.length }}</template>
          </div>

          <p v-if="!store.visibleMemos.length" class="lnb__empty tiny muted">
            {{ store.searching ? '찾는 메모가 없습니다.' : '이 폴더에 메모가 없습니다.' }}
          </p>

          <ul v-else class="notes">
            <li v-for="item in store.visibleMemos" :key="item.id">
              <button
                class="note"
                :class="{ 'note--on': memo?.id === item.id }"
                type="button"
                draggable="true"
                @dragstart="onDragMemo($event, item)"
                @click="openMemo(item.id)"
              >
                <span class="note__title">{{ item.title }}</span>
                <span class="note__preview tiny muted">{{ item.preview || '(내용 없음)' }}</span>
                <span class="note__when tiny muted">{{ timeAgo(item.updatedAt) }}</span>
              </button>
            </li>
          </ul>
        </div>
      </aside>

      <!-- ── 편집기 ── -->
      <section v-if="memo" class="pad card">
        <div class="pad__head">
          <input
            ref="titleInput"
            v-model="memo.title"
            class="control pad__title"
            type="text"
            maxlength="200"
            placeholder="제목"
            @input="touched"
          />

          <select
            class="control pad__folder"
            :value="memo.folderId ?? ''"
            aria-label="폴더"
            @change="changeFolder($event.target.value)"
          >
            <option value="">폴더 없음</option>
            <option v-for="option in folderOptions" :key="option.id" :value="option.id">
              {{ option.label }}
            </option>
          </select>

          <button class="btn btn--sm" type="button" :disabled="saving" @click="save">
            <span v-if="saving" class="spinner"></span>
            {{ dirty ? '저장' : '저장됨' }}
          </button>
          <button class="btn btn--sm" type="button" @click="exportText">txt 내보내기</button>
          <button class="btn btn--sm btn--danger" type="button" @click="removeMemo">삭제</button>
        </div>

        <div class="pad__tools">
          <!-- 서버가 null 을 키째로 빼고 내려주므로 undefined 가 온다. '' 로 눌러 기본 항목에 맞춘다 -->
          <select
            class="control pad__font"
            :value="memo.fontFamily || ''"
            aria-label="글꼴"
            @change="((memo.fontFamily = $event.target.value || null), touched())"
          >
            <option v-for="font in FONTS" :key="font.label" :value="font.value">
              {{ font.label }}
            </option>
          </select>

          <select
            class="control pad__size"
            :value="memo.fontSize ?? 14"
            aria-label="글자 크기"
            @change="((memo.fontSize = Number($event.target.value)), touched())"
          >
            <option v-for="size in SIZES" :key="size" :value="size">{{ size }}px</option>
          </select>

          <label class="pad__color" title="글자 색">
            <input
              type="color"
              :value="memo.fontColor || '#333333'"
              @input="((memo.fontColor = $event.target.value), touched())"
            />
            <span class="tiny">색</span>
          </label>
          <button
            v-if="memo.fontColor"
            class="btn btn--ghost btn--sm"
            type="button"
            title="글자 색을 기본으로"
            @click="((memo.fontColor = null), touched())"
          >
            색 지우기
          </button>

          <label class="pad__wrap tiny">
            <input
              type="checkbox"
              :checked="memo.wordWrap"
              @change="((memo.wordWrap = $event.target.checked), touched())"
            />
            자동 줄 바꿈
          </label>

          <span class="pad__spacer"></span>
          <button class="btn btn--ghost btn--sm" type="button" @click="notepad.openFinder(true)">
            찾기 / 바꾸기
          </button>
        </div>

        <!-- 찾기 / 바꾸기 -->
        <div v-if="finder.open" class="find">
          <input
            ref="findInput"
            v-model="finder.query"
            class="control find__input"
            type="text"
            placeholder="찾을 내용"
            @keydown.enter.prevent="notepad.search(false)"
            @keydown.esc.prevent="notepad.closeFinder()"
          />
          <button class="btn btn--sm" type="button" @click="notepad.search(true)">이전</button>
          <button class="btn btn--sm" type="button" @click="notepad.search(false)">다음</button>

          <input
            v-model="finder.replacement"
            class="control find__input"
            type="text"
            placeholder="바꿀 내용"
            @keydown.enter.prevent="notepad.replaceOne()"
            @keydown.esc.prevent="notepad.closeFinder()"
          />
          <button class="btn btn--sm" type="button" @click="notepad.replaceOne()">바꾸기</button>
          <button class="btn btn--sm" type="button" @click="notepad.replaceEvery()">모두 바꾸기</button>

          <label class="find__case tiny">
            <input v-model="finder.matchCase" type="checkbox" />
            대소문자 구분
          </label>

          <span v-if="finder.message" class="find__msg tiny">{{ finder.message }}</span>
          <button class="btn btn--ghost btn--sm" type="button" @click="notepad.closeFinder()">닫기</button>
        </div>

        <textarea
          ref="editor"
          v-model="memo.content"
          class="pad__area"
          :class="{ 'pad__area--nowrap': !memo.wordWrap }"
          :style="editorStyle"
          :wrap="memo.wordWrap ? 'soft' : 'off'"
          spellcheck="false"
          placeholder="여기에 적습니다. Tab 은 4칸, F5 는 날짜와 시각."
          @input="touched"
          @keydown="notepad.onKeydown"
          @copy="notepad.onCopy"
          @cut="notepad.onCut"
          @click="notepad.sync"
          @keyup="notepad.sync"
          @select="notepad.sync"
        ></textarea>

        <footer class="pad__status tiny">
          <span class="pad__crumb muted">{{ breadcrumb.join(' / ') }}</span>
          <span class="pad__spacer"></span>
          <span class="num">{{ status.line }}줄, {{ status.column }}칸</span>
          <span v-if="status.selected" class="num">고름 {{ status.selected }}자</span>
          <span class="num muted">{{ stats.lines }}줄 · {{ stats.chars }}자</span>
          <span :class="dirty ? 'pad__dirty' : 'muted'">
            {{ dirty ? '저장 안 됨' : `저장됨 · ${timeAgo(memo.updatedAt)}` }}
          </span>
        </footer>
      </section>

      <section v-else class="pad pad--empty card">
        <p class="muted">왼쪽에서 메모를 고르거나 새로 만드세요.</p>
        <button class="btn btn--primary" type="button" @click="createMemo()">첫 메모 만들기</button>
      </section>
    </div>
  </div>
</template>

<style scoped>
.memo {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.memo__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.memo__head h1 {
  margin: 0;
  font-size: 19px;
  letter-spacing: -0.02em;
}

.memo__head p {
  margin: 3px 0 0;
}

.memo__actions {
  display: flex;
  align-items: stretch;
  gap: 8px;
}

/* ---------- 단축키 ---------- */
.keys {
  padding: 14px 16px;
}

.keys__grid {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 6px 16px;
  font-size: var(--fs-sm);
  color: var(--text-2);
}

.keys__grid > span:nth-child(odd) {
  white-space: nowrap;
}

kbd {
  display: inline-block;
  padding: 1px 6px;
  border: 1px solid var(--line-strong);
  border-bottom-width: 2px;
  border-radius: var(--radius-xs);
  background: var(--surface-2);
  color: var(--text);
  font-family: var(--font);
  font-size: 11px;
  font-weight: 600;
}

/* ---------- 배치 ---------- */
.memo__grid {
  display: grid;
  grid-template-columns: 264px minmax(0, 1fr);
  gap: 12px;
  align-items: stretch;
  /* 보드처럼 화면에 묶어 둔다. 안에서 따로 굴러야 목록과 본문이 서로 밀지 않는다 */
  height: clamp(420px, calc(100vh - 210px), 1000px);
}

@media (max-width: 900px) {
  .memo__grid {
    grid-template-columns: minmax(0, 1fr);
    height: auto;
  }
}

/* ---------- LNB ---------- */
.lnb {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.lnb__search {
  padding: 10px;
  border-bottom: 1px solid var(--line);
}

.lnb__tree {
  flex: 0 1 auto;
  min-height: 92px;
  max-height: 44%;
  overflow-y: auto;
  padding: 8px 6px;
  border-bottom: 1px solid var(--line);
}

.lnb__list {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: 8px 6px 10px;
}

.lnb__label {
  padding: 2px 6px 6px;
  font-weight: 600;
}

.lnb__empty {
  margin: 0;
  padding: 14px 8px;
  line-height: 1.6;
}

.root {
  border-radius: var(--radius-xs);
}

.root__btn {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 5px 8px;
  border: 0;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--text-2);
  font-size: var(--fs-sm);
  font-weight: 600;
  text-align: left;
}

.root__btn:hover {
  background: var(--surface-hover);
}

.root--on .root__btn {
  background: var(--brand-soft);
  color: var(--brand-strong);
}

.root__count {
  margin-left: auto;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--bg-subtle);
  color: var(--text-3);
  font-size: 10px;
  font-weight: 600;
}

.notes {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.note {
  display: flex;
  flex-direction: column;
  gap: 1px;
  width: 100%;
  padding: 7px 8px;
  border: 0;
  border-left: 2px solid transparent;
  border-radius: var(--radius-xs);
  background: transparent;
  text-align: left;
}

.note:hover {
  background: var(--surface-hover);
}

.note--on {
  border-left-color: var(--brand);
  background: var(--brand-soft);
}

.note__title {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note__preview,
.note__when {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------- 편집기 ---------- */
.pad {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.pad--empty {
  align-items: center;
  justify-content: center;
  gap: 14px;
  text-align: center;
}

.pad__head,
.pad__tools {
  display: flex;
  align-items: stretch;
  gap: 7px;
  padding: 9px 10px;
  border-bottom: 1px solid var(--line);
}

.pad__tools {
  align-items: center;
  flex-wrap: wrap;
  background: var(--surface-2);
}

.pad__title {
  flex: 1;
  min-width: 120px;
  font-weight: 600;
}

.pad__folder {
  flex: none;
  width: 168px;
}

.pad__font {
  flex: none;
  width: 168px;
}

.pad__size {
  flex: none;
  width: 84px;
}

.pad__color {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--text-3);
}

.pad__color input {
  width: 30px;
  height: 26px;
  padding: 0;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-xs);
  background: transparent;
  cursor: pointer;
}

.pad__wrap {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--text-3);
}

.pad__spacer {
  flex: 1;
}

/* ---------- 찾기 ---------- */
.find {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--line);
  background: var(--warn-soft);
}

.find__input {
  flex: 1;
  min-width: 130px;
  max-width: 220px;
}

.find__case {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--text-2);
}

.find__msg {
  color: var(--warn);
  font-weight: 600;
}

/* ---------- 본문 ---------- */
.pad__area {
  flex: 1;
  min-height: 240px;
  width: 100%;
  padding: 14px 16px;
  border: 0;
  background: transparent;
  line-height: 1.65;
  resize: none;
  outline: none;
  tab-size: 4;
}

.pad__area--nowrap {
  white-space: pre;
  overflow-x: auto;
}

.pad__status {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  padding: 7px 12px;
  border-top: 1px solid var(--line);
  background: var(--surface-2);
  color: var(--text-3);
}

.pad__crumb {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 40%;
}

.pad__dirty {
  color: var(--warn);
  font-weight: 600;
}
</style>
