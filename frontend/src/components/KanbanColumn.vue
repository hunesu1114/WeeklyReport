<script setup>
import { computed, ref } from 'vue'
import KanbanCardItem from '@/components/KanbanCardItem.vue'
import { STATUS_META } from '@/utils/kanban'

const props = defineProps({
  status: { type: String, required: true },
  cards: { type: Array, default: () => [] },
  draggingId: { type: [Number, null], default: null },
  /** 읽기 전용으로 참여 중이면 옮기지도 추가하지도 못한다 */
  readOnly: { type: Boolean, default: false },
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

/**
 * 칸 위아래 끝에서는 목록을 따라 굴린다.
 *
 * 칸이 제 안에서 스크롤하게 되면서, 화면 밖에 있는 자리로는 카드를 놓을 수
 * 없게 됐다. 끌고 가장자리에 대면 그쪽으로 밀어준다.
 */
function autoScroll(event) {
  const list = listRef.value
  if (!list || list.scrollHeight <= list.clientHeight) return

  const box = list.getBoundingClientRect()
  const edge = 52
  if (event.clientY < box.top + edge) list.scrollTop -= 14
  else if (event.clientY > box.bottom - edge) list.scrollTop += 14
}

function onDragOver(event) {
  if (props.readOnly) {
    event.dataTransfer.dropEffect = 'none'
    return
  }
  event.dataTransfer.dropEffect = 'move'
  autoScroll(event)
  dropIndex.value = indexFromPointer(event)
}

function onDragLeave(event) {
  // 자식으로 들어가는 것도 leave 로 잡히므로, 칸 밖으로 나갈 때만 지운다
  if (!event.currentTarget.contains(event.relatedTarget)) {
    dropIndex.value = null
  }
}

function onDrop(event) {
  if (props.readOnly) return
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
      <h2 class="col__title">{{ meta.name }}</h2>
      <span class="col__key">{{ meta.label }}</span>
      <span class="col__count">{{ cards.length }}</span>
      <span class="col__spacer"></span>
      <button
        v-if="!readOnly"
        class="btn btn--icon col__add"
        type="button"
        :title="`${meta.name}에 카드 추가`"
        :aria-label="`${meta.name}에 카드 추가`"
        @click="emit('add', status)"
      >
        <AppIcon name="plus" :size="14" />
      </button>
    </header>

    <div ref="listRef" class="col__list">
      <div v-if="dropIndex === 0" class="col__marker" aria-hidden="true"></div>

      <template v-for="(card, index) in cards" :key="card.id">
        <div :data-card-id="card.id">
          <KanbanCardItem
            :card="card"
            :dragging="draggingId === card.id"
            :draggable="!readOnly"
            @open="emit('open', $event)"
            @dragstart="emit('dragstart', $event)"
            @dragend="onDragEnd"
          />
        </div>
        <div v-if="dropIndex === index + 1" class="col__marker" aria-hidden="true"></div>
      </template>

      <button
        v-if="!cards.length && !readOnly"
        class="col__empty"
        type="button"
        @click="emit('add', status)"
      >
        카드 없음 · 눌러서 추가
      </button>
      <!-- 읽기 전용에게 '눌러서 추가'를 보여주면 눌렀다가 아무 일도 일어나지 않는다 -->
      <p v-else-if="!cards.length" class="col__empty col__empty--static">카드 없음</p>
    </div>
  </section>
</template>

<style scoped>
/*
 * 칸은 상자가 아니라 머리 위의 선 한 줄로 나눈다. 회색 상자 네 개를 나란히
 * 세우면 카드보다 상자가 먼저 보인다. 진행 중만 키컬러 선이다.
 */
.col {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 0 0 8px;
  border-top: 2px solid var(--line-strong);
  border-radius: 0 0 var(--radius-sm) var(--radius-sm);
  transition: background-color 0.12s;
}

.col--info {
  border-top-color: var(--text-2);
}

.col--violet {
  border-top-color: var(--brand);
}

.col--over {
  background: var(--brand-soft);
}

.col__head {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 42px;
}

.col__title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}

.col__key {
  color: var(--text-3);
  font-family: var(--font-num);
  font-size: 10.5px;
  letter-spacing: 0.04em;
}

.col__count {
  color: var(--text-3);
  font-family: var(--font-num);
  font-size: 12px;
}

.col__spacer {
  flex: 1;
}

.col__add {
  width: 26px;
  height: 26px;
}

/*
 * 카드가 쌓이는 곳. 칸 안에서만 스크롤한다.
 *
 * 칸이 카드 수만큼 길어지면 한 칸이 스무 장일 때 페이지가 통째로 늘어나고,
 * 보드 아래에 있는 활동 기록은 한참을 내려야 나온다. 칸 높이를 화면에 묶어
 * 두면 카드가 몇 장이든 보드 전체 높이가 그대로다.
 *
 * min-height: 0 은 flex 자식이 내용보다 작아질 수 있게 하는 장치다.
 * 이게 없으면 flex: 1 이 내용 높이를 그대로 밀어올려 overflow 가 걸리지 않는다.
 */
.col__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  min-height: 0;
  max-height: var(--col-list-max, clamp(220px, calc(100vh - 430px), 760px));
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  scrollbar-color: var(--line-strong) transparent;
}

.col__list::-webkit-scrollbar {
  width: 8px;
}

.col__list::-webkit-scrollbar-thumb {
  border: 2px solid transparent;
  border-radius: 999px;
  background: var(--line-strong);
  background-clip: content-box;
}

.col__list::-webkit-scrollbar-track {
  background: transparent;
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
  padding: 16px 10px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-3);
  font-size: 12px;
}

.col__empty:hover {
  border-color: var(--brand);
  color: var(--brand-strong);
}

/* 누를 수 없는 자리 표시. 손이 올라가도 반응하지 않는다 */
.col__empty--static {
  margin: 0;
  text-align: center;
}

.col__empty--static:hover {
  border-color: var(--line-strong);
  color: var(--text-4);
}
</style>
