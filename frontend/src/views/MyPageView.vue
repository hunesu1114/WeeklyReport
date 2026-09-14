<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import UserAvatar from '@/components/UserAvatar.vue'
import { authApi, kanbanApi, profileApi, teamApi } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useKanbanStore } from '@/stores/kanban'
import { useToast } from '@/composables/useToast'
import { resizeAvatar } from '@/utils/image'
import { ROLE_META, timeAgo } from '@/utils/team'

const auth = useAuthStore()
const kanban = useKanbanStore()
const toast = useToast()
const router = useRouter()

const me = computed(() => auth.user)

// ── 닉네임 ──────────────────────────────────────────────
const displayName = ref(auth.user?.displayName ?? '')
const savingName = ref(false)

const nameChanged = computed(
  () => displayName.value.trim() && displayName.value.trim() !== me.value?.displayName,
)

async function saveName() {
  if (!nameChanged.value) return
  savingName.value = true
  try {
    const updated = await profileApi.update({ displayName: displayName.value.trim() })
    auth.applyUser(updated)
    toast.success('닉네임을 바꿨습니다.')
  } catch (error) {
    toast.error(error.message)
  } finally {
    savingName.value = false
  }
}

// ── 프로필 사진 ─────────────────────────────────────────
const fileInput = ref(null)
const uploading = ref(false)

async function pickAvatar(event) {
  const file = event.target.files?.[0]
  event.target.value = '' // 같은 파일을 다시 골라도 change 가 오도록
  if (!file) return

  if (!file.type.startsWith('image/')) {
    toast.error('이미지 파일만 올릴 수 있습니다.')
    return
  }

  uploading.value = true
  try {
    // 원본 그대로 올리면 사진 한 장이 수 MB 다. 브라우저에서 미리 줄여 보낸다.
    const resized = await resizeAvatar(file)
    const updated = await profileApi.uploadAvatar(resized)
    auth.applyUser(updated)
    toast.success('프로필 사진을 바꿨습니다.')
  } catch (error) {
    toast.error(error.message)
  } finally {
    uploading.value = false
  }
}

async function removeAvatar() {
  if (!window.confirm('프로필 사진을 지울까요?')) return
  uploading.value = true
  try {
    auth.applyUser(await profileApi.removeAvatar())
    toast.success('프로필 사진을 지웠습니다.')
  } catch (error) {
    toast.error(error.message)
  } finally {
    uploading.value = false
  }
}

// ── 비밀번호 ────────────────────────────────────────────
const pw = ref({ current: '', next: '', confirm: '' })
const changingPw = ref(false)

const pwMismatch = computed(
  () => pw.value.confirm.length > 0 && pw.value.next !== pw.value.confirm,
)
const pwReady = computed(
  () =>
    pw.value.current.length > 0 &&
    pw.value.next.length >= 8 &&
    pw.value.next === pw.value.confirm,
)

async function changePassword() {
  if (!pwReady.value) return
  changingPw.value = true
  try {
    await authApi.changePassword({
      currentPassword: pw.value.current,
      newPassword: pw.value.next,
    })
    pw.value = { current: '', next: '', confirm: '' }
    toast.success('비밀번호를 바꿨습니다. 다음 로그인부터 새 비밀번호를 씁니다.')
  } catch (error) {
    toast.error(error.message)
  } finally {
    changingPw.value = false
  }
}

// ── 소속 프로젝트 ───────────────────────────────────────
const projects = ref([])
const loadingProjects = ref(true)
const leavingId = ref(null)

onMounted(load)

async function load() {
  loadingProjects.value = true
  try {
    projects.value = await kanbanApi.projects(false)
  } catch (error) {
    toast.error(error.message)
  } finally {
    loadingProjects.value = false
  }
}

async function leave(project) {
  const message =
    `'${project.name}' 보드에서 나갈까요?\n` +
    '다시 들어오려면 초대를 받아야 합니다. 보드와 카드는 남습니다.'
  if (!window.confirm(message)) return

  leavingId.value = project.id
  try {
    await teamApi.removeMember(project.id, me.value.id)
    toast.success(`'${project.name}' 보드에서 나왔습니다.`)
    await Promise.all([load(), kanban.refresh()])
  } catch (error) {
    toast.error(error.message)
  } finally {
    leavingId.value = null
  }
}

function openBoard(project) {
  router.push({ name: 'kanban-board', params: { projectId: project.id } })
}
</script>

<template>
  <div class="my">
    <header class="my__head">
      <h1>마이페이지</h1>
      <p class="tiny muted">계정 정보와 참여 중인 보드를 관리합니다.</p>
    </header>

    <!-- 프로필 -->
    <section class="card">
      <div class="card__head">
        <h2 class="card__title">프로필</h2>
      </div>

      <div class="card__body my__profile">
        <div class="my__avatar">
          <UserAvatar :user="me" :size="96" />
          <div class="my__avatar-actions">
            <input
              ref="fileInput"
              class="sr-only"
              type="file"
              accept="image/png,image/jpeg,image/webp,image/gif"
              @change="pickAvatar"
            />
            <button
              class="btn btn--sm"
              type="button"
              :disabled="uploading"
              @click="fileInput?.click()"
            >
              {{ uploading ? '올리는 중…' : '사진 바꾸기' }}
            </button>
            <button
              v-if="me?.hasAvatar"
              class="btn btn--sm btn--danger"
              type="button"
              :disabled="uploading"
              @click="removeAvatar"
            >
              지우기
            </button>
          </div>
          <p class="tiny muted my__avatar-hint">
            정사각형 가운데만 남기고 256px 로 줄여 올립니다.
          </p>
        </div>

        <div class="my__fields">
          <div class="field">
            <label for="my-username">아이디</label>
            <input id="my-username" class="control" type="text" :value="me?.username" disabled />
            <span class="tiny muted">
              아이디는 바꿀 수 없습니다.
              <span v-if="auth.isAdmin" class="badge badge--ok">관리자</span>
              <template v-if="me?.createdAt"> · {{ timeAgo(me.createdAt) }} 가입</template>
            </span>
          </div>

          <div class="field">
            <label for="my-name">닉네임</label>
            <div class="my__inline">
              <input
                id="my-name"
                v-model="displayName"
                class="control"
                type="text"
                maxlength="50"
                placeholder="화면에 보일 이름"
                @keydown.enter.prevent="saveName"
              />
              <button
                class="btn btn--primary"
                type="button"
                :disabled="!nameChanged || savingName"
                @click="saveName"
              >
                저장
              </button>
            </div>
            <span class="tiny muted">카드 담당자, 참여자 목록, 활동 기록에 이 이름이 나옵니다.</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 비밀번호 -->
    <section class="card">
      <div class="card__head">
        <h2 class="card__title">비밀번호</h2>
      </div>

      <form class="card__body my__pw" @submit.prevent="changePassword">
        <div class="field">
          <label for="pw-current">현재 비밀번호</label>
          <input
            id="pw-current"
            v-model="pw.current"
            class="control"
            type="password"
            autocomplete="current-password"
          />
        </div>

        <div class="field">
          <label for="pw-next">새 비밀번호</label>
          <input
            id="pw-next"
            v-model="pw.next"
            class="control"
            type="password"
            autocomplete="new-password"
            minlength="8"
          />
          <span class="tiny muted">8자 이상</span>
        </div>

        <div class="field">
          <label for="pw-confirm">새 비밀번호 확인</label>
          <input
            id="pw-confirm"
            v-model="pw.confirm"
            class="control"
            :class="{ 'control--bad': pwMismatch }"
            type="password"
            autocomplete="new-password"
          />
          <span v-if="pwMismatch" class="tiny my__bad">비밀번호가 서로 다릅니다.</span>
        </div>

        <div class="my__pw-actions">
          <button class="btn btn--primary" type="submit" :disabled="!pwReady || changingPw">
            <span v-if="changingPw" class="spinner"></span>
            비밀번호 바꾸기
          </button>
        </div>
      </form>
    </section>

    <!-- 소속 프로젝트 -->
    <section class="card">
      <div class="card__head">
        <div>
          <h2 class="card__title">
            참여 중인 보드
            <span v-if="projects.length" class="badge">{{ projects.length }}</span>
          </h2>
          <p class="tiny muted my__hint">나가면 그 보드는 목록에서 사라집니다.</p>
        </div>
      </div>

      <div class="card__body">
        <p v-if="loadingProjects" class="my__state muted tiny">불러오는 중…</p>
        <p v-else-if="!projects.length" class="my__state muted tiny">
          참여 중인 보드가 없습니다. 칸반 화면에서 만들거나 초대를 받으세요.
        </p>

        <ul v-else class="my__projects">
          <li v-for="project in projects" :key="project.id" class="my__project">
            <span class="my__dot" :style="{ background: project.color || 'var(--text-4)' }"></span>
            <button class="my__pname" type="button" @click="openBoard(project)">
              <strong>{{ project.name }}</strong>
              <span class="tiny muted">
                카드 {{ project.cardCount }} · 진행할 일 {{ project.openCount }}
                <template v-if="!project.active"> · 보관됨</template>
              </span>
            </button>

            <span class="badge" :class="`badge--${ROLE_META[project.myRole]?.tone}`">
              {{ ROLE_META[project.myRole]?.label }}
            </span>

            <button
              class="btn btn--sm btn--danger"
              type="button"
              :disabled="leavingId === project.id"
              @click="leave(project)"
            >
              나가기
            </button>
          </li>
        </ul>

        <p class="tiny muted my__note">
          보드의 마지막 관리자는 나갈 수 없습니다. 다른 참여자를 관리자로 지정한 뒤에 나가세요.
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.my {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 860px;
  margin: 0 auto;
}

.my__head h1 {
  margin: 0;
  font-size: 19px;
  letter-spacing: -0.02em;
}

.my__head p {
  margin: 3px 0 0;
}

.my__hint,
.my__note {
  margin: 2px 0 0;
}

.my__note {
  margin-top: 12px;
}

.my__state {
  margin: 0;
  padding: 18px 2px;
  text-align: center;
}

/* ---------- 프로필 ---------- */
.my__profile {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 22px;
  align-items: start;
}

.my__avatar {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 9px;
  width: 160px;
}

.my__avatar-actions {
  display: flex;
  gap: 6px;
}

.my__avatar-hint {
  text-align: center;
  line-height: 1.5;
}

.my__fields {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.my__inline {
  display: flex;
  gap: 8px;
}

.my__inline .control {
  flex: 1;
}

/* ---------- 비밀번호 ---------- */
.my__pw {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
  align-items: start;
}

.my__pw-actions {
  grid-column: 1 / -1;
  display: flex;
  justify-content: flex-end;
}

.my__bad {
  color: var(--danger);
  font-weight: 600;
}

/* ---------- 소속 보드 ---------- */
.my__projects {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.my__project {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: var(--radius-sm);
}

.my__project:hover {
  background: var(--surface-hover);
}

.my__dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  flex: none;
}

.my__pname {
  display: flex;
  flex-direction: column;
  gap: 1px;
  flex: 1;
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
}

.my__pname strong {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.my__pname:hover strong {
  color: var(--brand-strong);
}

@media (max-width: 640px) {
  .my__profile {
    grid-template-columns: 1fr;
    justify-items: center;
  }

  .my__fields {
    width: 100%;
  }
}
</style>
