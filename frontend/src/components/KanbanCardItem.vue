<script setup>
import { computed } from 'vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { PRIORITY_META, dueLabel, dueTone } from '@/utils/kanban'

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
const priorityBadge = computed(() =>
  priority.value.tone === 'neutral' ? 'badge' : `badge badge--${priority.value.tone}`,
)
const due = computed(() => dueLabel(props.card.daysUntilDue))
const tone = computed(() => dueTone(props.card.daysUntilDue))
const done = computed(() => props.card.status === 'DONE')

function onDragStart(event) {
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', String(props.card.id))
  emit('dragstart', props.card)
}
</script>

<template>
  <article
    class="kcard"
    :class="[
      `kcard--${priority.tone}`,
      { 'kcard--dragging': dragging, 'kcard--done': done, 'kcard--static': !draggable },
    ]"
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
      <span :class="priorityBadge">{{ priority.label }}</span>
      <span v-if="showProject" class="kcard__project tiny muted">{{ card.projectName }}</span>
      <UserAvatar
        v-if="card.assignee"
        class="kcard__who"
        :user="card.assignee"
        :size="20"
        :title="`담당 ${card.assignee.displayName}`"
      />
    </div>

    <h4 class="kcard__title">{{ card.title }}</h4>
    <p v-if="card.content" class="kcard__content">{{ card.content }}</p>

    <div class="kcard__meta">
      <span v-if="card.startDate" class="tiny muted" title="생성일(시작일)">▶ {{ card.startDate }}</span>
      <span
        v-if="card.dueDate"
        class="kcard__due tiny"
        :class="`kcard__due--${done ? 'neutral' : tone}`"
        :title="`완료일 ${card.dueDate}`"
      >
        ■ {{ done ? card.dueDate : due }}
      </span>
    </div>
  </article>
</template>

<style scoped>
.kcard {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 9px 10px 8px;
  border: 1px solid var(--line);
  border-left: 3px solid var(--line-strong);
  border-radius: var(--radius-sm);
  background: var(--surface);
  cursor: grab;
  transition: border-color 0.12s, background-color 0.12s, opacity 0.12s;
}

/*
 * 들어올리지 않는다. 보드에 카드가 스무 장씩 놓이는데 하나하나가 떠 있으면
 * 어느 것도 떠 있지 않은 것과 같다. 지금 가리키는 카드만 배경으로 답한다.
 */
.kcard:hover {
  border-color: var(--line-strong);
  background: var(--surface-hover);
}

.kcard:focus-visible {
  outline: none;
  box-shadow: var(--ring);
}

.kcard--dragging {
  opacity: 0.4;
  border-style: dashed;
}

.kcard--static {
  cursor: pointer;
}

/* 중요도를 왼쪽 띠로 표시한다. 목록을 훑을 때 색만 보고 걸러진다. */
.kcard--neutral {
  border-left-color: var(--line-strong);
}
.kcard--info {
  border-left-color: var(--info);
}
.kcard--warn {
  border-left-color: var(--warn);
}
.kcard--danger {
  border-left-color: var(--danger);
}

.kcard--done .kcard__title {
  color: var(--text-3);
  text-decoration: line-through;
  text-decoration-color: var(--text-4);
}

.kcard__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.kcard__project {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: right;
}

/* 담당자는 맨 오른쪽에 붙는다. 여러 장을 훑을 때 한 줄로 읽힌다 */
.kcard__who {
  margin-left: auto;
}

.kcard__title {
  margin: 0;
  font-size: var(--fs);
  font-weight: 600;
  line-height: 1.45;
  letter-spacing: -0.01em;
  word-break: break-word;
}

.kcard__content {
  margin: 0;
  font-size: var(--fs-sm);
  line-height: 1.5;
  color: var(--text-3);
  /* 카드가 길어지면 보드가 흐트러진다. 세 줄까지만 보여주고 자른다 */
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  white-space: pre-wrap;
  word-break: break-word;
}

.kcard__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 2px;
  font-variant-numeric: tabular-nums;
}

.kcard__due {
  font-weight: 600;
}

.kcard__due--neutral {
  color: var(--text-3);
}
.kcard__due--warn {
  color: var(--warn);
}
.kcard__due--danger {
  color: var(--danger);
}
</style>
