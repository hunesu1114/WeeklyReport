<script setup>
import { computed } from 'vue'
import DetailEditor from '@/components/DetailEditor.vue'
import { THIS_WEEK } from '@/utils/report'

const props = defineProps({
  item: { type: Object, required: true },
  index: { type: Number, required: true },
  total: { type: Number, required: true },
  statuses: { type: Array, default: () => [] },
  taskNames: { type: Array, default: () => [] },
  dragging: { type: Boolean, default: false },
})
const emit = defineEmits(['remove', 'duplicate', 'move', 'dragstart', 'dragend', 'dragover', 'drop'])

const withHours = computed(() => props.item.section === THIS_WEEK)
const listId = computed(() => `task-names-${props.item.key}`)
const detailPlaceholder = computed(() =>
  withHours.value
    ? '[진행] 무엇을 했는지 한 줄로\n- 세부 내용\n   - 더 세부적인 내용'
    : '무엇을 할 예정인지 적어주세요',
)
</script>

<template>
  <article
    class="item"
    :class="{ 'item--dragging': dragging }"
    draggable="true"
    @dragstart="emit('dragstart', index)"
    @dragend="emit('dragend')"
    @dragover.prevent="emit('dragover', index)"
    @drop.prevent="emit('drop', index)"
  >
    <div class="item__bar">
      <span class="item__handle" title="끌어서 순서 바꾸기" aria-hidden="true">⋮⋮</span>
      <span class="item__no">{{ index + 1 }}</span>

      <div class="field item__name">
        <label :for="`name-${item.key}`">업무명</label>
        <input
          :id="`name-${item.key}`"
          v-model="item.taskName"
          class="control"
          type="text"
          :list="listId"
          placeholder="예: 포탈 v3"
          autocomplete="off"
        />
        <datalist :id="listId">
          <option v-for="name in taskNames" :key="name" :value="name" />
        </datalist>
      </div>

      <div class="field item__status">
        <label :for="`status-${item.key}`">진행상태</label>
        <select :id="`status-${item.key}`" v-model="item.status" class="control">
          <option value="">-</option>
          <option v-for="status in statuses" :key="status" :value="status">{{ status }}</option>
        </select>
      </div>

      <div v-if="withHours" class="field item__hours">
        <label :for="`hours-${item.key}`">소요시간(H)</label>
        <input
          :id="`hours-${item.key}`"
          v-model="item.hours"
          class="control"
          type="number"
          min="0"
          step="0.5"
          placeholder="0"
        />
      </div>

      <div class="item__actions">
        <button
          class="btn btn--ghost btn--icon"
          type="button"
          title="위로"
          :disabled="index === 0"
          @click="emit('move', index, -1)"
        >
          ↑
        </button>
        <button
          class="btn btn--ghost btn--icon"
          type="button"
          title="아래로"
          :disabled="index === total - 1"
          @click="emit('move', index, 1)"
        >
          ↓
        </button>
        <button
          class="btn btn--ghost btn--icon"
          type="button"
          title="복제"
          @click="emit('duplicate', index)"
        >
          ⧉
        </button>
        <button
          class="btn btn--ghost btn--icon btn--danger"
          type="button"
          title="삭제"
          @click="emit('remove', index)"
        >
          ✕
        </button>
      </div>
    </div>

    <div class="item__detail">
      <span class="item__detail-label">업무상세</span>
      <DetailEditor
        v-model="item.detail"
        :placeholder="detailPlaceholder"
        :min-rows="withHours ? 4 : 2"
        :show-meta="withHours"
      />
    </div>
  </article>
</template>

<style scoped>
.item {
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
  padding: 12px 14px 14px;
  transition: border-color 0.12s, box-shadow 0.12s, opacity 0.12s;
}

.item:hover {
  border-color: var(--line-strong);
}

.item--dragging {
  opacity: 0.42;
  border-style: dashed;
  border-color: var(--brand-strong);
}

.item__bar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 10px;
}

.item__name {
  flex: 1 1 200px;
}

.item__status {
  flex: 0 1 110px;
}

.item__hours {
  flex: 0 1 104px;
}

.item__handle {
  padding-bottom: 9px;
  color: var(--text-4);
  cursor: grab;
  letter-spacing: -2px;
  user-select: none;
}

.item__no {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  margin-bottom: 5px;
  border-radius: 7px;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 12px;
  font-weight: 800;
}

.item__actions {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-left: auto;
  padding-bottom: 4px;
}

.item__detail {
  margin-top: 12px;
}

.item__detail-label {
  display: block;
  margin-bottom: 5px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-3);
}

</style>
