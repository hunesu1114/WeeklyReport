<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import KanbanColumn from '@/components/KanbanColumn.vue'
import CardDialog from '@/components/CardDialog.vue'
import ProjectDialog from '@/components/ProjectDialog.vue'
import { kanbanApi } from '@/api/client'
import { useKanbanStore } from '@/stores/kanban'
import { useToast } from '@/composables/useToast'
import { STATUSES, emptyCard, toForm, toPayload } from '@/utils/kanban'

const route = useRoute()
const router = useRouter()
const store = useKanbanStore()
const toast = useToast()

const board = ref(null)
const loading = ref(true)
const saving = ref(false)
const draggingId = ref(null)

const editingCard = ref(null)
const editingProject = ref(null)

const projectId = computed(() => (route.params.projectId ? Number(route.params.projectId) : null))
const projects = computed(() => store.projects)
const activeProjects = computed(() => store.projects.filter((p) => p.active))

const totals = computed(() => {
  if (!board.value) return { all: 0, open: 0, done: 0, dueSoon: 0 }
  const columns = board.value.columns
  const all = STATUSES.reduce((sum, s) => sum + (columns[s]?.length ?? 0), 0)
  const done = columns.DONE?.length ?? 0
  const dueSoon = STATUSES.reduce(
    (sum, s) => sum + (columns[s]?.filter((c) => c.dueSoon).length ?? 0),
    0,
  )
  return { all, open: all - done, done, dueSoon }
})

onMounted(async () => {
  try {
    await store.loadProjects(true)
  } catch (error) {
    toast.error(`프로젝트를 불러오지 못했습니다. ${error.message}`)
    loading.value = false
    return
  }
  // 주소에 프로젝트가 없으면 마지막으로 보던 보드, 그것도 없으면 첫 번째
  if (!projectId.value) {
    const remembered = store.lastProjectId()
    const fallback =
      activeProjects.value.find((p) => p.id === remembered) ?? activeProjects.value[0] ?? null
    if (fallback) {
      await router.replace({ name: 'kanban-board', params: { projectId: fallback.id } })
      return
    }
    loading.value = false
    return
  }
  await loadBoard()
})

watch(projectId, (id) => {
  if (id) loadBoard()
})

async function loadBoard() {
  if (!projectId.value) return
  loading.value = true
  try {
    board.value = await kanbanApi.board(projectId.value)
    store.rememberProject(projectId.value)
  } catch (error) {
    board.value = null
    toast.error(`보드를 불러오지 못했습니다. ${error.message}`)
  } finally {
    loading.value = false
  }
}

function selectProject(id) {
  router.push({ name: 'kanban-board', params: { projectId: id } })
}

// ── 카드 ────────────────────────────────────────────────

function openNewCard(status = 'BACKLOG') {
  editingCard.value = emptyCard(projectId.value, status)
}

function openCard(card) {
  editingCard.value = toForm(card)
}

async function saveCard(form) {
  saving.value = true
  try {
    if (form.id) {
      await kanbanApi.updateCard(form.id, toPayload(form))
      toast.success('카드를 저장했습니다.')
    } else {
      await kanbanApi.createCard(toPayload(form))
      toast.success('카드를 추가했습니다.')
    }
    editingCard.value = null
    await Promise.all([loadBoard(), store.loadDueSoon()])
  } catch (error) {
    toast.error(error.message)
  } finally {
    saving.value = false
  }
}

async function removeCard(form) {
  if (!window.confirm(`"${form.title}" 카드를 삭제할까요?`)) return
  saving.value = true
  try {
    await kanbanApi.removeCard(form.id)
    editingCard.value = null
    toast.success('카드를 삭제했습니다.')
    await Promise.all([loadBoard(), store.loadDueSoon()])
  } catch (error) {
    toast.error(error.message)
  } finally {
    saving.value = false
  }
}

/**
 * 드래그로 옮긴다. 서버 응답을 기다리기 전에 화면을 먼저 바꿔
 * 카드가 손에서 놓자마자 제자리로 튕기는 느낌을 없앤다.
 */
async function moveCard({ cardId, status, sortOrder }) {
  const columns = board.value?.columns
  if (!columns) return

  let moved = null
  for (const key of STATUSES) {
    const index = columns[key].findIndex((c) => c.id === cardId)
    if (index !== -1) {
      moved = columns[key].splice(index, 1)[0]
      break
    }
  }
  if (!moved) return

  const before = JSON.parse(JSON.stringify(columns))
  moved.status = status
  columns[status].splice(Math.min(sortOrder, columns[status].length), 0, moved)
  draggingId.value = null

  try {
    await kanbanApi.moveCard(cardId, { status, sortOrder })
    await Promise.all([loadBoard(), store.loadDueSoon()])
  } catch (error) {
    board.value.columns = before // 실패하면 옮기기 전으로 되돌린다
    toast.error(`카드를 옮기지 못했습니다. ${error.message}`)
  }
}

// ── 프로젝트 ────────────────────────────────────────────

function openNewProject() {
  editingProject.value = { id: null, name: '', description: '', color: '#22c55e', active: true }
}

function openProjectSettings() {
  const current = projects.value.find((p) => p.id === projectId.value)
  if (current) editingProject.value = { ...current }
}

async function saveProject(form) {
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      description: form.description || null,
      color: form.color || null,
      active: form.active,
    }
    const saved = form.id
      ? await kanbanApi.updateProject(form.id, payload)
      : await kanbanApi.createProject(payload)
    editingProject.value = null
    await store.loadProjects(true)
    toast.success(form.id ? '프로젝트를 저장했습니다.' : '프로젝트를 만들었습니다.')
    if (!form.id) selectProject(saved.id)
    else await loadBoard()
  } catch (error) {
    toast.error(error.message)
  } finally {
    saving.value = false
  }
}

async function removeProject(form) {
  if (!window.confirm(`"${form.name}" 프로젝트를 삭제할까요?\n이 보드의 카드도 모두 사라집니다.`)) return
  saving.value = true
  try {
    await kanbanApi.removeProject(form.id)
    editingProject.value = null
    toast.success('프로젝트를 삭제했습니다.')
    await store.refresh()
    const next = store.projects.find((p) => p.active) ?? null
    if (next) selectProject(next.id)
    else await router.replace({ name: 'kanban' })
  } catch (error) {
    toast.error(error.message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="kanban">
    <header class="kanban__head">
      <div class="kanban__title">
        <h1>칸반 보드</h1>
        <p v-if="board" class="tiny muted">
          전체 {{ totals.all }} · 진행할 일 {{ totals.open }} · 완료 {{ totals.done }}
          <template v-if="totals.dueSoon">
            · <span class="kanban__due">임박 {{ totals.dueSoon }}</span>
          </template>
        </p>
      </div>

      <div class="kanban__actions">
        <button v-if="board" class="btn btn--sm" type="button" @click="openProjectSettings">
          프로젝트 설정
        </button>
        <button class="btn btn--sm" type="button" @click="openNewProject">+ 새 프로젝트</button>
        <button
          class="btn btn--primary"
          type="button"
          :disabled="!board"
          @click="openNewCard('BACKLOG')"
        >
          + 카드 추가
        </button>
      </div>
    </header>

    <nav v-if="activeProjects.length" class="tabs" aria-label="프로젝트">
      <button
        v-for="project in activeProjects"
        :key="project.id"
        class="tab"
        :class="{ 'tab--on': project.id === projectId }"
        type="button"
        @click="selectProject(project.id)"
      >
        <span class="tab__dot" :style="{ background: project.color || 'var(--text-4)' }"></span>
        {{ project.name }}
        <span class="tab__count">{{ project.openCount }}</span>
        <span v-if="project.dueSoonCount" class="tab__due" :title="`임박 ${project.dueSoonCount}건`">
          {{ project.dueSoonCount }}
        </span>
      </button>
    </nav>

    <div v-if="loading" class="kanban__state muted">불러오는 중…</div>

    <div v-else-if="!activeProjects.length" class="kanban__state card">
      <p class="muted">아직 프로젝트가 없습니다. 보드는 프로젝트마다 하나씩 만들어집니다.</p>
      <button class="btn btn--primary" type="button" @click="openNewProject">
        첫 프로젝트 만들기
      </button>
    </div>

    <div v-else-if="board" class="board">
      <KanbanColumn
        v-for="status in STATUSES"
        :key="status"
        :status="status"
        :cards="board.columns[status] || []"
        :dragging-id="draggingId"
        @open="openCard"
        @add="openNewCard"
        @dragstart="draggingId = $event.id"
        @dragend="draggingId = null"
        @drop="moveCard"
      />
    </div>

    <CardDialog
      v-if="editingCard"
      :card="editingCard"
      :projects="activeProjects"
      :saving="saving"
      @save="saveCard"
      @remove="removeCard"
      @close="editingCard = null"
    />

    <ProjectDialog
      v-if="editingProject"
      :project="editingProject"
      :saving="saving"
      @save="saveProject"
      @remove="removeProject"
      @close="editingProject = null"
    />
  </div>
</template>

<style scoped>
.kanban {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.kanban__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.kanban__title h1 {
  margin: 0;
  font-size: 19px;
  letter-spacing: -0.02em;
}

.kanban__title p {
  margin: 3px 0 0;
}

.kanban__due {
  color: var(--danger);
  font-weight: 700;
}

.kanban__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.kanban__state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 60px 20px;
  text-align: center;
}

/* ---------- 프로젝트 탭 ---------- */
.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding-bottom: 2px;
}

.tab {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 13px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--surface);
  color: var(--text-2);
  font-size: 13px;
  font-weight: 600;
  transition: border-color 0.12s, background 0.12s, color 0.12s;
}

.tab:hover {
  border-color: var(--line-strong);
  background: var(--surface-hover);
}

.tab--on {
  border-color: var(--brand);
  background: var(--brand-soft);
  color: var(--brand-strong);
}

.tab__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.tab__count {
  padding: 0 6px;
  border-radius: 999px;
  background: var(--bg-subtle);
  color: var(--text-3);
  font-size: 11px;
  font-weight: 700;
}

.tab--on .tab__count {
  background: var(--surface);
}

.tab__due {
  padding: 0 6px;
  border-radius: 999px;
  background: var(--danger-soft);
  color: var(--danger);
  font-size: 11px;
  font-weight: 800;
}

/* ---------- 보드 ---------- */
.board {
  display: grid;
  grid-template-columns: repeat(4, minmax(240px, 1fr));
  gap: 12px;
  align-items: start;
}

@media (max-width: 1100px) {
  .board {
    grid-template-columns: repeat(2, minmax(240px, 1fr));
  }
}

@media (max-width: 640px) {
  .board {
    grid-template-columns: 1fr;
  }
}
</style>
