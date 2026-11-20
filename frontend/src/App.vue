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
const { toasts, dismiss, act } = useToast()
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

/**
 * 메모는 머리글 아래를 가장자리까지 쓴다. 폴더 · 목록 · 편집기 세 칸이
 * 면의 밝기로만 나뉘어서, 둘레에 여백이 있으면 칸 경계가 흐려진다.
 */
const flush = computed(() => route.name === 'memos')

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
      <!-- MW 는 My Work 의 줄임말. 풀어 쓴 이름은 손을 올렸을 때만 보인다 -->
      <RouterLink to="/" class="brand" title="MW · My Work">
        <BrandMark :size="20" />
        <span class="brand__text">MW</span>
      </RouterLink>

      <!-- 지금 있는 곳은 배경색이 아니라 밑줄로. 머리글에서 강조색을 아낀다 -->
      <nav class="topnav" aria-label="주 메뉴">
        <RouterLink
          to="/"
          :class="{ 'topnav--on': section === 'reports' }"
          :aria-current="section === 'reports' ? 'page' : undefined"
        >
          보고서
        </RouterLink>
        <RouterLink
          to="/kanban"
          :class="{ 'topnav--on': section === 'kanban' }"
          :aria-current="section === 'kanban' ? 'page' : undefined"
        >
          칸반
        </RouterLink>
        <RouterLink
          to="/memos"
          :class="{ 'topnav--on': section === 'memos' }"
          :aria-current="section === 'memos' ? 'page' : undefined"
        >
          메모
        </RouterLink>

        <!--
          다른 앱이라 RouterLink 가 아니라 a 다. 새 창으로 여는 만큼 화살표로 미리 알린다 —
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
          <AppIcon name="external" :size="12" />
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
          <AppIcon :name="isDark ? 'sun' : 'moon'" :size="16" />
        </button>
        <!-- 어느 화면에서든 누를 수 있는 길목이라 초록을 쓰지 않는다. 화면 안의 실행 버튼과 겹친다 -->
        <RouterLink class="btn btn--sm topbar__new" to="/reports/new">
          <AppIcon name="plus" :size="14" />
          새 주간보고
        </RouterLink>

        <div ref="userMenu" class="user">
          <button
            class="user__btn"
            type="button"
            :title="auth.user?.username"
            :aria-expanded="menuOpen"
            @click="menuOpen = !menuOpen"
          >
            <UserAvatar :user="auth.user" :size="28" />
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

    <main class="content" :class="{ 'content--bare': !showChrome, 'content--flush': showChrome && flush }">
      <RouterView />
    </main>

    <div class="toasts" role="status" aria-live="polite">
      <div v-for="toast in toasts" :key="toast.id" class="toast" :class="`toast--${toast.tone}`">
        <button class="toast__msg" type="button" title="닫기" @click="dismiss(toast.id)">
          {{ toast.message }}
        </button>
        <button v-if="toast.action" class="toast__act" type="button" @click="act(toast)">
          {{ toast.action.label }}
          <kbd v-if="toast.action.hint" class="toast__kbd">{{ toast.action.hint }}</kbd>
        </button>
      </div>
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
  gap: 28px;
  padding: 0 16px 0 20px;
  height: var(--header-h);
  background: var(--surface);
  border-bottom: 1px solid var(--line);
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text);
  text-decoration: none;
}

.brand__text {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.02em;
  line-height: 1;
}

/* 탭은 머리글 높이를 다 쓰고, 밑줄이 머리글 아래 선 위에 얹힌다 */
.topnav {
  display: flex;
  align-self: stretch;
  gap: 22px;
}

.topnav a {
  display: flex;
  align-items: center;
  padding: 0 1px;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  color: var(--text-3);
  font-weight: 500;
  text-decoration: none;
  white-space: nowrap;
  transition: color 0.12s, border-color 0.12s;
}

.topnav a:hover {
  color: var(--text);
}

.topnav a.topnav--on {
  border-bottom-color: var(--text);
  color: var(--text);
  font-weight: 600;
}

/* 여기만 앱 밖으로 나간다. 화살표가 글자에 붙어 다니도록 묶어 둔다 */
.topnav__out {
  gap: 3px;
}

.topbar__right {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}

.topbar__new {
  margin: 0 8px 0 6px;
  text-decoration: none;
}

.themebtn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-2);
  transition: background 0.12s, color 0.12s;
}

.themebtn:hover {
  background: var(--surface-hover);
  color: var(--text);
}

.content {
  flex: 1;
  width: 100%;
  max-width: 1560px;
  margin: 0 auto;
  padding: 24px;
}

.content--bare {
  max-width: none;
  padding: 0;
}

.content--flush {
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
  box-shadow: 0 0 0 2px var(--line-strong);
}

.user__menu {
  position: absolute;
  right: 0;
  top: calc(100% + 8px);
  z-index: 50;
  min-width: 200px;
  padding: 6px;
  border: 1px solid var(--line-strong);
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
  padding: 8px 10px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  font-weight: 500;
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

/* ---------- 토스트 ---------- */
/*
 * 화면과 반대 색으로 띄운다. 흰 화면에서는 검게, 다크에서는 밝게 —
 * 테두리나 그림자를 겹겹이 얹지 않아도 화면 위에 떠 있는 것으로 읽힌다.
 */
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
  display: flex;
  align-items: center;
  gap: 4px;
  max-width: 460px;
  padding: 4px;
  border-radius: var(--radius-sm);
  background: var(--text);
  color: var(--surface);
  box-shadow: var(--shadow-2);
  animation: toast-in 0.16s ease-out;
}

.toast__msg {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
  padding: 6px 10px;
  border: 0;
  background: transparent;
  color: inherit;
  font-size: 13px;
  text-align: left;
}

/* 됐는지 안 됐는지는 앞의 작은 점 색으로. 글자 전체를 빨갛게 칠하지 않는다 */
.toast--success .toast__msg::before,
.toast--error .toast__msg::before {
  content: '';
  flex: none;
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.toast--success .toast__msg::before {
  background: var(--brand-border);
}

.toast--error .toast__msg::before {
  background: var(--danger-bright);
}

.toast__act {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: none;
  height: 30px;
  padding: 0 10px;
  border: 0;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--brand-border);
  font-size: 13px;
  font-weight: 600;
}

.toast__act:hover {
  background: color-mix(in srgb, var(--surface) 12%, transparent);
}

.toast__kbd {
  border-color: color-mix(in srgb, var(--surface) 30%, transparent);
  background: transparent;
  color: inherit;
  opacity: 0.8;
}

@keyframes toast-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
}

@media (max-width: 720px) {
  .topbar {
    gap: 14px;
    padding: 0 10px 0 12px;
  }

  .topnav {
    gap: 14px;
  }

  /* 좁은 화면에서는 머리글 버튼이 탭을 밀어낸다. 새 주간보고는 보고서 목록에도 있다 */
  .topbar__new {
    display: none;
  }
}

/* 휴대폰. 앱 밖으로 나가는 드라이브는 내린다 — 탭 넷이 한 줄에 들어가지 않는다 */
@media (max-width: 480px) {
  .topbar {
    gap: 12px;
  }

  .topnav {
    gap: 12px;
  }

  .topnav__out {
    display: none !important;
  }
}
</style>
