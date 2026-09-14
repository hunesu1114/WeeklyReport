<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import UserAvatar from '@/components/UserAvatar.vue'
import { useKanbanStore } from '@/stores/kanban'
import { useNotificationStore } from '@/stores/notifications'
import { useToast } from '@/composables/useToast'
import { PRIORITY_META, dueLabel, dueTone } from '@/utils/kanban'
import { timeAgo } from '@/utils/team'

const kanban = useKanbanStore()
const inbox = useNotificationStore()
const router = useRouter()
const toast = useToast()

const open = ref(false)
const tab = ref('inbox')
const root = ref(null)
const busyId = ref(null)

const cards = computed(() => kanban.dueSoon)
const dueCount = computed(() => kanban.dueSoonCount)
const overdue = computed(() => kanban.overdueCount)

/** 배지에는 '내가 아직 처리하지 않은 것'만 센다. 읽은 알림은 빠진다. */
const total = computed(() => inbox.unreadCount + dueCount.value)
const urgent = computed(() => overdue.value > 0 || inbox.pendingInviteCount > 0)

function onDocClick(event) {
  if (open.value && root.value && !root.value.contains(event.target)) open.value = false
}

onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))

async function toggle() {
  open.value = !open.value
  if (!open.value) return
  try {
    await inbox.load()
  } catch {
    /* 알림은 부가 기능이다. 못 읽어도 화면이 막히면 안 된다 */
  }
}

async function accept(notification) {
  busyId.value = notification.id
  try {
    await inbox.accept(notification.invitation.id)
    await kanban.refresh()
    toast.success(`'${notification.invitation.projectName}' 보드에 참여했습니다.`)
    router.push({ name: 'kanban-board', params: { projectId: notification.invitation.projectId } })
    open.value = false
  } catch (error) {
    toast.error(error.message)
  } finally {
    busyId.value = null
  }
}

async function decline(notification) {
  busyId.value = notification.id
  try {
    await inbox.decline(notification.invitation.id)
    toast.success('초대를 거절했습니다.')
  } catch (error) {
    toast.error(error.message)
  } finally {
    busyId.value = null
  }
}

async function openNotification(notification) {
  if (!notification.read) {
    inbox.markRead(notification.id).catch(() => {})
  }
  if (notification.link) {
    open.value = false
    router.push(notification.link)
  }
}

function goToCard(card) {
  open.value = false
  router.push({ name: 'kanban-board', params: { projectId: card.projectId } })
}

async function readAll() {
  try {
    await inbox.markAllRead()
  } catch (error) {
    toast.error(error.message)
  }
}
</script>

<template>
  <div ref="root" class="bell">
    <button
      class="bell__btn"
      :class="{ 'bell__btn--alert': total > 0, 'bell__btn--urgent': urgent }"
      type="button"
      :title="total ? `읽지 않은 알림 ${inbox.unreadCount}건 · 완료일 임박 ${dueCount}건` : '새 알림이 없습니다'"
      :aria-expanded="open"
      @click="toggle"
    >
      <span aria-hidden="true">🔔</span>
      <span v-if="total" class="bell__count">{{ total > 99 ? '99+' : total }}</span>
      <span class="sr-only">알림 {{ total }}건</span>
    </button>

    <div v-if="open" class="pop">
      <header class="pop__head">
        <div class="pop__tabs" role="tablist">
          <button
            class="pop__tab"
            :class="{ 'pop__tab--on': tab === 'inbox' }"
            type="button"
            role="tab"
            :aria-selected="tab === 'inbox'"
            @click="tab = 'inbox'"
          >
            알림
            <span v-if="inbox.unreadCount" class="pop__tabcount">{{ inbox.unreadCount }}</span>
          </button>
          <button
            class="pop__tab"
            :class="{ 'pop__tab--on': tab === 'due' }"
            type="button"
            role="tab"
            :aria-selected="tab === 'due'"
            @click="tab = 'due'"
          >
            임박
            <span v-if="dueCount" class="pop__tabcount pop__tabcount--warn">{{ dueCount }}</span>
          </button>
        </div>
        <button
          v-if="tab === 'inbox' && inbox.unreadCount"
          class="btn btn--ghost btn--sm"
          type="button"
          @click="readAll"
        >
          모두 읽음
        </button>
      </header>

      <!-- 알림 -->
      <template v-if="tab === 'inbox'">
        <p v-if="inbox.loading" class="pop__empty muted tiny">불러오는 중…</p>
        <p v-else-if="!inbox.items.length" class="pop__empty muted tiny">새 알림이 없습니다.</p>

        <ul v-else class="pop__list">
          <li v-for="item in inbox.items" :key="item.id" class="note" :class="{ 'note--new': !item.read }">
            <button class="note__main" type="button" @click="openNotification(item)">
              <span class="note__row">
                <span class="note__title">{{ item.title }}</span>
                <span class="note__time tiny muted">{{ timeAgo(item.createdAt) }}</span>
              </span>
              <span v-if="item.message" class="note__msg tiny muted">{{ item.message }}</span>
            </button>

            <!-- 초대는 알림함에서 바로 답한다. 다른 화면으로 옮겨갈 이유가 없다 -->
            <div
              v-if="item.type === 'PROJECT_INVITE' && item.invitation?.status === 'PENDING'"
              class="note__invite"
            >
              <UserAvatar :user="item.invitation.inviter" :size="22" />
              <span class="tiny muted note__inviter">
                {{ item.invitation.inviter?.displayName }} 님이 초대했습니다
              </span>
              <button
                class="btn btn--sm"
                type="button"
                :disabled="busyId === item.id"
                @click="decline(item)"
              >
                거절
              </button>
              <button
                class="btn btn--sm btn--primary"
                type="button"
                :disabled="busyId === item.id"
                @click="accept(item)"
              >
                수락
              </button>
            </div>
          </li>
        </ul>
      </template>

      <!-- 완료일 임박 -->
      <template v-else>
        <p v-if="!cards.length" class="pop__empty muted tiny">
          임박한 카드가 없습니다. 여유 있네요.
        </p>
        <ul v-else class="pop__list">
          <li v-for="card in cards" :key="card.id">
            <button class="pop__item" type="button" @click="goToCard(card)">
              <span class="pop__row">
                <span class="pop__title">{{ card.title }}</span>
                <span class="pop__due" :class="`pop__due--${dueTone(card.daysUntilDue)}`">
                  {{ dueLabel(card.daysUntilDue) }}
                </span>
              </span>
              <span class="pop__meta tiny muted">
                {{ card.projectName }} · {{ card.status }} ·
                {{ PRIORITY_META[card.priority]?.label }}
              </span>
            </button>
          </li>
        </ul>
        <p class="pop__foot tiny muted">내가 담당한 카드만 보여줍니다. 3일 이내 · 지난 것 포함</p>
      </template>
    </div>
  </div>
</template>

<style scoped>
.bell {
  position: relative;
}

.bell__btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--surface);
  transition: border-color 0.12s, background 0.12s;
}

.bell__btn:hover {
  background: var(--surface-hover);
  border-color: var(--line-strong);
}

.bell__btn--alert {
  border-color: var(--warn-border);
  background: var(--warn-soft);
}

.bell__btn--urgent {
  border-color: var(--danger-border);
  background: var(--danger-soft);
}

.bell__count {
  position: absolute;
  top: -6px;
  right: -6px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--danger-bright);
  color: #fff;
  font-size: 11px;
  font-weight: 800;
  line-height: 18px;
}

.pop {
  position: absolute;
  right: 0;
  top: calc(100% + 8px);
  z-index: 50;
  width: 380px;
  max-height: 66vh;
  overflow-y: auto;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: var(--shadow-3);
  animation: pop-in 0.13s ease-out;
}

.pop__head {
  position: sticky;
  top: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--line);
  background: var(--surface);
}

.pop__tabs {
  display: flex;
  gap: 2px;
}

.pop__tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 11px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-3);
  font-size: 13px;
  font-weight: 700;
}

.pop__tab:hover {
  background: var(--surface-hover);
  color: var(--text);
}

.pop__tab--on {
  background: var(--brand-soft);
  color: var(--brand-strong);
}

.pop__tabcount {
  min-width: 17px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--bg-subtle);
  color: var(--text-3);
  font-size: 11px;
  font-weight: 800;
}

.pop__tabcount--warn {
  background: var(--danger-soft);
  color: var(--danger);
}

.pop__empty {
  margin: 0;
  padding: 26px 14px;
  text-align: center;
}

.pop__foot {
  margin: 0;
  padding: 8px 12px 12px;
  text-align: center;
}

.pop__list {
  margin: 0;
  padding: 6px;
  list-style: none;
}

.pop__item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  width: 100%;
  padding: 9px 10px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  text-align: left;
}

.pop__item:hover {
  background: var(--surface-hover);
}

.pop__row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.pop__title {
  font-weight: 700;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pop__due {
  flex: none;
  font-size: 11px;
  font-weight: 800;
}

.pop__due--warn {
  color: var(--warn);
}
.pop__due--danger {
  color: var(--danger);
}
.pop__due--neutral {
  color: var(--text-3);
}

/* ---------- 알림 한 줄 ---------- */
.note {
  border-radius: var(--radius-sm);
}

.note:hover {
  background: var(--surface-hover);
}

.note--new {
  background: var(--brand-soft);
}

.note__main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  width: 100%;
  padding: 9px 10px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  text-align: left;
}

.note__row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.note__title {
  font-size: 13px;
  font-weight: 700;
  line-height: 1.45;
  word-break: break-word;
}

.note__time {
  flex: none;
}

.note__msg {
  line-height: 1.5;
}

.note__invite {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 0 10px 10px;
}

.note__inviter {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@keyframes pop-in {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
}
</style>
