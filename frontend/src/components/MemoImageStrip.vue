<script setup>
/**
 * 그림 줄. 제목 아래에 본문에 나온 순서대로 섬네일을 늘어놓는다.
 *
 * 본문 속 토큰만으로는 무엇이 들었는지 보이지 않는다. 그렇다고 글 사이에 그림을 펼치면
 * textarea 를 버려야 해서, 그림은 여기 한 줄에 모으고 본문의 토큰 줄과 번호로 잇는다.
 * 커서가 토큰 줄에 있으면 그 칸에 테두리가 생기고 아래 줄에 이름과 크기가 나온다.
 */
import { computed } from 'vue'
import { formatBytes, typeLabel } from '@/utils/memoImage'

const props = defineProps({
  /** [{ id, alt, n, entry }] — entry 가 없으면 지워졌거나 남의 그림이다 */
  items: { type: Array, required: true },
  currentId: { type: String, default: null },
  readonly: { type: Boolean, default: false },
})

const emit = defineEmits(['open', 'retry', 'add', 'broken'])

const current = computed(() => props.items.find((item) => item.id === props.currentId) ?? null)

function stateOf(item) {
  if (!item.entry) return 'missing'
  return item.entry.status
}

function info(entry) {
  if (!entry) return ''
  const parts = []
  if (entry.width && entry.height) parts.push(`${entry.width}×${entry.height}`)
  if (typeLabel(entry.contentType)) parts.push(typeLabel(entry.contentType))
  if (entry.byteSize) parts.push(formatBytes(entry.byteSize))
  return parts.join(' · ')
}

function ariaOf(item) {
  const name = item.alt || '설명 없음'
  switch (stateOf(item)) {
    case 'failed':
      return `그림 ${item.n} 올리지 못함, ${name}. 눌러서 다시 올리기`
    case 'uploading':
      return `그림 ${item.n} 올리는 중 ${Math.round((item.entry.progress ?? 0) * 100)}%, ${name}`
    case 'preparing':
      return `그림 ${item.n} 준비 중, ${name}`
    case 'missing':
      return `그림 ${item.n} 찾을 수 없음, ${name}`
    default:
      return `그림 ${item.n} 크게 보기, ${name}`
  }
}

function onTile(item) {
  if (stateOf(item) === 'failed') emit('retry', item.id)
  else emit('open', item.id)
}

const captionText = computed(() => {
  const item = current.value
  if (!item) return null
  const state = stateOf(item)
  if (state === 'failed') return { tone: 'bad', text: item.entry.error || '올리지 못했습니다', info: '눌러서 다시 올리기' }
  if (state === 'missing') return { tone: 'bad', text: item.alt || '설명 없음', info: '그림을 찾을 수 없습니다. 지워졌거나 다른 사람의 그림입니다' }
  return { tone: 'on', text: item.alt || '설명 없음', info: info(item.entry) }
})
</script>

<template>
  <div class="strip">
    <div class="strip__row" role="list" aria-label="이 메모의 그림, 본문 순서">
      <div v-for="item in items" :key="item.id" role="listitem" class="strip__cell">
        <button
          type="button"
          class="tile"
          :class="[`tile--${stateOf(item)}`, { 'tile--on': item.id === currentId }]"
          :title="item.alt || '설명 없음'"
          :aria-label="ariaOf(item)"
          @click="onTile(item)"
        >
          <img
            v-if="item.entry && (item.entry.preview || item.entry.thumbUrl)"
            class="tile__img"
            :src="item.entry.preview || item.entry.thumbUrl"
            alt=""
            loading="lazy"
            draggable="false"
            @error="emit('broken', item.id)"
          />
          <span class="tile__n num">{{ item.n }}</span>

          <span v-if="stateOf(item) === 'uploading' || stateOf(item) === 'preparing'" class="tile__over">
            <span class="tile__say">{{ stateOf(item) === 'preparing' ? '줄이는 중' : '올리는 중' }}</span>
            <span v-if="stateOf(item) === 'uploading'" class="tile__pct num">{{ Math.round((item.entry.progress ?? 0) * 100) }}%</span>
            <span class="tile__bar"><span :style="{ width: `${Math.round((item.entry.progress ?? 0) * 100)}%` }"></span></span>
          </span>
          <span v-else-if="stateOf(item) === 'failed'" class="tile__over tile__over--bad">
            <AppIcon name="refresh" :size="14" />
            다시 올리기
          </span>
          <span v-else-if="stateOf(item) === 'missing'" class="tile__over tile__over--missing">
            <AppIcon name="image" :size="14" />
            그림 없음
          </span>
        </button>
      </div>

      <button v-if="!readonly" type="button" class="tile tile--add" aria-label="그림 넣기 (파일 고르기)" @click="emit('add')">
        <AppIcon name="plus" :size="14" />
        <span class="tile__addlabel">그림 넣기</span>
        <span class="tile__addhint num">Ctrl+V · 끌어 놓기</span>
      </button>
    </div>

    <!-- 커서가 토큰 줄을 지나갈 때마다 본문이 들썩이지 않게 자리는 늘 지킨다 -->
    <p class="strip__cap" :class="captionText && `strip__cap--${captionText.tone}`" aria-live="polite">
      <template v-if="captionText">
        <span class="strip__capn num">{{ current.n }}</span>
        <span class="strip__captext">{{ captionText.text }}</span>
        <span class="strip__capinfo num">{{ captionText.info }}</span>
      </template>
    </p>
  </div>
</template>

<style scoped>
.strip {
  padding: 0 var(--gutter);
}

.strip__row {
  display: flex;
  gap: 8px;
  margin: 0 -3px;
  padding: 3px 3px 6px;
  overflow-x: auto;
  scrollbar-width: thin;
}

.strip__cell {
  flex: none;
}

.tile {
  position: relative;
  display: block;
  flex: none;
  width: 112px;
  height: 70px;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--line-strong);
  border-radius: 5px;
  background: var(--bg-subtle);
  cursor: zoom-in;
}

.tile:focus-visible {
  outline: none;
  box-shadow: var(--ring);
}

.tile--on {
  border-color: var(--brand);
  box-shadow: 0 0 0 2px var(--brand-soft);
}

.tile--failed {
  border-color: var(--danger-border);
  cursor: pointer;
}

.tile--missing {
  border-style: dashed;
  cursor: default;
}

.tile__img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.tile--uploading .tile__img,
.tile--preparing .tile__img,
.tile--failed .tile__img {
  opacity: 0.35;
}

.tile__n {
  position: absolute;
  top: 4px;
  left: 4px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 3px;
  background: rgba(24, 25, 27, 0.72);
  color: #ffffff;
  font-size: 10.5px;
  line-height: 16px;
  text-align: center;
}

.tile__over {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: var(--text);
  line-height: 1.3;
}

.tile__say {
  color: var(--text-2);
  font-size: 10.5px;
}

.tile__pct {
  font-size: 12px;
  font-weight: 500;
}

.tile__bar {
  position: absolute;
  right: 6px;
  bottom: 6px;
  left: 6px;
  height: 3px;
  overflow: hidden;
  border-radius: 2px;
  background: var(--line-strong);
}

.tile__bar span {
  display: block;
  height: 3px;
  background: var(--brand);
  transition: width 0.2s;
}

.tile__over--bad {
  gap: 3px;
  color: var(--danger);
  font-size: 11px;
  font-weight: 600;
}

.tile__over--missing {
  gap: 3px;
  color: var(--text-3);
  font-size: 11px;
}

.tile--add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  border: 1px dashed var(--line-strong);
  background: transparent;
  color: var(--text-3);
  line-height: 1.3;
  cursor: pointer;
}

.tile--add:hover {
  border-color: var(--brand-border);
  color: var(--brand);
}

.tile__addlabel {
  color: var(--text-2);
  font-size: 12px;
  font-weight: 500;
}

.tile__addhint {
  font-size: 10px;
}

.strip__cap {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  height: 18px;
  margin: 4px 0 8px;
  overflow: hidden;
  color: var(--text-3);
  font-size: 12px;
  white-space: nowrap;
}

.strip__capn {
  flex: none;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 4px;
  background: var(--brand-soft);
  color: var(--brand-strong);
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}

.strip__cap--bad,
.strip__cap--bad .strip__capinfo {
  color: var(--danger);
}

.strip__cap--bad .strip__capn {
  background: var(--danger-soft);
  color: var(--danger);
}

.strip__captext {
  overflow: hidden;
  color: var(--text-2);
  text-overflow: ellipsis;
}

.strip__cap--bad .strip__captext {
  color: var(--danger);
}

.strip__capinfo {
  flex: none;
  color: var(--text-3);
  font-size: 11px;
}
</style>
