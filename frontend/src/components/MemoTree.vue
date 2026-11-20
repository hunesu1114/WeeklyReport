<script setup>
import { ref } from 'vue'
import { RECENT } from '@/stores/memo'

const props = defineProps({
  nodes: { type: Array, default: () => [] },
  selectedId: { type: [Number, String], default: RECENT },
  depth: { type: Number, default: 0 },
  isOpen: { type: Function, required: true },
  /** 이름을 고치거나 새로 만드는 중인 줄. { mode: 'rename'|'create', id?, parentId? } */
  edit: { type: Object, default: null },
  /** ⋯ 메뉴가 열린 폴더 */
  menuId: { type: Number, default: null },
})
const emit = defineEmits([
  'select',
  'toggle',
  'menu',
  'rename',
  'remove',
  'add-child',
  'commit-edit',
  'cancel-edit',
  'drop-memo',
  'drop-folder',
])

/** 지금 끌고 온 것이 올라와 있는 폴더. 어디에 떨어질지 보여준다. */
const hoverId = ref(null)

/* 이름 칸은 나타나자마자 글자를 고른 채로 받는다. 바로 치면 바뀐다 */
const vFocus = {
  mounted(el) {
    el.focus()
    el.select()
  },
}

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

/** Enter 는 확정, Esc 는 취소. 칸을 벗어나도 확정한다 — 탐색기와 같다. */
function onEditKey(event) {
  if (event.key === 'Enter') {
    event.preventDefault()
    emit('commit-edit', event.target.value)
  } else if (event.key === 'Escape') {
    event.preventDefault()
    emit('cancel-edit')
  }
}

const renaming = (node) => props.edit?.mode === 'rename' && props.edit.id === node.id
const creatingIn = (node) => props.edit?.mode === 'create' && props.edit.parentId === node.id
const indent = (depth) => `${2 + depth * 16}px`
</script>

<template>
  <ul class="tree" :class="{ 'tree--nested': depth > 0 }">
    <li v-for="node in nodes" :key="node.id">
      <div
        class="row"
        :class="{
          'row--on': selectedId === node.id,
          'row--drop': hoverId === node.id,
          'row--menu': menuId === node.id,
        }"
        :style="{ paddingLeft: indent(depth) }"
        :draggable="!renaming(node)"
        @dragstart="onDragStart($event, node)"
        @dragover="onDragOver($event, node)"
        @dragleave="hoverId = null"
        @drop="onDrop($event, node)"
      >
        <button
          v-if="node.children.length"
          class="row__twisty"
          type="button"
          :aria-label="`${isOpen(node.id) ? '접기' : '펼치기'}: ${node.name}`"
          :aria-expanded="isOpen(node.id)"
          @click.stop="emit('toggle', node.id)"
        >
          <AppIcon :name="isOpen(node.id) ? 'chevron-down' : 'chevron-right'" :size="12" />
        </button>
        <span v-else class="row__twisty" aria-hidden="true"></span>

        <label v-if="renaming(node)" class="row__edit">
          <AppIcon name="folder" :size="15" class="row__icon" />
          <input
            v-focus
            class="row__input"
            type="text"
            maxlength="100"
            :value="node.name"
            aria-label="폴더 이름"
            @keydown="onEditKey"
            @blur="emit('commit-edit', $event.target.value)"
          />
        </label>

        <button
          v-else
          class="row__name"
          type="button"
          :aria-current="selectedId === node.id ? 'true' : undefined"
          @click="emit('select', node.id)"
          @dblclick="emit('rename', node)"
        >
          <AppIcon name="folder" :size="15" class="row__icon" />
          <span class="row__label">{{ node.name }}</span>
          <span v-if="node.memoCount" class="row__count">{{ node.memoCount }}</span>
        </button>

        <button
          v-if="!renaming(node)"
          class="row__more"
          type="button"
          :aria-label="`폴더 메뉴: ${node.name}`"
          :aria-expanded="menuId === node.id"
          @click.stop="emit('menu', menuId === node.id ? null : node.id)"
        >
          <AppIcon name="more" :size="14" />
        </button>

        <div v-if="menuId === node.id" class="menu" role="menu" @click.stop>
          <button class="menu__item" type="button" role="menuitem" @click="emit('add-child', node)">
            <AppIcon name="plus" :size="14" />
            하위 폴더 만들기
          </button>
          <button class="menu__item" type="button" role="menuitem" @click="emit('rename', node)">
            <AppIcon name="pencil" :size="14" />
            이름 바꾸기
          </button>
          <button class="menu__item menu__item--danger" type="button" role="menuitem" @click="emit('remove', node)">
            <AppIcon name="trash" :size="14" />
            폴더 삭제
          </button>
        </div>
      </div>

      <!-- 이 폴더 안에 새 폴더를 만드는 중. 맨 위에 빈 칸이 생긴다 -->
      <div v-if="creatingIn(node)" class="row row--new" :style="{ paddingLeft: indent(depth + 1) }">
        <span class="row__twisty" aria-hidden="true"></span>
        <label class="row__edit">
          <AppIcon name="folder" :size="15" class="row__icon" />
          <input
            v-focus
            class="row__input"
            type="text"
            maxlength="100"
            placeholder="새 폴더"
            aria-label="새 폴더 이름"
            @keydown="onEditKey"
            @blur="emit('commit-edit', $event.target.value)"
          />
        </label>
      </div>

      <MemoTree
        v-if="node.children.length && isOpen(node.id)"
        :nodes="node.children"
        :selected-id="selectedId"
        :depth="depth + 1"
        :is-open="isOpen"
        :edit="edit"
        :menu-id="menuId"
        @select="emit('select', $event)"
        @toggle="emit('toggle', $event)"
        @menu="emit('menu', $event)"
        @rename="emit('rename', $event)"
        @remove="emit('remove', $event)"
        @add-child="emit('add-child', $event)"
        @commit-edit="emit('commit-edit', $event)"
        @cancel-edit="emit('cancel-edit')"
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
  position: relative;
  display: flex;
  align-items: center;
  height: 30px;
  padding-right: 2px;
  border-radius: 5px;
  color: var(--text-2);
}

.row:hover {
  background: var(--surface-hover);
}

.row--on,
.row--on:hover {
  background: var(--surface-sel);
  color: var(--text);
}

/* 지금 끌고 온 것이 여기 떨어진다 */
.row--drop,
.row--drop:hover {
  background: var(--brand-soft);
  box-shadow: inset 0 0 0 1px var(--brand);
  color: var(--brand-strong);
}

.row__twisty {
  display: inline-grid;
  place-items: center;
  flex: none;
  width: 18px;
  height: 30px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-3);
}

.row__name,
.row__edit {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
  height: 30px;
  padding: 0 4px 0 0;
  border: 0;
  background: transparent;
  color: inherit;
  font-size: 13px;
  font-weight: 500;
  text-align: left;
}

.row--on .row__name {
  font-weight: 600;
}

.row__icon {
  color: var(--text-3);
}

.row--on .row__icon {
  color: var(--text);
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
  color: var(--text-3);
  font-family: var(--font-num);
  font-size: 11.5px;
  font-weight: 400;
}

.row__input {
  flex: 1;
  min-width: 0;
  height: 24px;
  padding: 0 6px;
  border: 1px solid var(--brand);
  border-radius: var(--radius-xs);
  outline: none;
  background: var(--surface);
  box-shadow: var(--ring);
  color: var(--text);
  font-size: 13px;
}

/*
 * 손이 올라갔거나 고른 폴더에서만 ⋯ 이 보인다.
 * 늘 떠 있으면 폴더 이름보다 버튼이 먼저 읽힌다. 키보드로 오면 보인다.
 */
.row__more {
  display: inline-grid;
  place-items: center;
  flex: none;
  width: 24px;
  height: 24px;
  padding: 0;
  border: 0;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--text-3);
  opacity: 0;
}

.row:hover .row__more,
.row--on .row__more,
.row--menu .row__more,
.row__more:focus-visible {
  opacity: 1;
}

.row__more:hover {
  background: var(--surface);
  color: var(--text);
}

/* ---------- ⋯ 메뉴 ---------- */
.menu {
  position: absolute;
  top: calc(100% + 2px);
  right: 0;
  z-index: 20;
  min-width: 170px;
  padding: 4px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: var(--shadow-3);
}

.menu__item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 7px 8px;
  border: 0;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--text);
  font-size: 13px;
  text-align: left;
}

.menu__item:hover {
  background: var(--surface-hover);
}

.menu__item--danger {
  color: var(--danger);
}

.menu__item--danger:hover {
  background: var(--danger-soft);
}
</style>
