<script setup>
import { ref } from 'vue'
import { ROOT } from '@/stores/memo'

const props = defineProps({
  nodes: { type: Array, default: () => [] },
  selectedId: { type: [Number, String], default: ROOT },
  depth: { type: Number, default: 0 },
  isOpen: { type: Function, required: true },
})
const emit = defineEmits(['select', 'toggle', 'rename', 'remove', 'add-child', 'drop-memo', 'drop-folder'])

/** 지금 끌고 온 것이 올라와 있는 폴더. 어디에 떨어질지 보여준다. */
const hoverId = ref(null)

function onDragOver(event, node) {
  const types = event.dataTransfer.types
  if (!types.includes('application/x-memo-id') && !types.includes('application/x-folder-id')) return
  event.preventDefault()
  event.dataTransfer.dropEffect = 'move'
  hoverId.value = node.id
}

function onDrop(event, node) {
  event.preventDefault()
  event.stopPropagation()
  hoverId.value = null

  const memoId = event.dataTransfer.getData('application/x-memo-id')
  if (memoId) {
    emit('drop-memo', { memoId: Number(memoId), folderId: node.id })
    return
  }
  const folderId = event.dataTransfer.getData('application/x-folder-id')
  if (folderId && Number(folderId) !== node.id) {
    emit('drop-folder', { folderId: Number(folderId), parentId: node.id })
  }
}

function onDragStart(event, node) {
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('application/x-folder-id', String(node.id))
}
</script>

<template>
  <ul class="tree" :class="{ 'tree--nested': depth > 0 }">
    <li v-for="node in nodes" :key="node.id">
      <div
        class="row"
        :class="{ 'row--on': selectedId === node.id, 'row--drop': hoverId === node.id }"
        :style="{ paddingLeft: `${8 + depth * 14}px` }"
        draggable="true"
        @dragstart="onDragStart($event, node)"
        @dragover="onDragOver($event, node)"
        @dragleave="hoverId = null"
        @drop="onDrop($event, node)"
      >
        <button
          class="row__twisty"
          type="button"
          :aria-label="isOpen(node.id) ? '접기' : '펼치기'"
          :disabled="!node.children.length"
          @click.stop="emit('toggle', node.id)"
        >
          <span v-if="node.children.length" aria-hidden="true">{{ isOpen(node.id) ? '▾' : '▸' }}</span>
        </button>

        <button class="row__name" type="button" @click="emit('select', node.id)">
          <span class="row__icon" aria-hidden="true">{{ isOpen(node.id) ? '📂' : '📁' }}</span>
          <span class="row__label">{{ node.name }}</span>
          <span v-if="node.memoCount" class="row__count">{{ node.memoCount }}</span>
        </button>

        <span class="row__tools">
          <button class="row__tool" type="button" title="하위 폴더 추가" @click.stop="emit('add-child', node)">＋</button>
          <button class="row__tool" type="button" title="이름 바꾸기" @click.stop="emit('rename', node)">✎</button>
          <button
            class="row__tool row__tool--danger"
            type="button"
            title="폴더 삭제"
            @click.stop="emit('remove', node)"
          >
            ✕
          </button>
        </span>
      </div>

      <MemoTree
        v-if="node.children.length && isOpen(node.id)"
        :nodes="node.children"
        :selected-id="selectedId"
        :depth="depth + 1"
        :is-open="isOpen"
        @select="emit('select', $event)"
        @toggle="emit('toggle', $event)"
        @rename="emit('rename', $event)"
        @remove="emit('remove', $event)"
        @add-child="emit('add-child', $event)"
        @drop-memo="emit('drop-memo', $event)"
        @drop-folder="emit('drop-folder', $event)"
      />
    </li>
  </ul>
</template>

<style scoped>
.tree {
  display: flex;
  flex-direction: column;
  gap: 1px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.row {
  display: flex;
  align-items: center;
  gap: 2px;
  padding-right: 4px;
  border-radius: var(--radius-xs);
  color: var(--text-2);
}

.row:hover {
  background: var(--surface-hover);
}

.row--on {
  background: var(--brand-soft);
  color: var(--brand-strong);
}

/* 지금 끌고 온 것이 여기 떨어진다 */
.row--drop {
  background: var(--brand-soft);
  box-shadow: inset 0 0 0 1px var(--brand);
}

.row__twisty {
  flex: none;
  width: 16px;
  height: 24px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-4);
  font-size: 10px;
  line-height: 1;
}

.row__twisty:disabled {
  cursor: default;
}

.row__name {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
  padding: 4px 2px;
  border: 0;
  background: transparent;
  color: inherit;
  font-size: var(--fs-sm);
  font-weight: 600;
  text-align: left;
}

.row__icon {
  flex: none;
  font-size: 11px;
  line-height: 1;
}

.row__label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row__count {
  flex: none;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--bg-subtle);
  color: var(--text-3);
  font-size: 10px;
  font-weight: 600;
}

/* 손이 올라갔을 때만 보인다. 늘 떠 있으면 폴더 이름보다 버튼이 먼저 읽힌다 */
.row__tools {
  display: flex;
  gap: 0;
  opacity: 0;
  transition: opacity 0.1s;
}

.row:hover .row__tools,
.row:focus-within .row__tools {
  opacity: 1;
}

.row__tool {
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--text-3);
  font-size: 11px;
  line-height: 1;
}

.row__tool:hover {
  background: var(--surface);
  color: var(--text);
}

.row__tool--danger:hover {
  background: var(--danger-soft);
  color: var(--danger);
}
</style>
