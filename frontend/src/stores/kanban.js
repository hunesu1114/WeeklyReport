import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { kanbanApi } from '@/api/client'

const LAST_PROJECT_KEY = 'weekly-report:last-project'

export const useKanbanStore = defineStore('kanban', () => {
  const projects = ref([])
  const dueSoon = ref([])
  const loaded = ref(false)

  const dueSoonCount = computed(() => dueSoon.value.length)
  /** 이미 완료일이 지난 카드 수. 헤더에서 빨간 배지로 쓴다. */
  const overdueCount = computed(() => dueSoon.value.filter((c) => c.daysUntilDue < 0).length)

  async function loadProjects(force = false) {
    if (loaded.value && !force) return projects.value
    projects.value = await kanbanApi.projects()
    loaded.value = true
    return projects.value
  }

  async function loadDueSoon() {
    dueSoon.value = await kanbanApi.dueSoon()
    return dueSoon.value
  }

  async function refresh() {
    await Promise.all([loadProjects(true), loadDueSoon()])
  }

  /** 마지막으로 보던 보드를 기억해 두었다가 다음에 그대로 연다. */
  function rememberProject(id) {
    try {
      localStorage.setItem(LAST_PROJECT_KEY, String(id))
    } catch {
      /* 저장이 막혀 있어도 동작에는 지장이 없다 */
    }
  }

  function lastProjectId() {
    try {
      const raw = localStorage.getItem(LAST_PROJECT_KEY)
      return raw ? Number(raw) : null
    } catch {
      return null
    }
  }

  return {
    projects,
    dueSoon,
    loaded,
    dueSoonCount,
    overdueCount,
    loadProjects,
    loadDueSoon,
    refresh,
    rememberProject,
    lastProjectId,
  }
})
