import { createRouter, createWebHistory } from 'vue-router'
import ReportListView from '@/views/ReportListView.vue'
import ReportEditorView from '@/views/ReportEditorView.vue'
import KanbanView from '@/views/KanbanView.vue'
import LoginView from '@/views/LoginView.vue'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },

    { path: '/', name: 'reports', component: ReportListView },
    { path: '/reports/new', name: 'report-new', component: ReportEditorView },
    { path: '/reports/:id', name: 'report-edit', component: ReportEditorView, props: true },

    // 프로젝트를 안 고르고 들어오면 화면이 마지막으로 보던 보드로 옮겨준다
    { path: '/kanban', name: 'kanban', component: KanbanView },
    { path: '/kanban/:projectId', name: 'kanban-board', component: KanbanView, props: true },
  ],
})

/**
 * 로그인하지 않았으면 로그인 화면으로 보낸다.
 * 원래 가려던 곳을 redirect 로 남겨 로그인 후 그대로 이어가게 한다.
 */
router.beforeEach((to) => {
  const auth = useAuthStore()

  if (to.meta.public) {
    // 이미 로그인한 사람이 로그인 화면에 오면 되돌린다
    return auth.isLoggedIn ? { path: '/' } : true
  }
  if (!auth.isLoggedIn) {
    return { name: 'login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  }
  return true
})

export default router
