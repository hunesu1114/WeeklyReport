import { createRouter, createWebHistory } from 'vue-router'
import ReportListView from '@/views/ReportListView.vue'
import ReportEditorView from '@/views/ReportEditorView.vue'
import KanbanView from '@/views/KanbanView.vue'
import LoginView from '@/views/LoginView.vue'
import MyPageView from '@/views/MyPageView.vue'
import MemoView from '@/views/MemoView.vue'
import { useAuthStore } from '@/stores/auth'

/** 브라우저 탭에 항상 붙는 앱 이름. */
export const APP_NAME = '주간보고/칸반'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true, title: '로그인' } },

    { path: '/', name: 'reports', component: ReportListView, meta: { title: '보고서 목록' } },
    {
      path: '/reports/new',
      name: 'report-new',
      component: ReportEditorView,
      meta: { title: '새 주간보고' },
    },
    {
      path: '/reports/:id',
      name: 'report-edit',
      component: ReportEditorView,
      props: true,
      meta: { title: '주간보고 수정' },
    },

    { path: '/memos', name: 'memos', component: MemoView, meta: { title: '메모' } },

    { path: '/me', name: 'my-page', component: MyPageView, meta: { title: '마이페이지' } },

    // 프로젝트를 안 고르고 들어오면 화면이 마지막으로 보던 보드로 옮겨준다
    { path: '/kanban', name: 'kanban', component: KanbanView, meta: { title: '칸반 보드' } },
    {
      path: '/kanban/:projectId',
      name: 'kanban-board',
      component: KanbanView,
      props: true,
      meta: { title: '칸반 보드' },
    },
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

// 탭 제목에도 앱 이름이 남아 있어야 여러 탭 중에서 찾을 수 있다
router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · ${APP_NAME}` : APP_NAME
})

export default router
