<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import UserAvatar from '@/components/UserAvatar.vue'
import { authApi, kanbanApi, memoApi, profileApi, teamApi } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useKanbanStore } from '@/stores/kanban'
import { useMemoStore } from '@/stores/memo'
import { useToast } from '@/composables/useToast'
import { resizeAvatar } from '@/utils/image'
import { formatBytes } from '@/utils/memoImage'
import { ROLE_META, timeAgo } from '@/utils/team'

const auth = useAuthStore()
const kanban = useKanbanStore()
const memoStore = useMemoStore()
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

// ── 메모 그림 공간 ──────────────────────────────────────
const usage = ref(null)
const loadingUsage = ref(true)

onMounted(loadUsage)

async function loadUsage() {
  loadingUsage.value = true
  try {
    usage.value = await memoApi.imageUsage()
  } catch (error) {
    toast.error(`그림 사용량을 불러오지 못했습니다. ${error.message}`)
  } finally {
    loadingUsage.value = false
  }
}

/** 쓴 비율(0~1). 한도가 0 이면(설정 실수) 다 쓴 것으로 본다 */
const usedRatio = computed(() => {
  const u = usage.value
  if (!u) return 0
  return u.quotaBytes > 0 ? Math.min(1, u.bytes / u.quotaBytes) : 1
})

/** 막대 색. 80% 를 넘으면 노랑, 95% 를 넘으면 빨강 — 그때부터 큰 사진 몇 장에 막힌다 */
const usageTone = computed(() => (usedRatio.value >= 0.95 ? 'bad' : usedRatio.value >= 0.8 ? 'warn' : 'ok'))

const usedPercent = computed(() => {
  const pct = usedRatio.value * 100
  if (pct > 0 && pct < 1) return '1% 미만'
  return `${Math.round(pct)}%`
})

/** 메모 화면은 마지막에 연 메모를 연다. 그 자리에 이 메모를 적어 두고 넘어간다 */
function openMemo(item) {
  memoStore.rememberMemo(item.id)
  router.push({ name: 'memos' })
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

    <!-- 메모 그림 공간 -->
    <section class="card">
      <div class="card__head">
        <div>
          <h2 class="card__title">메모 그림</h2>
          <p class="tiny muted my__hint">메모에 붙여넣은 그림이 쓰는 공간입니다.</p>
        </div>
      </div>

      <div class="card__body">
        <p v-if="loadingUsage && !usage" class="my__state muted tiny">불러오는 중…</p>
        <template v-else-if="usage">
          <div class="space">
            <p class="space__head">
              <strong class="num">{{ formatBytes(usage.bytes) || '0KB' }}</strong>
              <span class="muted">/ {{ formatBytes(usage.quotaBytes) }}</span>
              <span class="space__pct num" :class="`space__pct--${usageTone}`">{{ usedPercent }}</span>
            </p>
            <div
              class="space__bar"
              role="meter"
              aria-label="메모 그림이 쓰는 공간"
              aria-valuemin="0"
              :aria-valuemax="usage.quotaBytes"
              :aria-valuenow="usage.bytes"
              :aria-valuetext="`${formatBytes(usage.bytes) || '0KB'} / ${formatBytes(usage.quotaBytes)}, ${usedPercent}`"
            >
              <span
                class="space__fill"
                :class="`space__fill--${usageTone}`"
                :style="{ width: `${Math.max(usedRatio * 100, usage.bytes ? 0.6 : 0)}%` }"
              ></span>
            </div>
            <p class="tiny muted space__facts">
              그림 <span class="num">{{ usage.count }}</span>장 ·
              한 장 {{ formatBytes(usage.maxBytes) }}, 메모 하나에 {{ usage.maxPerMemo }}장까지
            </p>
            <p v-if="usage.orphanCount" class="tiny space__soon">
              본문에서 뺀 그림 <span class="num">{{ usage.orphanCount }}</span>장({{ formatBytes(usage.orphanBytes) }})은
              하루 안에 지워지고 그만큼 공간이 돌아옵니다.
            </p>
          </div>

          <template v-if="usage.memos.length">
            <p class="eyebrow space__label">그림을 많이 쓰는 메모</p>
            <ul class="space__memos">
              <li v-for="item in usage.memos" :key="item.id" class="space__memo">
                <button
                  v-if="!item.trashed"
                  class="space__title"
                  type="button"
                  :title="`'${item.title}' 메모 열기`"
                  @click="openMemo(item)"
                >
                  {{ item.title }}
                </button>
                <span v-else class="space__title space__title--off">
                  {{ item.title }}
                  <span class="badge">휴지통</span>
                </span>
                <span class="tiny muted num space__size">
                  {{ item.images }}장 · {{ formatBytes(item.bytes) }}
                </span>
              </li>
            </ul>
            <p v-if="usage.memos.some((m) => m.trashed)" class="tiny muted my__note">
              휴지통에 든 메모의 그림도 공간을 씁니다. 휴지통을 비우면 하루 뒤 공간이 돌아옵니다.
            </p>
          </template>
          <p v-else-if="!usage.count" class="tiny muted my__note">
            아직 메모에 넣은 그림이 없습니다. 메모에서 Ctrl+V 로 붙여넣거나 끌어다 놓으면 됩니다.
          </p>
        </template>
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

/* ---------- 메모 그림 공간 ---------- */
.space {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.space__head {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin: 0;
  font-size: 13px;
}

.space__head strong {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.space__pct {
  margin-left: auto;
  font-size: 12px;
  font-weight: 600;
}

.space__pct--ok {
  color: var(--text-3);
}

.space__pct--warn {
  color: var(--warn);
}

.space__pct--bad {
  color: var(--danger);
}

.space__bar {
  height: 8px;
  overflow: hidden;
  border-radius: 4px;
  background: var(--surface-sel);
}

.space__fill {
  display: block;
  height: 100%;
  border-radius: 4px;
  background: var(--brand);
  transition: width 0.3s ease;
}

.space__fill--warn {
  background: var(--warn);
}

.space__fill--bad {
  background: var(--danger);
}

.space__facts,
.space__soon {
  margin: 0;
}

.space__soon {
  color: var(--text-2);
}

.space__label {
  margin: 18px 0 6px;
}

.space__memos {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
}

.space__memo {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 34px;
  padding: 0 8px;
  border-radius: var(--radius-sm);
}

.space__memo:hover {
  background: var(--surface-hover);
}

.space__title {
  display: inline-flex;
  flex: 1;
  align-items: center;
  gap: 6px;
  min-width: 0;
  padding: 0;
  overflow: hidden;
  border: 0;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-size: 13px;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

button.space__title:hover {
  color: var(--brand-strong);
}

.space__title--off {
  color: var(--text-3);
}

.space__size {
  flex: none;
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
