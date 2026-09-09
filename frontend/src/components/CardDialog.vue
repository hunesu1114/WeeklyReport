<script setup>
import { computed, reactive, ref, watch } from 'vue'
import BaseModal from '@/components/BaseModal.vue'
import { PRIORITIES, PRIORITY_META, STATUSES, STATUS_META, dueLabel, dueTone } from '@/utils/kanban'

const props = defineProps({
  card: { type: Object, required: true },
  projects: { type: Array, default: () => [] },
  saving: { type: Boolean, default: false },
})
const emit = defineEmits(['save', 'remove', 'close'])

const form = reactive({ ...props.card })
const error = ref('')

watch(
  () => props.card,
  (next) => Object.assign(form, next),
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

function submit() {
  if (!form.title.trim()) {
    error.value = '제목은 반드시 입력해야 합니다.'
    return
  }
  error.value = ''
  emit('save', { ...form })
}
</script>

<template>
  <BaseModal
    :title="isNew ? '새 카드' : '카드 수정'"
    :subtitle="isNew ? '보드에 올릴 일을 적습니다' : `#${form.id}`"
    width="620px"
    @close="emit('close')"
  >
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
        />
      </div>

      <div class="cardform__row">
        <div class="field">
          <label for="card-project">프로젝트</label>
          <select id="card-project" v-model="form.projectId" class="control">
            <option v-for="project in projects" :key="project.id" :value="project.id">
              {{ project.name }}
            </option>
          </select>
        </div>

        <div class="field">
          <label for="card-status">상태</label>
          <select id="card-status" v-model="form.status" class="control">
            <option v-for="status in STATUSES" :key="status" :value="status">
              {{ STATUS_META[status].label }} · {{ STATUS_META[status].hint }}
            </option>
          </select>
        </div>
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
            @click="form.priority = level"
          >
            {{ PRIORITY_META[level].label }}
          </button>
        </div>
      </div>

      <div class="cardform__row">
        <div class="field">
          <label for="card-start">생성일 <span class="muted">(시작일)</span></label>
          <input id="card-start" v-model="form.startDate" class="control" type="date" />
          <span class="tiny muted">주간보고의 금주 기간에 이 날짜가 들어가면 목록에 잡힙니다.</span>
        </div>

        <div class="field">
          <label for="card-due">완료일</label>
          <input id="card-due" v-model="form.dueDate" class="control" type="date" />
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
        ></textarea>
      </div>

      <p v-if="dateWarning" class="cardform__msg cardform__msg--warn tiny">{{ dateWarning }}</p>
      <p v-if="error" class="cardform__msg cardform__msg--error tiny">{{ error }}</p>
    </form>

    <template #footer>
      <button
        v-if="!isNew"
        class="btn btn--danger"
        type="button"
        :disabled="saving"
        @click="emit('remove', form)"
      >
        삭제
      </button>
      <span class="cardform__spacer"></span>
      <button class="btn" type="button" :disabled="saving" @click="emit('close')">취소</button>
      <button class="btn btn--primary" type="button" :disabled="saving" @click="submit">
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
