<script setup>
import { computed } from 'vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { PRIORITY_META, dueLabel, dueShort, dueTone, shortDate } from '@/utils/kanban'

const props = defineProps({
  card: { type: Object, required: true },
  dragging: { type: Boolean, default: false },
  /** 여러 프로젝트를 섞어 보여줄 때만 프로젝트 이름을 띄운다 */
  showProject: { type: Boolean, default: false },
  /** 읽기 전용 참여자에게는 끌 수 없게 한다 */
  draggable: { type: Boolean, default: true },
})
const emit = defineEmits(['open', 'dragstart', 'dragend'])

const priority = computed(() => PRIORITY_META[props.card.priority] ?? PRIORITY_META.NORMAL)
const tone = computed(() => dueTone(props.card.daysUntilDue))
const done = computed(() => props.card.status === 'DONE')

/** 'D-2 · 11.22'. 끝난 카드는 날짜만 남긴다 — 지난 마감을 세어 봐야 소용없다 */
const dueText = computed(() => {
  if (!props.card.dueDate) return ''
  const date = shortDate(props.card.dueDate)
  return done.value ? date : `${dueShort(props.card.daysUntilDue)} · ${date}`
})

function onDragStart(event) {
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', String(props.card.id))
  emit('dragstart', props.card)
}
</script>

<template>
  <article
    class="kcard"
    :class="{ 'kcard--dragging': dragging, 'kcard--done': done, 'kcard--static': !draggable }"
    :draggable="draggable"
    tabindex="0"
    role="button"
    @dragstart="onDragStart"
    @dragend="emit('dragend')"
    @click="emit('open', card)"
    @keydown.enter.prevent="emit('open', card)"
    @keydown.space.prevent="emit('open', card)"
  >
    <div class="kcard__top">
      <!--
        중요도는 막대 세 개로. 색 배지를 카드마다 달면 보드 전체가 알록달록해져
        정작 급한 것이 묻힌다. 색은 높음(호박)과 긴급(빨강)에만 쓴다.
      -->
      <span class="prio" :class="`prio--${priority.tone}`" :title="`중요도 ${priority.label}`">
        <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true">
          <rect x="1" y="7" width="2.4" height="4" rx="0.6" class="prio__bar prio__bar--on" />
          <rect x="4.8" y="4.5" width="2.4" height="6.5" rx="0.6" class="prio__bar" :class="{ 'prio__bar--on': priority.level >= 2 }" />
          <rect x="8.6" y="2" width="2.4" height="9" rx="0.6" class="prio__bar" :class="{ 'prio__bar--on': priority.level >= 3 }" />
        </svg>
        {{ priority.label }}
      </span>
      <span v-if="showProject" class="kcard__project">{{ card.projectName }}</span>
    </div>

    <h4 class="kcard__title">{{ card.title }}</h4>
    <p v-if="card.content" class="kcard__content">{{ card.content }}</p>

    <div class="kcard__meta">
      <span
        v-if="card.dueDate"
        class="kcard__due"
        :class="`kcard__due--${done ? 'neutral' : tone}`"
        :title="done ? `완료일 ${card.dueDate}` : `완료일 ${card.dueDate} · ${dueLabel(card.daysUntilDue)}`"
      >
        {{ dueText }}
      </span>
      <span v-else-if="card.startDate" class="kcard__start" title="생성일(시작일)">
        {{ shortDate(card.startDate) }} 시작
      </span>
      <UserAvatar
        v-if="card.assignee"
        class="kcard__who"
        :user="card.assignee"
        :size="20"
        :title="`담당 ${card.assignee.displayName}`"
      />
    </div>
  </article>
</template>

<style scoped>
.kcard {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 11px 12px 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--surface);
  cursor: grab;
  transition: border-color 0.12s, opacity 0.12s;
}

/*
 * 들어올리지 않는다. 보드에 카드가 스무 장씩 놓이는데 하나하나가 떠 있으면
 * 어느 것도 떠 있지 않은 것과 같다. 지금 가리키는 카드는 테두리로만 답한다.
 */
.kcard:hover {
  border-color: var(--line-strong);
}

.kcard:focus-visible {
  outline: none;
  border-color: var(--brand);
  box-shadow: var(--ring);
}

.kcard--dragging {
  opacity: 0.4;
  border-style: dashed;
}

.kcard--static {
  cursor: pointer;
}

.kcard__top {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 16px;
}

.prio {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--text-3);
  font-size: 11.5px;
  font-weight: 500;
}

.prio__bar {
  fill: var(--line-strong);
}

.prio__bar--on {
  fill: currentColor;
}

.prio--info {
  color: var(--text-2);
}

.prio--warn {
  color: var(--warn);
}

.prio--danger {
  color: var(--danger);
}

.kcard__project {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--text-3);
  font-size: 11.5px;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kcard__title {
  margin: 2px 0 0;
  font-size: 13.5px;
  font-weight: 600;
  line-height: 1.45;
  word-break: break-word;
}

.kcard--done .kcard__title {
  color: var(--text-3);
}

.kcard__content {
  margin: 0;
  color: var(--text-3);
  font-size: 12.5px;
  line-height: 1.5;
  /* 카드가 길어지면 보드가 흐트러진다. 두 줄까지만 보여주고 자른다 */
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  white-space: pre-wrap;
  word-break: break-word;
}

.kcard__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 20px;
  margin-top: 6px;
}

.kcard__due,
.kcard__start {
  font-family: var(--font-num);
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
  color: var(--text-3);
}

.kcard__due--warn {
  color: var(--warn);
  font-weight: 500;
}

.kcard__due--danger {
  color: var(--danger);
  font-weight: 500;
}

/* 담당자는 맨 오른쪽에 붙는다. 여러 장을 훑을 때 한 줄로 읽힌다 */
.kcard__who {
  margin-left: auto;
}
</style>
