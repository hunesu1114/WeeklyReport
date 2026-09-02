import { createRouter, createWebHistory } from 'vue-router'
import ReportListView from '@/views/ReportListView.vue'
import ReportEditorView from '@/views/ReportEditorView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'reports', component: ReportListView },
    { path: '/reports/new', name: 'report-new', component: ReportEditorView },
    { path: '/reports/:id', name: 'report-edit', component: ReportEditorView, props: true },
  ],
})

export default router
