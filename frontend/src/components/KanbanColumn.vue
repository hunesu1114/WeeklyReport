<script setup>
import { computed, ref } from 'vue'
import KanbanCardItem from '@/components/KanbanCardItem.vue'
import { STATUS_META } from '@/utils/kanban'

const props = defineProps({
  status: { type: String, required: true },
  cards: { type: Array, default: () => [] },
  draggingId: { type: [Number, null], default: null },
})
const emit = defineEmits(['open', 'add', 'dragstart', 'dragend', 'drop'])

const meta = computed(() => STATUS_META[props.status])
/** 드롭될 위치. null 이면 이 칸 위에 없다는 뜻. */
const dropIndex = ref(null)

const listRef = ref(null)

/**
 * 마우스 y 좌표로 몇 번째 자리에 놓일지 계산한다.
 * 각 카드의 세로 중앙보다 위면 그 카드 앞, 아니면 뒤.
 */
function indexFromPointer(event) {
  const nodes = [...(listRef.value?.querySelectorAll('[data-card-id]') ?? [])].filter(
    (node) => Number(node.dataset.cardId) !== props.draggingId,
  )
  for (let i = 0; i < nodes.length; i += 1) {
    const box = nodes[i].getBoundingClientRect()
    if (event.clientY < box.top + box.height / 2) return i
  }
  return nodes.length
}

function onDragOver(event) {
  event.dataTransfer.dropEffect = 'move'
  dropIndex.value = indexFromPointer(event)
}

function onDragLeave(event) {
  // 자식으로 들어가는 것도 leave 로 잡히므로, 칸 밖으로 나갈 때만 지운다
  if (!event.currentTarget.contains(event.relatedTarget)) {
    dropIndex.value = null
  }
}

function onDrop(event) {
  const index = dropIndex.value ?? indexFromPointer(event)
  dropIndex.value = null
  const cardId = Number(event.dataTransfer.getData('text/plain'))
  if (Number.isFinite(cardId)) {
    emit('drop', { cardId, status: props.status, sortOrder: index })
  }
}

function onDragEnd() {
  dropIndex.value = null
  emit('dragend')
}
</script>

<template>
  <section
    class="col"
    :class="[`col--${meta.tone}`, { 'col--over': dropIndex !== null }]"
    @dragover.prevent="onDragOver"
    @dragleave="onDragLeave"
    @drop.prevent="onDrop"
  >
    <header class="col__head">
      <div class="col__title">
        <span class="col__dot" aria-hidden="true"></span>
        <strong>{{ meta.label }}</strong>
        <span class="col__count">{{ cards.length }}</span>
      </div>
      <button class="btn btn--ghost btn--icon" type="button" :title="`${meta.label} 에 카드 추가`" @click="emit('add', status)">
        ＋
      </button>
    </header>
    <p class="col__hint tiny muted">{{ meta.hint }}</p>

    <div ref="listRef" class="col__list">
      <div v-if="dropIndex === 0" class="col__marker" aria-hidden="true"></div>

      <template v-for="(card, index) in cards" :key="card.id">
        <div :data-card-id="card.id">
          <KanbanCardItem
            :card="card"
            :dragging="draggingId === card.id"
            @open="emit('open', $event)"
            @dragstart="emit('dragstart', $event)"
            @dragend="onDragEnd"
          />
        </div>
        <div v-if="dropIndex === index + 1" class="col__marker" aria-hidden="true"></div>
      </template>

      <button v-if="!cards.length" class="col__empty" type="button" @click="emit('add', status)">
        카드 없음 · 눌러서 추가
      </button>
    </div>
  </section>
</template>

<style scoped>
.col {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--bg-subtle);
  transition: border-color 0.12s, background-color 0.12s;
}

.col--over {
  border-color: var(--brand);
  background: var(--brand-soft);
}

.col__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.col__title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  letter-spacing: 0.04em;
}

.col__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-4);
}

.col--info .col__dot {
  background: var(--info);
}
.col--violet .col__dot {
  background: var(--violet);
}
.col--ok .col__dot {
  background: var(--brand);
}

.col__count {
  padding: 1px 7px;
  border-radius: 999px;
  background: var(--surface);
  border: 1px solid var(--line);
  color: var(--text-3);
  font-size: 11px;
  font-weight: 700;
}

.col__hint {
  margin: 2px 0 10px 15px;
}

.col__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 80px;
  flex: 1;
}

/* 드롭 위치 표시선 */
.col__marker {
  height: 3px;
  border-radius: 999px;
  background: var(--brand);
  animation: marker-in 0.12s ease-out;
}

@keyframes marker-in {
  from {
    transform: scaleX(0.4);
    opacity: 0;
  }
}

.col__empty {
  padding: 18px 10px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-4);
  font-size: 12px;
}

.col__empty:hover {
  border-color: var(--brand);
  color: var(--brand-strong);
}
</style>
