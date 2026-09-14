<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import BaseModal from '@/components/BaseModal.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { teamApi } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { ROLES, ROLE_META, isOwner, timeAgo } from '@/utils/team'

const props = defineProps({
  project: { type: Object, required: true },
})
const emit = defineEmits(['close', 'changed', 'left'])

const auth = useAuthStore()
const toast = useToast()

const members = ref([])
const invitations = ref([])
const loading = ref(true)
const busy = ref(false)

const query = ref('')
const results = ref([])
const searching = ref(false)
const inviteRole = ref('MEMBER')
let searchTimer = null

const myRole = computed(() => props.project.myRole)
const amOwner = computed(() => isOwner(myRole.value))
const myUserId = computed(() => auth.user?.id)

onMounted(load)

async function load() {
  loading.value = true
  try {
    members.value = await teamApi.members(props.project.id)
    // 보낸 초대는 관리자만 관리한다. 멤버에게는 굳이 보여주지 않는다.
    invitations.value = amOwner.value ? await teamApi.pendingInvitations(props.project.id) : []
  } catch (error) {
    toast.error(error.message)
  } finally {
    loading.value = false
  }
}

/** 한 글자 칠 때마다 부르면 서버가 괴롭다. 멈춘 뒤에 한 번 찾는다. */
watch(query, (value) => {
  clearTimeout(searchTimer)
  const q = value.trim()
  if (!q) {
    results.value = []
    return
  }
  searching.value = true
  searchTimer = setTimeout(async () => {
    try {
      results.value = await teamApi.searchUsers(q, props.project.id)
    } catch (error) {
      results.value = []
      toast.error(error.message)
    } finally {
      searching.value = false
    }
  }, 250)
})

const invitedIds = computed(() => new Set(invitations.value.map((i) => i.invitee?.id)))

async function invite(user) {
  busy.value = true
  try {
    await teamApi.invite(props.project.id, user.id, inviteRole.value)
    toast.success(`${user.displayName} 님에게 초대를 보냈습니다. 수락하면 참여자가 됩니다.`)
    query.value = ''
    results.value = []
    await load()
  } catch (error) {
    toast.error(error.message)
  } finally {
    busy.value = false
  }
}

async function cancelInvite(invitation) {
  busy.value = true
  try {
    await teamApi.cancelInvitation(props.project.id, invitation.id)
    toast.success('초대를 취소했습니다.')
    await load()
  } catch (error) {
    toast.error(error.message)
  } finally {
    busy.value = false
  }
}

async function changeRole(member, role) {
  if (member.role === role) return
  busy.value = true
  try {
    await teamApi.changeRole(props.project.id, member.userId, role)
    await load()
    emit('changed')
  } catch (error) {
    toast.error(error.message)
    await load() // 실패했으면 select 를 서버 값으로 되돌린다
  } finally {
    busy.value = false
  }
}

async function remove(member) {
  const mine = member.userId === myUserId.value
  // 담당 카드는 조용히 사라지는 게 아니라 담당 없음이 된다. 누르기 전에 알려준다.
  const cards = member.assignedCount
    ? `\n담당 중인 카드 ${member.assignedCount}장이 담당 없음이 됩니다.`
    : ''
  const message = mine
    ? `'${props.project.name}' 보드에서 나갈까요?\n다시 들어오려면 초대를 받아야 합니다.${cards}`
    : `${member.displayName} 님을 내보낼까요?${cards}`
  if (!window.confirm(message)) return

  busy.value = true
  try {
    await teamApi.removeMember(props.project.id, member.userId)
    if (mine) {
      toast.success('보드에서 나왔습니다.')
      emit('left')
      return
    }
    toast.success(`${member.displayName} 님을 내보냈습니다.`)
    await load()
    emit('changed')
  } catch (error) {
    toast.error(error.message)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <BaseModal
    title="참여자"
    :subtitle="`${project.name} · ${ROLE_META[myRole]?.label ?? '참여자'}로 보는 중`"
    width="620px"
    @close="emit('close')"
  >
    <div class="mem">
      <!-- 초대 -->
      <section v-if="amOwner" class="mem__invite">
        <div class="field">
          <label for="mem-search">사람 초대</label>
          <div class="mem__searchbar">
            <input
              id="mem-search"
              v-model="query"
              class="control"
              type="search"
              placeholder="아이디 또는 이름으로 찾기"
              autocomplete="off"
            />
            <select v-model="inviteRole" class="control mem__role" aria-label="초대할 역할">
              <option v-for="role in ROLES" :key="role" :value="role">
                {{ ROLE_META[role].label }}
              </option>
            </select>
          </div>
          <span class="tiny muted">{{ ROLE_META[inviteRole].hint }}</span>
        </div>

        <p v-if="searching" class="mem__state tiny muted">찾는 중…</p>
        <p v-else-if="query.trim() && !results.length" class="mem__state tiny muted">
          해당하는 사용자가 없습니다.
        </p>

        <ul v-else-if="results.length" class="mem__results">
          <li v-for="user in results" :key="user.id" class="mem__row">
            <UserAvatar :user="user" :size="30" />
            <span class="mem__who">
              <strong>{{ user.displayName }}</strong>
              <span class="tiny muted">{{ user.username }}</span>
            </span>
            <span v-if="invitedIds.has(user.id)" class="badge">초대함</span>
            <button
              v-else
              class="btn btn--sm btn--primary"
              type="button"
              :disabled="busy"
              @click="invite(user)"
            >
              초대하기
            </button>
          </li>
        </ul>
      </section>

      <!-- 답을 기다리는 초대 -->
      <section v-if="amOwner && invitations.length" class="mem__block">
        <h3 class="mem__title">
          보낸 초대 <span class="badge">{{ invitations.length }}</span>
        </h3>
        <ul class="mem__list">
          <li v-for="invitation in invitations" :key="invitation.id" class="mem__row">
            <UserAvatar :user="invitation.invitee" :size="30" />
            <span class="mem__who">
              <strong>{{ invitation.invitee?.displayName }}</strong>
              <span class="tiny muted">
                {{ ROLE_META[invitation.role]?.label }}로 초대 · {{ timeAgo(invitation.createdAt) }}
              </span>
            </span>
            <span class="badge badge--warn">대기 중</span>
            <button class="btn btn--sm" type="button" :disabled="busy" @click="cancelInvite(invitation)">
              취소
            </button>
          </li>
        </ul>
      </section>

      <!-- 참여자 -->
      <section class="mem__block">
        <h3 class="mem__title">
          참여자 <span class="badge">{{ members.length }}</span>
        </h3>

        <p v-if="loading" class="mem__state tiny muted">불러오는 중…</p>

        <ul v-else class="mem__list">
          <li v-for="member in members" :key="member.userId" class="mem__row">
            <UserAvatar :user="member" :size="30" />
            <span class="mem__who">
              <strong>
                {{ member.displayName }}
                <span v-if="member.userId === myUserId" class="badge badge--ok">나</span>
              </strong>
              <span class="tiny muted">
                {{ member.username }} · {{ timeAgo(member.joinedAt) }} 참여
                <template v-if="member.assignedCount"> · 담당 {{ member.assignedCount }}장</template>
              </span>
            </span>

            <select
              v-if="amOwner"
              class="control mem__role"
              :value="member.role"
              :disabled="busy"
              :aria-label="`${member.displayName} 역할`"
              @change="changeRole(member, $event.target.value)"
            >
              <option v-for="role in ROLES" :key="role" :value="role">
                {{ ROLE_META[role].label }}
              </option>
            </select>
            <span v-else class="badge" :class="`badge--${ROLE_META[member.role]?.tone}`">
              {{ ROLE_META[member.role]?.label }}
            </span>

            <button
              v-if="amOwner || member.userId === myUserId"
              class="btn btn--sm btn--danger"
              type="button"
              :disabled="busy"
              @click="remove(member)"
            >
              {{ member.userId === myUserId ? '나가기' : '내보내기' }}
            </button>
          </li>
        </ul>
      </section>

      <p v-if="!amOwner" class="mem__note tiny muted">
        참여자를 초대하거나 역할을 바꾸는 것은 보드 관리자만 할 수 있습니다.
      </p>
    </div>

    <template #footer>
      <button class="btn" type="button" @click="emit('close')">닫기</button>
    </template>
  </BaseModal>
</template>

<style scoped>
.mem {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.mem__invite {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line);
}

.mem__searchbar {
  display: flex;
  gap: 8px;
}

.mem__searchbar .control:first-child {
  flex: 1;
}

.mem__role {
  flex: none;
  width: 118px;
}

.mem__block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mem__title {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-2);
}

.mem__state {
  margin: 0;
  padding: 10px 2px;
}

.mem__list,
.mem__results {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.mem__results {
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 4px;
  background: var(--surface-2);
}

.mem__row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 8px;
  border-radius: var(--radius-sm);
}

.mem__row:hover {
  background: var(--surface-hover);
}

.mem__who {
  display: flex;
  flex-direction: column;
  gap: 1px;
  flex: 1;
  min-width: 0;
}

.mem__who strong {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mem__note {
  margin: 0;
}
</style>
