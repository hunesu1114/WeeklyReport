<script setup>
import { computed, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import BrandMark from '@/components/BrandMark.vue'
import DueSoonBell from '@/components/DueSoonBell.vue'
import { useToast } from '@/composables/useToast'
import { useTheme } from '@/composables/useTheme'
import { useKanbanStore } from '@/stores/kanban'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const { toasts, dismiss } = useToast()
const { isDark, toggle } = useTheme()
const kanban = useKanbanStore()
const auth = useAuthStore()

const menuOpen = ref(false)
/** 로그인 화면에서는 헤더를 감춘다. 로그아웃 상태에서 보여줄 메뉴가 없다. */
const showChrome = computed(() => auth.isLoggedIn && route.name !== 'login')

// 로그인한 뒤에야 내 데이터를 부를 수 있다. 로그아웃하면 비운다.
watch(
  () => auth.isLoggedIn,
  async (loggedIn) => {
    if (!loggedIn) {
      kanban.reset()
      return
    }
    try {
      await auth.refreshMe()
      await Promise.all([kanban.loadProjects(true), kanban.loadDueSoon()])
    } catch {
      /* 알림은 부가 기능이다. 실패해도 화면이 막히면 안 된다 */
    }
  },
  { immediate: true },
)

function logout() {
  menuOpen.value = false
  auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="shell">
    <header v-if="showChrome" class="topbar">
      <RouterLink to="/" class="brand" title="주간보고/칸반">
        <BrandMark :size="30" />
        <span class="brand__text">
          <strong>주간보고/칸반</strong>
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

        <div class="user" @mouseleave="menuOpen = false">
          <button
            class="user__btn"
            type="button"
            :title="auth.user?.username"
            :aria-expanded="menuOpen"
            @click="menuOpen = !menuOpen"
          >
            {{ (auth.user?.displayName || '?').slice(0, 1) }}
          </button>

          <div v-if="menuOpen" class="user__menu">
            <div class="user__head">
              <strong>{{ auth.user?.displayName }}</strong>
              <span class="tiny muted">
                {{ auth.user?.username }}
                <span v-if="auth.isAdmin" class="badge badge--ok">관리자</span>
              </span>
            </div>
            <button class="user__item" type="button" @click="logout">로그아웃</button>
          </div>
        </div>
      </div>
    </header>

    <main class="content" :class="{ 'content--bare': !showChrome }">
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

.content--bare {
  max-width: none;
  padding: 0;
}

/* ---------- 사용자 메뉴 ---------- */
.user {
  position: relative;
}

.user__btn {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--line);
  border-radius: 50%;
  background: var(--brand-soft);
  color: var(--brand-strong);
  font-weight: 800;
}

.user__btn:hover {
  border-color: var(--brand-border);
}

.user__menu {
  position: absolute;
  right: 0;
  top: calc(100% + 8px);
  z-index: 50;
  min-width: 200px;
  padding: 6px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: var(--shadow-3);
}

.user__head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 9px 10px;
  border-bottom: 1px solid var(--line);
  margin-bottom: 4px;
}

.user__item {
  display: block;
  width: 100%;
  padding: 9px 10px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--danger);
  font-weight: 600;
  text-align: left;
}

.user__item:hover {
  background: var(--danger-soft);
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
