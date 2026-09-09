<script setup>
import { onMounted } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import DueSoonBell from '@/components/DueSoonBell.vue'
import { useToast } from '@/composables/useToast'
import { useTheme } from '@/composables/useTheme'
import { useKanbanStore } from '@/stores/kanban'

const { toasts, dismiss } = useToast()
const { isDark, toggle } = useTheme()
const kanban = useKanbanStore()

onMounted(async () => {
  // 임박 알림은 어느 화면에 있든 헤더에 떠 있어야 한다.
  // 실패해도 화면 전체가 막히면 안 되므로 조용히 넘긴다.
  try {
    await Promise.all([kanban.loadProjects(), kanban.loadDueSoon()])
  } catch {
    /* 알림은 부가 기능이다 */
  }
})
</script>

<template>
  <div class="shell">
    <header class="topbar">
      <RouterLink to="/" class="brand">
        <span class="brand__mark" aria-hidden="true">W</span>
        <span class="brand__text">
          <strong>주간보고</strong>
          <em>Weekly Report &amp; Kanban</em>
        </span>
      </RouterLink>

      <nav class="topnav">
        <RouterLink to="/">보고서</RouterLink>
        <RouterLink to="/kanban">칸반</RouterLink>
      </nav>

      <div class="topbar__right">
        <DueSoonBell />
        <button
          class="themebtn"
          type="button"
          :title="isDark ? '라이트 모드로' : '다크 모드로'"
          :aria-label="isDark ? '라이트 모드로 전환' : '다크 모드로 전환'"
          @click="toggle"
        >
          <span aria-hidden="true">{{ isDark ? '☀' : '☾' }}</span>
        </button>
        <RouterLink class="btn btn--primary btn--sm" to="/reports/new">새 주간보고</RouterLink>
      </div>
    </header>

    <main class="content">
      <RouterView />
    </main>

    <div class="toasts" role="status" aria-live="polite">
      <button
        v-for="toast in toasts"
        :key="toast.id"
        class="toast"
        :class="`toast--${toast.tone}`"
        type="button"
        @click="dismiss(toast.id)"
      >
        {{ toast.message }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 0 20px;
  height: var(--header-h);
  background: var(--surface);
  border-bottom: 1px solid var(--line);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--text);
  text-decoration: none;
}

.brand__mark {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: var(--brand);
  color: var(--on-brand);
  font-weight: 800;
  font-size: 15px;
}

.brand__text {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.brand__text strong {
  font-size: 14px;
  letter-spacing: -0.02em;
}

.brand__text em {
  font-style: normal;
  font-size: 10px;
  color: var(--text-4);
  letter-spacing: 0.02em;
}

.topnav {
  display: flex;
  align-items: center;
  gap: 2px;
}

.topnav a {
  padding: 7px 13px;
  border-radius: var(--radius-sm);
  color: var(--text-3);
  font-weight: 600;
  text-decoration: none;
  transition: background 0.12s, color 0.12s;
}

.topnav a:hover {
  background: var(--surface-hover);
  color: var(--text);
}

.topnav a.router-link-active {
  background: var(--brand-soft);
  color: var(--brand-strong);
}

.topbar__right {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.topbar__right .btn {
  text-decoration: none;
}

.themebtn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text-2);
  font-size: 15px;
  transition: background 0.12s, border-color 0.12s;
}

.themebtn:hover {
  background: var(--surface-hover);
  border-color: var(--line-strong);
}

.content {
  flex: 1;
  width: 100%;
  max-width: 1560px;
  margin: 0 auto;
  padding: 20px;
}

.toasts {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 90;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-end;
}

.toast {
  max-width: 420px;
  padding: 10px 14px;
  border: 1px solid var(--line);
  border-left: 3px solid var(--text-3);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text);
  box-shadow: var(--shadow-2);
  text-align: left;
  font-weight: 600;
  animation: toast-in 0.16s ease-out;
}

.toast--success {
  border-left-color: var(--brand);
}

.toast--error {
  border-left-color: var(--danger);
  color: var(--danger);
}

@keyframes toast-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
}

@media (max-width: 720px) {
  .brand__text {
    display: none;
  }

  .topbar {
    gap: 10px;
    padding: 0 12px;
  }
}
</style>
