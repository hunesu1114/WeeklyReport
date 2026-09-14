import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { teamApi } from '@/api/client'

export const useNotificationStore = defineStore('notifications', () => {
  const items = ref([])
  const unreadCount = ref(0)
  const pendingInviteCount = ref(0)
  const loading = ref(false)

  /** 아직 답하지 않은 초대. 알림함 맨 위에 따로 세운다. */
  const invites = computed(() =>
    items.value.filter((n) => n.type === 'PROJECT_INVITE' && n.invitation?.status === 'PENDING'),
  )

  async function load() {
    loading.value = true
    try {
      const inbox = await teamApi.inbox()
      items.value = inbox.items ?? []
      unreadCount.value = inbox.unreadCount ?? 0
      pendingInviteCount.value = inbox.pendingInviteCount ?? 0
    } finally {
      loading.value = false
    }
    return items.value
  }

  async function markAllRead() {
    await teamApi.markAllRead()
    items.value = items.value.map((n) => ({ ...n, read: true }))
    unreadCount.value = 0
  }

  async function markRead(id) {
    const target = items.value.find((n) => n.id === id)
    if (!target || target.read) return
    await teamApi.markRead(id)
    target.read = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }

  /**
   * 초대를 수락한다. 보드 목록이 늘어나므로 부르는 쪽에서 칸반 스토어도 다시 읽는다.
   * 여기서 kanban 스토어를 직접 건드리지 않는 것은 두 스토어가 서로를 부르면
   * 어느 쪽이 먼저인지 따라다녀야 하기 때문이다.
   */
  async function accept(invitationId) {
    const member = await teamApi.accept(invitationId)
    await load()
    return member
  }

  async function decline(invitationId) {
    await teamApi.decline(invitationId)
    await load()
  }

  function reset() {
    items.value = []
    unreadCount.value = 0
    pendingInviteCount.value = 0
  }

  return {
    items,
    invites,
    unreadCount,
    pendingInviteCount,
    loading,
    load,
    markAllRead,
    markRead,
    accept,
    decline,
    reset,
  }
})
