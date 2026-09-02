<script setup>
import { ref } from 'vue'
import ItemEditor from '@/components/ItemEditor.vue'
import { newItem, uid } from '@/utils/report'

const props = defineProps({
  title: { type: String, required: true },
  hint: { type: String, default: '' },
  section: { type: String, required: true },
  items: { type: Array, required: true },
  statuses: { type: Array, default: () => [] },
  taskNames: { type: Array, default: () => [] },
})

const draggingIndex = ref(null)

function add() {
  props.items.push(newItem(props.section))
}

function remove(index) {
  props.items.splice(index, 1)
}

function duplicate(index) {
  const source = props.items[index]
  props.items.splice(index + 1, 0, { ...source, key: uid(), id: null })
}

function move(index, delta) {
  const target = index + delta
  if (target < 0 || target >= props.items.length) return
  const [item] = props.items.splice(index, 1)
  props.items.splice(target, 0, item)
}

function onDragStart(index) {
  draggingIndex.value = index
}

function onDragOver(index) {
  const from = draggingIndex.value
  if (from === null || from === index) return
  const [item] = props.items.splice(from, 1)
  props.items.splice(index, 0, item)
  draggingIndex.value = index
}

function onDrop() {
  draggingIndex.value = null
}
</script>

<template>
  <section class="card section">
    <div class="card__head">
      <div>
        <h2 class="card__title">{{ title }}</h2>
        <p v-if="hint" class="section__hint tiny muted">{{ hint }}</p>
      </div>
      <div class="section__head-right">
        <span class="badge">{{ items.length }}건</span>
        <button class="btn btn--sm" type="button" @click="add">+ 항목 추가</button>
      </div>
    </div>

    <div class="card__body section__body">
      <p v-if="!items.length" class="section__empty muted">
        아직 항목이 없습니다. <button class="linklike" type="button" @click="add">첫 항목 추가하기</button>
      </p>

      <ItemEditor
        v-for="(item, index) in items"
        :key="item.key"
        :item="item"
        :index="index"
        :total="items.length"
        :statuses="statuses"
        :task-names="taskNames"
        :dragging="draggingIndex === index"
        @remove="remove"
        @duplicate="duplicate"
        @move="move"
        @dragstart="onDragStart"
        @dragover="onDragOver"
        @drop="onDrop"
        @dragend="onDrop"
      />

      <button v-if="items.length" class="btn section__add" type="button" @click="add">
        + 항목 추가
      </button>
    </div>
  </section>
</template>

<style scoped>
.section__hint {
  margin: 2px 0 0;
}

.section__head-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.section__body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section__empty {
  margin: 0;
  padding: 26px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius);
  text-align: center;
}

.section__add {
  align-self: flex-start;
  border-style: dashed;
  color: var(--navy-600);
}

.linklike {
  border: 0;
  background: none;
  padding: 0;
  color: var(--navy-600);
  font-weight: 700;
  text-decoration: underline;
}
</style>
