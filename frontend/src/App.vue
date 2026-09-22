<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import BrandMark from '@/components/BrandMark.vue'
import NotificationBell from '@/components/NotificationBell.vue'
import SessionBanner from '@/components/SessionBanner.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { useToast } from '@/composables/useToast'
import { useTheme } from '@/composables/useTheme'
import { useKanbanSocket } from '@/composables/useKanbanSocket'
import { useKanbanStore } from '@/stores/kanban'
import { useNotificationStore } from '@/stores/notifications'
import { useMemoStore } from '@/stores/memo'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const { toasts, dismiss } = useToast()
const { isDark, toggle } = useTheme()
const kanban = useKanbanStore()
const inbox = useNotificationStore()
const memo = useMemoStore()
const auth = useAuthStore()

/**
 * 알림용 연결. 보드를 보고 있지 않아도 초대는 도착해야 한다.
 * 칸반 화면은 자기 것을 따로 열어 보드를 구독한다 — 연결 두 개는
 * 각자 살고 죽어서, 보드를 떠난다고 알림이 끊기지 않는다.
 */
const socket = useKanbanSocket({
  'inbox-changed': () => inbox.load().catch(() => {}),
  // 탭으로 돌아왔을 때. 자리를 비운 사이 도착한 초대가 있을 수 있다.
  resync: () => {
    if (!auth.isLoggedIn) return
    inbox.load().catch(() => {})
    kanban.loadDueSoon().catch(() => {})
  },
})

const menuOpen = ref(false)
const userMenu = ref(null)

/**
 * 메뉴는 바깥을 눌렀을 때 닫는다.
 *
 * mouseleave 로 닫으면 버튼에서 메뉴로 마우스를 옮기는 도중 그 사이 여백을
 * 지나는 순간 닫혀 버려서, 메뉴 항목을 아예 누를 수 없다.
 */
function onDocPointerDown(event) {
  if (menuOpen.value && userMenu.value && !userMenu.value.contains(event.target)) {
    menuOpen.value = false
  }
}

function onEscape(event) {
  if (event.key === 'Escape') menuOpen.value = false
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocPointerDown)
  document.addEventListener('keydown', onEscape)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocPointerDown)
  document.removeEventListener('keydown', onEscape)
})

// 화면을 옮기면 메뉴는 닫혀 있어야 한다
watch(() => route.fullPath, () => (menuOpen.value = false))

/**
 * 지금 어느 메뉴에 있는지.
 *
 * router-link-active 를 쓰면 to="/" 가 모든 경로의 접두사라 보고서가 늘 켜져 보인다.
 * 경로가 아니라 라우트 이름으로 묶어서 판단한다. 마이페이지처럼 어느 쪽도
 * 아닌 화면에서는 둘 다 꺼둔다 — 있지도 않은 곳이 켜져 보이면 더 헷갈린다.
 */
const section = computed(() => {
  const name = String(route.name ?? '')
  if (name.startsWith('kanban')) return 'kanban'
  if (name === 'memos') return 'memos'
  if (name === 'reports' || name.startsWith('report-')) return 'reports'
  return null
})
/** 로그인 화면에서는 헤더를 감춘다. 로그아웃 상태에서 보여줄 메뉴가 없다. */
const showChrome = computed(() => auth.isLoggedIn && route.name !== 'login')

// 로그인한 뒤에야 내 데이터를 부를 수 있다. 로그아웃하면 비운다.
watch(
  () => auth.isLoggedIn,
  async (loggedIn) => {
    if (!loggedIn) {
      kanban.reset()
      inbox.reset()
      memo.reset()
      socket.close()
      return
    }
    try {
      await auth.refreshMe()
      await Promise.all([kanban.loadProjects(true), kanban.loadDueSoon(), inbox.load()])
      socket.connect()
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
        <RouterLink to="/" :class="{ 'topnav--on': section === 'reports' }">보고서</RouterLink>
        <RouterLink to="/kanban" :class="{ 'topnav--on': section === 'kanban' }">칸반</RouterLink>
        <RouterLink to="/memos" :class="{ 'topnav--on': section === 'memos' }">메모</RouterLink>

        <!--
          다른 앱이라 RouterLink 가 아니라 a 다. 새 창으로 여는 만큼 ↗ 로 미리 알린다 —
          같은 줄에 나란히 있으면 이것도 화면이 바뀌는 탭으로 읽힌다.
          noopener 없이 열면 저쪽 문서가 window.opener 로 이 창을 건드릴 수 있다.
        -->
        <a
          class="topnav__out"
          href="https://filehub-khs.duckdns.org/"
          target="_blank"
          rel="noopener noreferrer"
        >
          드라이브
          <span class="topnav__ext" aria-hidden="true">↗</span>
          <span class="sr-only">(새 창에서 열림)</span>
        </a>
      </nav>

      <div class="topbar__right">
        <NotificationBell />
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

        <div ref="userMenu" class="user">
          <button
            class="user__btn"
            type="button"
            :title="auth.user?.username"
            :aria-expanded="menuOpen"
            @click="menuOpen = !menuOpen"
          >
            <UserAvatar :user="auth.user" :size="32" />
          </button>

          <div v-if="menuOpen" class="user__menu">
            <div class="user__head">
              <UserAvatar :user="auth.user" :size="36" />
              <span class="user__who">
                <strong>{{ auth.user?.displayName }}</strong>
                <span class="tiny muted">
                  {{ auth.user?.username }}
                  <span v-if="auth.isAdmin" class="badge badge--ok">관리자</span>
                </span>
              </span>
            </div>
            <RouterLink class="user__item user__item--link" to="/me" @click="menuOpen = false">
              마이페이지
            </RouterLink>
            <button class="user__item user__item--out" type="button" @click="logout">
              로그아웃
            </button>
          </div>
        </div>
      </div>
    </header>

    <SessionBanner v-if="showChrome" />

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

.topnav a.topnav--on {
  background: var(--brand-soft);
  color: var(--brand-strong);
}

/* 여기만 앱 밖으로 나간다. 화살표가 글자에 붙어 다니도록 묶어 둔다 */
.topnav__out {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.topnav__ext {
  font-size: 10px;
  line-height: 1;
  opacity: 0.6;
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
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  line-height: 0;
}

.user__btn:hover {
  box-shadow: 0 0 0 2px var(--brand-border);
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
  align-items: center;
  gap: 9px;
  padding: 9px 10px;
  border-bottom: 1px solid var(--line);
  margin-bottom: 4px;
}

.user__who {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.user__item {
  display: block;
  width: 100%;
  padding: 9px 10px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  font-weight: 600;
  text-align: left;
  text-decoration: none;
}

.user__item--link {
  color: var(--text);
}

.user__item--link:hover {
  background: var(--surface-hover);
}

.user__item--out {
  color: var(--danger);
}

.user__item--out:hover {
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
