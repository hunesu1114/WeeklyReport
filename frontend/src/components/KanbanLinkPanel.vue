<script setup>
import { computed, ref, watch } from 'vue'
import { kanbanApi } from '@/api/client'
import { PRIORITY_META, dueLabel, dueTone } from '@/utils/kanban'

const props = defineProps({
  /** 보고서의 금주 기간 */
  from: { type: String, default: '' },
  to: { type: String, default: '' },
  /** 이미 보고서에 들어간 업무명들. 중복 추가를 눈으로 알 수 있게 */
  usedTaskNames: { type: Array, default: () => [] },
})
const emit = defineEmits(['add'])

/** 카드 상태를 보고서의 말투로 바꾼다. */
const STATUS_TAG = {
  BACKLOG: '예정',
  TODO: '예정',
  ING: '진행',
  DONE: '완료',
}

const cards = ref([])
const loading = ref(false)
const error = ref('')
const collapsed = ref(false)

/** 프로젝트별로 묶는다. 보고서의 '업무명' 이 프로젝트 단위이기 때문이다. */
const groups = computed(() => {
  const byProject = new Map()
  for (const card of cards.value) {
    if (!byProject.has(card.projectId)) {
      byProject.set(card.projectId, {
        projectId: card.projectId,
        projectName: card.projectName,
        projectColor: card.projectColor,
        cards: [],
      })
    }
    byProject.get(card.projectId).cards.push(card)
  }
  return [...byProject.values()]
})

watch(() => [props.from, props.to], load, { immediate: true })

async function load() {
  if (!props.from || !props.to) {
    cards.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    cards.value = await kanbanApi.startedBetween(props.from, props.to)
  } catch (e) {
    cards.value = []
    error.value = e.message
  } finally {
    loading.value = false
  }
}

/** 카드 한 장을 업무상세 줄로. */
function lineOf(card) {
  const head = `[${STATUS_TAG[card.status] ?? '진행'}] ${card.title}`
  if (!card.content) return [head]
  // 내용의 각 줄을 하위 항목으로 만든다. 이미 불릿이면 그대로 둔다.
  const body = card.content
    .split('\n')
    .map((line) => line.trimEnd())
    .filter((line) => line.trim())
    .map((line) => (/^\s*[-*>]/.test(line) ? `  ${line.trim()}` : `  - ${line.trim()}`))
  return [head, ...body]
}

function addCard(group, card) {
  emit('add', { taskName: group.projectName, lines: lineOf(card) })
}

function addGroup(group) {
  const lines = group.cards.flatMap((card) => lineOf(card))
  emit('add', { taskName: group.projectName, lines })
}

function isUsed(name) {
  return props.usedTaskNames.some((used) => (used || '').trim() === name.trim())
}
</script>

<template>
  <section class="card klink">
    <div class="card__head">
      <div>
        <h2 class="card__title">
          칸반 연동
          <span v-if="cards.length" class="badge badge--ok">{{ cards.length }}</span>
        </h2>
        <p class="tiny muted klink__range">
          금주 기간에 시작한 카드
          <template v-if="from && to"> · {{ from }} ~ {{ to }}</template>
        </p>
      </div>
      <button class="btn btn--ghost btn--sm" type="button" @click="collapsed = !collapsed">
        {{ collapsed ? '펼치기' : '접기' }}
      </button>
    </div>

    <div v-if="!collapsed" class="klink__body">
      <p v-if="loading" class="klink__state muted tiny">불러오는 중…</p>
      <p v-else-if="error" class="klink__state klink__state--error tiny">{{ error }}</p>
      <p v-else-if="!cards.length" class="klink__state muted tiny">
        이 기간에 시작한 칸반 카드가 없습니다.
        <br />카드의 <strong>생성일</strong>이 금주 기간 안에 있으면 여기에 모입니다.
      </p>

      <div v-for="group in groups" v-else :key="group.projectId" class="group">
        <header class="group__head">
          <span class="group__dot" :style="{ background: group.projectColor || 'var(--text-4)' }"></span>
          <strong class="group__name">{{ group.projectName }}</strong>
          <span class="badge">{{ group.cards.length }}</span>
          <span v-if="isUsed(group.projectName)" class="badge badge--ok" title="이미 금주 항목에 있는 업무명">
            추가됨
          </span>
          <button
            class="btn btn--sm group__all"
            type="button"
            title="이 프로젝트의 카드를 모두 금주 진행 항목으로 넣습니다"
            @click="addGroup(group)"
          >
            전부 넣기
          </button>
        </header>

        <ul class="group__list">
          <li v-for="card in group.cards" :key="card.id" class="row">
            <span class="row__status" :class="`row__status--${card.status.toLowerCase()}`">
              {{ STATUS_TAG[card.status] }}
            </span>
            <span class="row__title" :title="card.content || card.title">{{ card.title }}</span>
            <span class="row__prio tiny muted">{{ PRIORITY_META[card.priority]?.label }}</span>
            <span
              v-if="card.dueDate"
              class="row__due tiny"
              :class="`row__due--${dueTone(card.daysUntilDue)}`"
            >
              {{ dueLabel(card.daysUntilDue) }}
            </span>
            <button
              class="btn btn--sm row__add"
              type="button"
              title="이 카드를 금주 진행 항목에 넣습니다"
              @click="addCard(group, card)"
            >
              넣기
            </button>
          </li>
        </ul>
      </div>
    </div>
  </section>
</template>

<style scoped>
.klink__range {
  margin: 2px 0 0;
}

.klink__body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  max-height: 320px;
  overflow-y: auto;
}

.klink__state {
  margin: 0;
  padding: 18px 6px;
  text-align: center;
  line-height: 1.7;
}

.klink__state--error {
  color: var(--danger);
}

.group {
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--surface-2);
}

.group__head {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--line);
}

.group__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: none;
}

.group__name {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.group__all {
  margin-left: auto;
  flex: none;
}

.group__list {
  margin: 0;
  padding: 4px;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 6px;
  border-radius: var(--radius-xs);
}

.row:hover {
  background: var(--surface-hover);
}

.row__status {
  flex: none;
  min-width: 34px;
  padding: 1px 6px;
  border-radius: var(--radius-xs);
  background: var(--bg-subtle);
  color: var(--text-3);
  font-size: 11px;
  font-weight: 700;
  text-align: center;
}

.row__status--ing {
  background: var(--violet-soft);
  color: var(--violet);
}

.row__status--done {
  background: var(--brand-soft);
  color: var(--brand-strong);
}

.row__title {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row__prio,
.row__due {
  flex: none;
}

.row__due {
  font-weight: 700;
}

.row__due--warn {
  color: var(--warn);
}
.row__due--danger {
  color: var(--danger);
}
.row__due--neutral {
  color: var(--text-3);
}

.row__add {
  flex: none;
}
</style>
