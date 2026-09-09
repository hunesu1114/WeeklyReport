import { createRouter, createWebHistory } from 'vue-router'
import ReportListView from '@/views/ReportListView.vue'
import ReportEditorView from '@/views/ReportEditorView.vue'
import KanbanView from '@/views/KanbanView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'reports', component: ReportListView },
    { path: '/reports/new', name: 'report-new', component: ReportEditorView },
    { path: '/reports/:id', name: 'report-edit', component: ReportEditorView, props: true },

    // 프로젝트를 안 고르고 들어오면 화면이 마지막으로 보던 보드로 옮겨준다
    { path: '/kanban', name: 'kanban', component: KanbanView },
    { path: '/kanban/:projectId', name: 'kanban-board', component: KanbanView, props: true },
  ],
})

export default router
