<script setup>
import { RouterLink, RouterView } from 'vue-router'
import { useToast } from '@/composables/useToast'

const { toasts, dismiss } = useToast()
</script>

<template>
  <div class="shell">
    <header class="topbar">
      <RouterLink to="/" class="brand">
        <span class="brand__mark">주</span>
        <span class="brand__text">
          <strong>주간보고</strong>
          <em>작성 &amp; 엑셀 내보내기</em>
        </span>
      </RouterLink>
      <nav class="topnav">
        <RouterLink to="/">보고서 목록</RouterLink>
        <RouterLink class="topnav__cta" to="/reports/new">새 주간보고</RouterLink>
      </nav>
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
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 20px;
  height: 56px;
  background: var(--navy);
  color: #fff;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  color: inherit;
  text-decoration: none;
}

.brand__mark {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: var(--sky);
  color: var(--navy);
  font-weight: 800;
  font-size: 15px;
}

.brand__text {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.brand__text strong {
  font-size: 15px;
  letter-spacing: -0.02em;
}

.brand__text em {
  font-style: normal;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.62);
}

.topnav {
  display: flex;
  align-items: center;
  gap: 6px;
}

.topnav a {
  padding: 7px 12px;
  border-radius: var(--radius-sm);
  color: rgba(255, 255, 255, 0.78);
  font-weight: 600;
  text-decoration: none;
  transition: background 0.12s, color 0.12s;
}

.topnav a:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
}

.topnav a.router-link-exact-active {
  color: #fff;
}

.topnav__cta {
  background: var(--sky);
  color: var(--navy) !important;
}

.topnav__cta:hover {
  background: #d3e6f6;
}

.content {
  flex: 1;
  width: 100%;
  max-width: 1500px;
  margin: 0 auto;
  padding: 20px;
}

.toasts {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 60;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-end;
}

.toast {
  max-width: 420px;
  padding: 10px 14px;
  border: 1px solid var(--line);
  border-left: 3px solid var(--ink-3);
  border-radius: var(--radius-sm);
  background: var(--surface);
  box-shadow: var(--shadow-2);
  text-align: left;
  font-weight: 600;
  animation: toast-in 0.16s ease-out;
}

.toast--success {
  border-left-color: var(--ok);
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
</style>
