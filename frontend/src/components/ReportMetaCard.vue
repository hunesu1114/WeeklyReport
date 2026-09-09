<script setup>
import { computed, watch } from 'vue'
import { daysBetween, weekDefaults, weekdayOf } from '@/utils/report'

const props = defineProps({
  form: { type: Object, required: true },
  templates: { type: Array, default: () => [] },
  authors: { type: Array, default: () => [] },
})

const RANGE_KEYS = ['thisWeekStart', 'thisWeekEnd', 'nextWeekStart', 'nextWeekEnd']

function matchesDefaultsFor(reportDate) {
  const defaults = weekDefaults(reportDate)
  return RANGE_KEYS.every((key) => props.form[key] === defaults[key])
}

const rangeMatchesDefault = computed(() => matchesDefaultsFor(props.form.reportDate))

const thisWeekDays = computed(() => daysBetween(props.form.thisWeekStart, props.form.thisWeekEnd))
const nextWeekDays = computed(() => daysBetween(props.form.nextWeekStart, props.form.nextWeekEnd))

/**
 * 보고일이 바뀌면 기간을 따라 옮긴다. 단 지금 기간이 "이전 보고일의 기본값"과
 * 똑같을 때만이다. 사용자가 직접 조정해둔 기간(공휴일 주 등)이나 서버에서 불러온
 * 기간은 그대로 둔다.
 */
watch(
  () => props.form.reportDate,
  (next, previous) => {
    if (!next || !previous || next === previous) return
    if (matchesDefaultsFor(previous)) {
      Object.assign(props.form, weekDefaults(next))
    }
  },
)

function applyDefaults() {
  Object.assign(props.form, weekDefaults(props.form.reportDate))
}
</script>

<template>
  <section class="card">
    <div class="card__head">
      <h2 class="card__title">기본 정보</h2>
      <span class="tiny muted">보고일을 고르면 기간이 자동으로 채워집니다</span>
    </div>

    <div class="card__body meta">
      <div class="field meta__author">
        <label for="author">작성자</label>
        <input
          id="author"
          v-model="form.authorName"
          class="control"
          type="text"
          list="author-options"
          placeholder="홍길동"
          autocomplete="off"
        />
        <datalist id="author-options">
          <option v-for="name in authors" :key="name" :value="name" />
        </datalist>
      </div>

      <div class="field meta__date">
        <label for="report-date">보고일</label>
        <input id="report-date" v-model="form.reportDate" class="control" v-date-picker type="date" />
        <span class="tiny muted">{{ weekdayOf(form.reportDate) }}요일</span>
      </div>

      <div class="field meta__base">
        <label for="base-hours">기준 근무시간</label>
        <input
          id="base-hours"
          v-model="form.baseHours"
          class="control"
          type="number"
          min="0"
          step="1"
        />
        <span class="tiny muted">제외시간 = 기준 − 합계</span>
      </div>

      <div class="field meta__template">
        <label for="template">다운로드 양식</label>
        <select id="template" v-model="form.templateKey" class="control">
          <option v-for="template in templates" :key="template.templateKey" :value="template.templateKey">
            {{ template.name }}
          </option>
        </select>
        <span class="tiny muted">{{
          templates.find((t) => t.templateKey === form.templateKey)?.description || ''
        }}</span>
      </div>

      <div class="meta__ranges">
        <div class="range">
          <div class="range__label">
            금주 진행 내용
            <span class="badge">{{ thisWeekDays }}일</span>
          </div>
          <div class="range__inputs">
            <input v-model="form.thisWeekStart" class="control" v-date-picker type="date" />
            <span class="range__tilde">~</span>
            <input v-model="form.thisWeekEnd" class="control" v-date-picker type="date" />
          </div>
        </div>

        <div class="range">
          <div class="range__label">
            차주 진행 예정
            <span class="badge">{{ nextWeekDays }}일</span>
          </div>
          <div class="range__inputs">
            <input v-model="form.nextWeekStart" class="control" v-date-picker type="date" />
            <span class="range__tilde">~</span>
            <input v-model="form.nextWeekEnd" class="control" v-date-picker type="date" />
          </div>
        </div>

        <button
          class="btn btn--sm meta__reset"
          type="button"
          :disabled="rangeMatchesDefault"
          title="보고일 기준 기본값(금주 = 보고일-7 ~ 보고일-1)으로 되돌립니다"
          @click="applyDefaults"
        >
          ↺ 기간 자동 계산
        </button>
      </div>

      <details class="meta__advanced">
        <summary>제목 직접 지정</summary>
        <div class="field">
          <label for="title-override">제목</label>
          <input
            id="title-override"
            v-model="form.titleOverride"
            class="control"
            type="text"
            :placeholder="`주간보고 - ${form.authorName || '작성자'}`"
          />
          <span class="tiny muted">비워두면 &ldquo;주간보고 - 작성자&rdquo; 로 들어갑니다.</span>
        </div>
      </details>
    </div>
  </section>
</template>

<style scoped>
.meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 14px;
}

.meta__ranges {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 16px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--bg-subtle);
}

.range {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.range__label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-2);
}

.range__inputs {
  display: flex;
  align-items: center;
  gap: 6px;
}

.range__inputs .control {
  width: 148px;
}

.range__tilde {
  color: var(--text-4);
}

.meta__reset {
  margin-left: auto;
}

.meta__advanced {
  grid-column: 1 / -1;
}

.meta__advanced summary {
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-3);
}

.meta__advanced .field {
  margin-top: 10px;
  max-width: 420px;
}
</style>
