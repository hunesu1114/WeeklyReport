<script setup>
/**
 * 메모 그림 크게 보기.
 *
 * 화면 전체를 어둡게 덮고 한 장을 크게 띄운다. 좌우로 넘기고, 아래 줄에서 골라 뛴다.
 * '본문에서 찾기'는 이 그림의 토큰 줄로 돌아가고, '본문에서 빼기'는 토큰을 지운다 —
 * 그림 자체를 지우는 버튼은 두지 않는다. 아무도 가리키지 않는 그림은 하루 뒤 서버가 치운다.
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { describeImage, typeLabel } from '@/utils/memoImage'

const props = defineProps({
  /** [{ id, alt, n, entry }] — 그림 줄과 같은 것 */
  items: { type: Array, required: true },
  index: { type: Number, required: true },
  readonly: { type: Boolean, default: false },
})

const emit = defineEmits(['update:index', 'close', 'find', 'remove'])

const root = ref(null)

const item = computed(() => props.items[props.index] ?? null)
const entry = computed(() => item.value?.entry ?? null)

/** 다 올렸으면 서버 원본, 아직이면 이 브라우저 안의 사본 */
const src = computed(() => entry.value?.url || entry.value?.full || entry.value?.preview || null)
const ready = computed(() => entry.value?.status === 'ready' && entry.value?.url)

const info = computed(() => describeImage(entry.value))

const status = computed(() => {
  const e = entry.value
  if (!e) return '그림을 찾을 수 없습니다. 지워졌거나 다른 사람의 그림입니다.'
  if (e.status === 'failed') return `올리지 못했습니다. ${e.error ?? ''}`
  if (e.status === 'uploading') return `올리는 중 ${Math.round((e.progress ?? 0) * 100)}%`
  if (e.status === 'preparing') return '줄이는 중'
  return ''
})

/** 내려받을 이름. 형식이 바뀌었을 수 있으니 확장자는 지금 형식으로 붙인다. */
const downloadName = computed(() => {
  const e = entry.value
  const ext = typeLabel(e?.contentType) || 'png'
  const stem = (e?.name || '').replace(/\.[^.]+$/, '') || item.value?.alt || `그림-${item.value?.id}`
  return `${stem}.${ext}`
})

function go(delta) {
  const count = props.items.length
  if (count < 2) return
  emit('update:index', (props.index + delta + count) % count)
}

function onKey(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopPropagation()
    emit('close')
  } else if (event.key === 'ArrowRight') {
    event.preventDefault()
    go(1)
  } else if (event.key === 'ArrowLeft') {
    event.preventDefault()
    go(-1)
  }
}

/** 그림 바깥(어두운 곳)을 누르면 닫는다. 그림이나 버튼을 누른 것은 닫지 않는다. */
function onBackdrop(event) {
  if (event.target === event.currentTarget) emit('close')
}

let returnFocus = null

onMounted(async () => {
  returnFocus = document.activeElement
  // 화면 어디에서 듣는 단축키보다 먼저 받는다 — Esc 가 편집기의 찾기 막대까지 닫으면 안 된다
  window.addEventListener('keydown', onKey, true)
  await nextTick()
  root.value?.focus()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey, true)
  if (returnFocus instanceof HTMLElement && document.contains(returnFocus)) returnFocus.focus()
})
</script>

<template>
  <Teleport to="body">
    <div ref="root" class="viewer" role="dialog" aria-modal="true" aria-label="그림 크게 보기" tabindex="-1">
      <header class="viewer__bar">
        <span class="viewer__count num">{{ index + 1 }} / {{ items.length }}</span>
        <span class="viewer__alt">{{ item?.alt || '설명 없음' }}</span>
        <span class="viewer__info num">{{ info }}</span>
        <span class="viewer__spacer"></span>

        <button class="viewer__btn" type="button" title="본문에서 이 그림의 토큰 줄을 고릅니다" @click="emit('find', item.id)">
          <AppIcon name="corner-down-right" :size="15" />
          본문에서 찾기
        </button>
        <a
          v-if="ready"
          class="viewer__btn"
          :href="entry.url"
          target="_blank"
          rel="noopener noreferrer"
          title="새 탭에서 원본 열기"
        >
          <AppIcon name="external" :size="15" />
          원본 열기
        </a>
        <a v-if="ready" class="viewer__btn" :href="entry.url" :download="downloadName" title="이 그림 내려받기">
          <AppIcon name="download" :size="15" />
          내려받기
        </a>
        <button
          v-if="!readonly"
          class="viewer__btn viewer__btn--danger"
          type="button"
          title="본문에서 그림 줄을 지웁니다. 되돌릴 수 있습니다"
          @click="emit('remove', item.id)"
        >
          <AppIcon name="trash" :size="15" />
          본문에서 빼기
        </button>
        <span class="viewer__divider" aria-hidden="true"></span>
        <button class="viewer__btn viewer__btn--icon" type="button" aria-label="닫기 (Esc)" title="닫기 (Esc)" @click="emit('close')">
          <AppIcon name="close" :size="16" />
        </button>
      </header>

      <div class="viewer__stage" @click="onBackdrop">
        <button
          v-if="items.length > 1"
          class="viewer__nav viewer__nav--prev"
          type="button"
          aria-label="앞 그림 (←)"
          @click="go(-1)"
        >
          <AppIcon name="chevron-left" :size="20" />
        </button>

        <img v-if="src" :key="item.id" class="viewer__img" :src="src" :alt="item.alt || ''" />
        <p v-if="status" class="viewer__status">{{ status }}</p>

        <button
          v-if="items.length > 1"
          class="viewer__nav viewer__nav--next"
          type="button"
          aria-label="다음 그림 (→)"
          @click="go(1)"
        >
          <AppIcon name="chevron-right" :size="20" />
        </button>
      </div>

      <footer class="viewer__foot">
        <div v-if="items.length > 1" class="viewer__film" role="list" aria-label="이 메모의 그림">
          <button
            v-for="(other, i) in items"
            :key="other.id"
            class="viewer__thumb"
            :class="{ 'viewer__thumb--on': i === index }"
            type="button"
            role="listitem"
            :aria-label="`그림 ${other.n}, ${other.alt || '설명 없음'}`"
            :aria-current="i === index ? 'true' : undefined"
            @click="emit('update:index', i)"
          >
            <img v-if="other.entry && (other.entry.preview || other.entry.thumbUrl)" :src="other.entry.preview || other.entry.thumbUrl" alt="" draggable="false" />
            <span class="viewer__thumbn num">{{ other.n }}</span>
          </button>
        </div>
        <p class="viewer__hint num">← → 넘기기 · Esc 닫기</p>
      </footer>
    </div>
  </Teleport>
</template>

<style scoped>
/*
 * 테마와 상관없이 늘 어둡다. 사진은 어두운 바탕에서 가장 덜 왜곡돼 보이고,
 * 밝은 테마에서 흰 바탕 스크린샷을 흰 판에 띄우면 경계가 사라진다.
 */
.viewer {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  /* 칸 너비를 화면에 묶는다. 안 묶으면 아래 섬네일 줄 길이만큼 넓어져 닫기 버튼이 밖으로 밀린다 */
  grid-template-columns: minmax(0, 1fr);
  grid-template-rows: auto minmax(0, 1fr) auto;
  background: rgba(14, 15, 16, 0.94);
  color: #e9e9e6;
  outline: none;
}

.viewer__bar {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  height: 52px;
  padding: 0 12px 0 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.viewer__count {
  flex: none;
  color: #a5a7a3;
  font-size: 12px;
}

.viewer__alt {
  min-width: 0;
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.viewer__info {
  flex: none;
  color: #8d908b;
  font-size: 11.5px;
}

.viewer__spacer {
  flex: 1;
}

.viewer__btn {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #d6d7d3;
  font: inherit;
  font-size: 13px;
  text-decoration: none;
  white-space: nowrap;
  cursor: pointer;
}

.viewer__btn:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}

.viewer__btn:focus-visible,
.viewer__nav:focus-visible,
.viewer__thumb:focus-visible {
  outline: 2px solid #7cc39e;
  outline-offset: 1px;
}

.viewer__btn--danger {
  color: #f3a69c;
}

.viewer__btn--danger:hover {
  color: #ffc2ba;
}

.viewer__btn--icon {
  width: 32px;
  padding: 0;
  justify-content: center;
}

.viewer__divider {
  flex: none;
  width: 1px;
  height: 18px;
  background: rgba(255, 255, 255, 0.12);
}

.viewer__stage {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 0;
  padding: 20px 72px;
}

.viewer__img {
  display: block;
  max-width: min(100%, 1400px);
  max-height: 100%;
  object-fit: contain;
  border-radius: 2px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.5);
}

.viewer__status {
  margin: 12px 0 0;
  color: #c8cac6;
  font-size: 13px;
}

.viewer__nav {
  position: absolute;
  top: 50%;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  margin-top: -22px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 50%;
  background: rgba(28, 29, 31, 0.8);
  color: #e9e9e6;
  cursor: pointer;
}

.viewer__nav:hover {
  background: rgba(50, 52, 55, 0.95);
}

.viewer__nav--prev {
  left: 16px;
}

.viewer__nav--next {
  right: 16px;
}

.viewer__foot {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 10px 16px 14px;
}

.viewer__film {
  display: flex;
  gap: 6px;
  max-width: 100%;
  overflow-x: auto;
  padding: 2px;
}

.viewer__thumb {
  position: relative;
  flex: none;
  width: 76px;
  height: 50px;
  padding: 0;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 4px;
  background: #232426;
  opacity: 0.55;
  cursor: pointer;
}

.viewer__thumb:hover {
  opacity: 0.85;
}

.viewer__thumb--on {
  border-color: #7cc39e;
  opacity: 1;
}

.viewer__thumb img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.viewer__thumbn {
  position: absolute;
  top: 3px;
  left: 3px;
  min-width: 14px;
  height: 14px;
  padding: 0 3px;
  border-radius: 3px;
  background: rgba(0, 0, 0, 0.6);
  color: #ffffff;
  font-size: 10px;
  line-height: 14px;
}

.viewer__hint {
  margin: 0;
  color: #7d807b;
  font-size: 11px;
}

@media (max-width: 760px) {
  .viewer__info,
  .viewer__hint {
    display: none;
  }

  .viewer__btn {
    padding: 0 8px;
    font-size: 0;
    gap: 0;
  }

  .viewer__stage {
    padding: 12px 8px;
  }

  .viewer__nav {
    display: none;
  }
}
</style>
