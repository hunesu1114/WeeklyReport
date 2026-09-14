<script setup>
import { computed, reactive, ref, watch } from 'vue'
import BaseModal from '@/components/BaseModal.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { teamApi } from '@/api/client'
import {
  PRIORITIES,
  PRIORITY_META,
  STATUSES,
  STATUS_META,
  dueLabel,
  dueTone,
  toForm,
} from '@/utils/kanban'
import { activityText, activityTone, timeAgo } from '@/utils/team'

const props = defineProps({
  card: { type: Object, required: true },
  projects: { type: Array, default: () => [] },
  /** 담당자 후보. 이 보드의 참여자만 들어온다. */
  members: { type: Array, default: () => [] },
  saving: { type: Boolean, default: false },
  /** 읽기 전용으로 참여 중이면 입력을 잠근다. */
  readOnly: { type: Boolean, default: false },
  /** 저장이 409 로 거절됐을 때 서버가 함께 준 '지금 값'. */
  conflict: { type: Object, default: null },
})
const emit = defineEmits(['save', 'remove', 'close', 'resolve-conflict'])

const form = reactive({ ...props.card })
const error = ref('')

const logs = ref([])
const logsOpen = ref(false)
const logsLoading = ref(false)

watch(
  () => props.card,
  (next) => {
    Object.assign(form, next)
    logs.value = []
    logsOpen.value = false
  },
)

const isNew = computed(() => !form.id)

/** 완료일이 시작일보다 앞서면 저장 전에 잡아준다. */
const dateWarning = computed(() => {
  if (!form.startDate || !form.dueDate) return ''
  return form.dueDate < form.startDate ? '완료일이 생성일보다 빠릅니다.' : ''
})

const remaining = computed(() => {
  if (!form.dueDate) return null
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const [y, m, d] = form.dueDate.split('-').map(Number)
  return Math.round((new Date(y, m - 1, d) - today) / 86400000)
})

const assignee = computed(() => props.members.find((m) => m.userId === form.assigneeId) ?? null)

function submit() {
  if (props.readOnly) return
  if (!form.title.trim()) {
    error.value = '제목은 반드시 입력해야 합니다.'
    return
  }
  error.value = ''
  emit('save', { ...form })
}

/** 상대가 고친 내용을 그대로 가져온다. 내가 친 것은 버려진다. */
function takeTheirs() {
  Object.assign(form, toForm(props.conflict))
  emit('resolve-conflict')
}

/**
 * 내가 친 내용으로 덮어쓴다. 상대의 버전을 달고 다시 보내므로 이번에는 통과한다.
 * 무엇을 덮어쓰는지 위 비교에서 이미 보여줬다.
 */
function keepMine() {
  form.version = props.conflict.version
  emit('resolve-conflict')
  submit()
}

async function toggleLogs() {
  logsOpen.value = !logsOpen.value
  if (!logsOpen.value || logs.value.length || !form.id) return
  logsLoading.value = true
  try {
    const page = await teamApi.cardActivities(form.projectId, form.id, 0, 20)
    logs.value = page.content ?? []
  } catch {
    logs.value = []
  } finally {
    logsLoading.value = false
  }
}
</script>

<template>
  <BaseModal
    :title="isNew ? '새 카드' : readOnly ? '카드 보기' : '카드 수정'"
    :subtitle="isNew ? '보드에 올릴 일을 적습니다' : `#${form.id}`"
    width="620px"
    @close="emit('close')"
  >
    <!-- 저장이 거절됐다. 무엇이 다른지 보여주고 고르게 한다 -->
    <div v-if="conflict" class="clash">
      <strong class="clash__title">다른 사람이 먼저 수정했습니다</strong>
      <p class="tiny clash__desc">
        내가 이 카드를 연 사이에 서버 쪽 내용이 바뀌었습니다. 어느 쪽을 남길지 고르세요.
      </p>
      <div class="clash__cols">
        <div class="clash__col">
          <span class="tiny muted">서버의 현재 내용</span>
          <strong class="clash__head">{{ conflict.title }}</strong>
          <p class="tiny clash__body">{{ conflict.content || '(내용 없음)' }}</p>
        </div>
        <div class="clash__col">
          <span class="tiny muted">내가 쓴 내용</span>
          <strong class="clash__head">{{ form.title }}</strong>
          <p class="tiny clash__body">{{ form.content || '(내용 없음)' }}</p>
        </div>
      </div>
      <div class="clash__actions">
        <button class="btn btn--sm" type="button" @click="takeTheirs">서버 내용 가져오기</button>
        <button class="btn btn--sm btn--danger" type="button" @click="keepMine">
          내 내용으로 덮어쓰기
        </button>
      </div>
    </div>

    <form class="cardform" @submit.prevent="submit">
      <div class="field">
        <label for="card-title">제목</label>
        <input
          id="card-title"
          v-model="form.title"
          class="control"
          type="text"
          placeholder="무엇을 하는 일인가요?"
          maxlength="200"
          :disabled="readOnly"
        />
      </div>

      <div class="cardform__row">
        <div class="field">
          <label for="card-project">프로젝트</label>
          <select id="card-project" v-model="form.projectId" class="control" :disabled="readOnly">
            <option v-for="project in projects" :key="project.id" :value="project.id">
              {{ project.name }}
            </option>
          </select>
        </div>

        <div class="field">
          <label for="card-status">상태</label>
          <select id="card-status" v-model="form.status" class="control" :disabled="readOnly">
            <option v-for="status in STATUSES" :key="status" :value="status">
              {{ STATUS_META[status].label }} · {{ STATUS_META[status].hint }}
            </option>
          </select>
        </div>
      </div>

      <div class="field">
        <label for="card-assignee">담당자</label>
        <div class="cardform__assignee">
          <UserAvatar :user="assignee" :size="30" />
          <select id="card-assignee" v-model="form.assigneeId" class="control" :disabled="readOnly">
            <option :value="null">담당 없음</option>
            <option v-for="member in members" :key="member.userId" :value="member.userId">
              {{ member.displayName }}
            </option>
          </select>
        </div>
        <span class="tiny muted">
          담당자에게는 완료일 임박 알림이 갑니다. 이 보드의 참여자만 지정할 수 있습니다.
        </span>
      </div>

      <div class="field">
        <span class="field-label">중요도</span>
        <div class="chips" role="radiogroup" aria-label="중요도">
          <button
            v-for="level in PRIORITIES"
            :key="level"
            class="chip"
            :class="{ [`chip--${PRIORITY_META[level].tone}`]: form.priority === level }"
            type="button"
            role="radio"
            :aria-checked="form.priority === level"
            :disabled="readOnly"
            @click="form.priority = level"
          >
            {{ PRIORITY_META[level].label }}
          </button>
        </div>
      </div>

      <div class="cardform__row">
        <div class="field">
          <label for="card-start">생성일 <span class="muted">(시작일)</span></label>
          <input
            id="card-start"
            v-model="form.startDate"
            class="control"
            v-date-picker
            type="date"
            :disabled="readOnly"
          />
          <span class="tiny muted">주간보고의 금주 기간에 이 날짜가 들어가면 목록에 잡힙니다.</span>
        </div>

        <div class="field">
          <label for="card-due">완료일</label>
          <input
            id="card-due"
            v-model="form.dueDate"
            class="control"
            v-date-picker
            type="date"
            :disabled="readOnly"
          />
          <span
            v-if="remaining !== null"
            class="tiny"
            :class="`due--${dueTone(remaining)}`"
          >
            {{ dueLabel(remaining) }}<template v-if="remaining <= 3 && remaining >= 0"> · 임박 알림 대상</template>
          </span>
          <span v-else class="tiny muted">비워두면 알림을 받지 않습니다.</span>
        </div>
      </div>

      <div class="field">
        <label for="card-content">내용</label>
        <textarea
          id="card-content"
          v-model="form.content"
          class="control"
          rows="5"
          placeholder="세부 내용, 참고 링크, 남은 일 등"
          :disabled="readOnly"
        ></textarea>
      </div>

      <p v-if="dateWarning" class="cardform__msg cardform__msg--warn tiny">{{ dateWarning }}</p>
      <p v-if="error" class="cardform__msg cardform__msg--error tiny">{{ error }}</p>
      <p v-if="readOnly" class="cardform__msg cardform__msg--warn tiny">
        읽기 전용으로 참여 중입니다. 내용을 바꿀 수 없습니다.
      </p>
    </form>

    <!-- 이 카드의 활동 기록 -->
    <section v-if="!isNew" class="clog">
      <button class="clog__toggle" type="button" @click="toggleLogs">
        <span>이 카드의 활동 기록</span>
        <span aria-hidden="true">{{ logsOpen ? '▾' : '▸' }}</span>
      </button>

      <div v-if="logsOpen" class="clog__body">
        <p v-if="logsLoading" class="tiny muted clog__state">불러오는 중…</p>
        <p v-else-if="!logs.length" class="tiny muted clog__state">아직 기록이 없습니다.</p>
        <ul v-else class="clog__list">
          <li v-for="log in logs" :key="log.id" class="clog__row">
            <UserAvatar :user="log.actor" :size="20" />
            <span class="clog__text" :class="`clog__text--${activityTone(log)}`">
              {{ activityText(log) }}
            </span>
            <span class="tiny muted clog__when">{{ timeAgo(log.createdAt) }}</span>
          </li>
        </ul>
      </div>
    </section>

    <template #footer>
      <button
        v-if="!isNew && !readOnly"
        class="btn btn--danger"
        type="button"
        :disabled="saving"
        @click="emit('remove', form)"
      >
        삭제
      </button>
      <span class="cardform__spacer"></span>
      <button class="btn" type="button" :disabled="saving" @click="emit('close')">
        {{ readOnly ? '닫기' : '취소' }}
      </button>
      <button
        v-if="!readOnly"
        class="btn btn--primary"
        type="button"
        :disabled="saving"
        @click="submit"
      >
        <span v-if="saving" class="spinner"></span>
        {{ isNew ? '추가' : '저장' }}
      </button>
    </template>
  </BaseModal>
</template>

<style scoped>
.cardform {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.cardform__row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
}

.cardform__assignee {
  display: flex;
  align-items: center;
  gap: 9px;
}

.cardform__assignee .control {
  flex: 1;
}

.cardform__spacer {
  flex: 1;
}

.cardform__msg {
  margin: 0;
  padding: 7px 10px;
  border-radius: var(--radius-xs);
  font-weight: 600;
}

.cardform__msg--warn {
  background: var(--warn-soft);
  color: var(--warn);
}

.cardform__msg--error {
  background: var(--danger-soft);
  color: var(--danger);
}

/* ---------- 충돌 ---------- */
.clash {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
  padding: 12px;
  border: 1px solid var(--danger-border);
  border-radius: var(--radius-sm);
  background: var(--danger-soft);
}

.clash__title {
  font-size: 13px;
  color: var(--danger);
}

.clash__desc {
  margin: 0;
  color: var(--text-2);
  line-height: 1.6;
}

.clash__cols {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 8px;
}

.clash__col {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 9px 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius-xs);
  background: var(--surface);
}

.clash__head {
  font-size: 12px;
  word-break: break-word;
}

.clash__body {
  margin: 0;
  max-height: 76px;
  overflow: hidden;
  color: var(--text-3);
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

.clash__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* ---------- 카드 활동 기록 ---------- */
.clog {
  margin-top: 16px;
  border-top: 1px solid var(--line);
  padding-top: 12px;
}

.clog__toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  padding: 6px 2px;
  border: 0;
  background: transparent;
  color: var(--text-2);
  font-size: 12px;
  font-weight: 700;
}

.clog__toggle:hover {
  color: var(--text);
}

.clog__body {
  max-height: 190px;
  overflow-y: auto;
  padding-top: 4px;
}

.clog__state {
  margin: 0;
  padding: 12px 2px;
  text-align: center;
}

.clog__list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.clog__row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 4px;
  border-radius: var(--radius-xs);
}

.clog__row:hover {
  background: var(--surface-hover);
}

.clog__text {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.5;
  word-break: break-word;
}

.clog__text--ok {
  color: var(--brand-strong);
}
.clog__text--danger {
  color: var(--danger);
}
.clog__text--info,
.clog__text--neutral {
  color: var(--text-2);
}

.clog__when {
  flex: none;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.chip {
  padding: 6px 13px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: var(--surface);
  color: var(--text-3);
  font-size: 12px;
  font-weight: 700;
  transition: background 0.12s, color 0.12s, border-color 0.12s;
}

.chip:hover {
  border-color: var(--text-4);
  color: var(--text);
}

.chip:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.chip--neutral {
  background: var(--bg-subtle);
  border-color: var(--line-strong);
  color: var(--text);
}
.chip--info {
  background: var(--info-soft);
  border-color: var(--info-border);
  color: var(--info);
}
.chip--warn {
  background: var(--warn-soft);
  border-color: var(--warn-border);
  color: var(--warn);
}
.chip--danger {
  background: var(--danger-soft);
  border-color: var(--danger-border);
  color: var(--danger);
}

.due--neutral {
  color: var(--text-3);
}
.due--warn {
  color: var(--warn);
  font-weight: 700;
}
.due--danger {
  color: var(--danger);
  font-weight: 700;
}
</style>
