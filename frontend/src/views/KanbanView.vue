<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import KanbanColumn from '@/components/KanbanColumn.vue'
import CardDialog from '@/components/CardDialog.vue'
import ProjectDialog from '@/components/ProjectDialog.vue'
import MembersDialog from '@/components/MembersDialog.vue'
import ActivityPanel from '@/components/ActivityPanel.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { kanbanApi, teamApi } from '@/api/client'
import { useKanbanStore } from '@/stores/kanban'
import { useNotificationStore } from '@/stores/notifications'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useKanbanSocket } from '@/composables/useKanbanSocket'
import { STATUSES, emptyCard, toForm, toPayload } from '@/utils/kanban'
import { ROLE_META, canWrite as roleCanWrite, isOwner } from '@/utils/team'

const route = useRoute()
const router = useRouter()
const store = useKanbanStore()
const inbox = useNotificationStore()
const auth = useAuthStore()
const toast = useToast()

const board = ref(null)
const members = ref([])
const loading = ref(true)
const saving = ref(false)
const draggingId = ref(null)
/** 담당자 필터. 'all' 이거나 내 사용자 id. */
const assigneeFilter = ref('all')

const editingCard = ref(null)
const editingProject = ref(null)
const conflict = ref(null)
const membersOpen = ref(false)
const activityPanel = ref(null)

const projectId = computed(() => (route.params.projectId ? Number(route.params.projectId) : null))
const projects = computed(() => store.projects)
const activeProjects = computed(() => store.projects.filter((p) => p.active))

const myRole = computed(() => board.value?.project?.myRole ?? null)
const canWrite = computed(() => roleCanWrite(myRole.value))
const amOwner = computed(() => isOwner(myRole.value))

/** 담당자가 없는데 완료일이 임박한 카드. 팀 보드에서 제일 잘 새는 구멍이다. */
const unassignedDue = computed(() => board.value?.unassignedDueCount ?? 0)

/**
 * 화면에 그릴 칸. 담당자 필터를 여기서만 적용한다 —
 * 서버에 다시 묻지 않으므로 필터를 바꿔도 즉시 반응한다.
 */
const columns = computed(() => {
  const raw = board.value?.columns
  if (!raw) return null
  if (assigneeFilter.value === 'all') return raw

  const mine = assigneeFilter.value
  const filtered = {}
  for (const status of STATUSES) {
    filtered[status] = (raw[status] ?? []).filter((card) => card.assignee?.id === mine)
  }
  return filtered
})

const totals = computed(() => {
  if (!board.value) return { all: 0, open: 0, done: 0, dueSoon: 0 }
  const raw = board.value.columns
  const all = STATUSES.reduce((sum, s) => sum + (raw[s]?.length ?? 0), 0)
  const done = raw.DONE?.length ?? 0
  const dueSoon = STATUSES.reduce(
    (sum, s) => sum + (raw[s]?.filter((c) => c.dueSoon).length ?? 0),
    0,
  )
  return { all, open: all - done, done, dueSoon }
})

// ── 실시간 ──────────────────────────────────────────────
//
// 서버는 "무엇이 바뀌었다"만 보낸다. 받으면 그 부분만 다시 읽는다.
// 내가 일으킨 변경은 composable 이 걸러내므로 여기까지 오지 않는다.
const socket = useKanbanSocket({
  'board-changed': () => {
    loadBoard({ quiet: true })
    store.loadDueSoon().catch(() => {})
    activityPanel.value?.reload()
  },
  'members-changed': () => {
    loadMembers()
    store.loadProjects(true).catch(() => {})
    activityPanel.value?.reload()
  },
  'inbox-changed': () => {
    inbox.load().catch(() => {})
  },
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
  else socket.watchProject(null)
})

/** quiet: 실시간 신호로 다시 읽을 때. 로딩 화면을 띄우면 보드가 깜빡인다. */
async function loadBoard({ quiet = false } = {}) {
  if (!projectId.value) return
  if (!quiet) loading.value = true
  try {
    board.value = await kanbanApi.board(projectId.value)
    store.rememberProject(projectId.value)
    socket.watchProject(projectId.value)
    await loadMembers()
  } catch (error) {
    board.value = null
    members.value = []
    toast.error(`보드를 불러오지 못했습니다. ${error.message}`)
  } finally {
    loading.value = false
  }
}

async function loadMembers() {
  if (!projectId.value) return
  try {
    members.value = await teamApi.members(projectId.value)
  } catch {
    // 참여자를 못 읽어도 보드는 보여야 한다. 담당자 선택만 비게 된다.
    members.value = []
  }
}

function selectProject(id) {
  assigneeFilter.value = 'all'
  router.push({ name: 'kanban-board', params: { projectId: id } })
}

// ── 카드 ────────────────────────────────────────────────

function openNewCard(status = 'BACKLOG') {
  if (!canWrite.value) return
  conflict.value = null
  // 팀 보드에서는 대개 내가 할 일을 적는다. 담당자를 나로 미리 채운다.
  editingCard.value = emptyCard(projectId.value, status, auth.user?.id ?? null)
}

function openCard(card) {
  conflict.value = null
  editingCard.value = toForm(card)
}

function closeCard() {
  editingCard.value = null
  conflict.value = null
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
    conflict.value = null
    editingCard.value = null
    await Promise.all([loadBoard({ quiet: true }), store.loadDueSoon()])
    activityPanel.value?.reload()
  } catch (error) {
    // 내가 열어둔 사이에 남이 먼저 고쳤다. 닫지 말고 무엇이 다른지 보여준다.
    if (error.conflict) {
      conflict.value = error.conflict
      editingCard.value = { ...form }
    } else {
      toast.error(error.message)
    }
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
    conflict.value = null
    toast.success('카드를 삭제했습니다.')
    await Promise.all([loadBoard({ quiet: true }), store.loadDueSoon()])
    activityPanel.value?.reload()
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
  const raw = board.value?.columns
  if (!raw || !canWrite.value) return

  let moved = null
  for (const key of STATUSES) {
    const index = raw[key].findIndex((c) => c.id === cardId)
    if (index !== -1) {
      moved = raw[key].splice(index, 1)[0]
      break
    }
  }
  if (!moved) return

  const before = JSON.parse(JSON.stringify(raw))
  moved.status = status
  raw[status].splice(Math.min(sortOrder, raw[status].length), 0, moved)
  draggingId.value = null

  try {
    await kanbanApi.moveCard(cardId, { status, sortOrder })
    await Promise.all([loadBoard({ quiet: true }), store.loadDueSoon()])
    activityPanel.value?.reload()
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
    else await loadBoard({ quiet: true })
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
    await goToAnotherBoard()
  } catch (error) {
    toast.error(error.message)
  } finally {
    saving.value = false
  }
}

/** 보드에서 나갔거나 보드를 지웠다. 더 이상 볼 수 없으므로 다른 보드로 옮긴다. */
async function goToAnotherBoard() {
  membersOpen.value = false
  board.value = null
  socket.watchProject(null)
  await store.refresh()
  const next = store.projects.find((p) => p.active) ?? null
  if (next) selectProject(next.id)
  else await router.replace({ name: 'kanban' })
}

async function onMembersChanged() {
  await Promise.all([loadMembers(), store.loadProjects(true)])
  await loadBoard({ quiet: true })
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
          <template v-if="myRole">
            · <span class="kanban__role">{{ ROLE_META[myRole]?.label }}</span>
          </template>
        </p>
      </div>

      <div class="kanban__actions">
        <button v-if="board && amOwner" class="btn btn--sm" type="button" @click="openProjectSettings">
          프로젝트 설정
        </button>
        <button class="btn btn--sm" type="button" @click="openNewProject">+ 새 프로젝트</button>
        <button
          class="btn btn--sm btn--primary"
          type="button"
          :disabled="!board || !canWrite"
          :title="canWrite ? '' : '읽기 전용으로 참여 중입니다'"
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

    <!-- 참여자 바 -->
    <div v-if="board" class="team">
      <button class="team__faces" type="button" title="참여자 보기" @click="membersOpen = true">
        <UserAvatar
          v-for="member in members.slice(0, 6)"
          :key="member.userId"
          :user="member"
          :size="26"
          :title="`${member.displayName} · ${ROLE_META[member.role]?.label}`"
        />
        <span v-if="members.length > 6" class="team__more">+{{ members.length - 6 }}</span>
        <span class="team__label tiny">참여자 {{ members.length }}</span>
      </button>

      <span
        v-if="unassignedDue"
        class="team__warn tiny"
        title="담당자 없이 완료일만 다가오는 카드입니다"
      >
        담당 없는 임박 카드 {{ unassignedDue }}건
      </span>

      <div class="team__filter">
        <button
          class="team__chip"
          :class="{ 'team__chip--on': assigneeFilter === 'all' }"
          type="button"
          @click="assigneeFilter = 'all'"
        >
          전체
        </button>
        <button
          class="team__chip"
          :class="{ 'team__chip--on': assigneeFilter !== 'all' }"
          type="button"
          @click="assigneeFilter = auth.user?.id"
        >
          내 카드
        </button>
      </div>

      <button class="btn btn--sm" type="button" @click="membersOpen = true">
        {{ amOwner ? '참여자 관리' : '참여자' }}
      </button>
    </div>

    <div v-if="loading" class="kanban__state muted">불러오는 중…</div>

    <div v-else-if="!activeProjects.length" class="kanban__state card">
      <p class="muted">아직 프로젝트가 없습니다. 보드는 프로젝트마다 하나씩 만들어집니다.</p>
      <button class="btn btn--primary" type="button" @click="openNewProject">
        첫 프로젝트 만들기
      </button>
    </div>

    <template v-else-if="board">
      <div class="board">
        <KanbanColumn
          v-for="status in STATUSES"
          :key="status"
          :status="status"
          :cards="columns[status] || []"
          :dragging-id="draggingId"
          :read-only="!canWrite"
          @open="openCard"
          @add="openNewCard"
          @dragstart="draggingId = $event.id"
          @dragend="draggingId = null"
          @drop="moveCard"
        />
      </div>

      <ActivityPanel ref="activityPanel" :project-id="projectId" />
    </template>

    <CardDialog
      v-if="editingCard"
      :card="editingCard"
      :projects="activeProjects"
      :members="members"
      :saving="saving"
      :read-only="!canWrite"
      :conflict="conflict"
      @save="saveCard"
      @remove="removeCard"
      @resolve-conflict="conflict = null"
      @close="closeCard"
    />

    <ProjectDialog
      v-if="editingProject"
      :project="editingProject"
      :saving="saving"
      @save="saveProject"
      @remove="removeProject"
      @close="editingProject = null"
    />

    <MembersDialog
      v-if="membersOpen && board"
      :project="board.project"
      @changed="onMembersChanged"
      @left="goToAnotherBoard"
      @close="membersOpen = false"
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

.kanban__role {
  color: var(--brand-strong);
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

/* ---------- 참여자 바 ---------- */
.team {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--surface);
}

.team__faces {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 6px 2px 2px;
  border: 0;
  border-radius: 999px;
  background: transparent;
}

.team__faces:hover {
  background: var(--surface-hover);
}

.team__more {
  display: inline-grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--bg-subtle);
  color: var(--text-3);
  font-size: 11px;
  font-weight: 800;
}

.team__label {
  margin-left: 4px;
  color: var(--text-3);
  font-weight: 700;
}

.team__warn {
  padding: 3px 9px;
  border-radius: 999px;
  background: var(--warn-soft);
  color: var(--warn);
  font-weight: 700;
}

.team__filter {
  display: flex;
  gap: 2px;
  margin-left: auto;
  padding: 2px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--surface-2);
}

.team__chip {
  padding: 4px 11px;
  border: 0;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--text-3);
  font-size: 12px;
  font-weight: 700;
}

.team__chip:hover {
  color: var(--text);
}

.team__chip--on {
  background: var(--surface);
  color: var(--brand-strong);
  box-shadow: var(--shadow-1);
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
