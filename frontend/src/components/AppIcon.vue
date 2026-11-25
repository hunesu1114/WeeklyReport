<script setup>
/**
 * 선 아이콘. 16px 격자, 선 1.5px, 글자색을 따라간다.
 *
 * 이모지를 쓰지 않는 이유: OS 마다 모양과 색이 달라서, 같은 화면이 윈도우와
 * 맥에서 다른 앱처럼 보이고 다크 모드에서 혼자 튄다. 여기 없는 그림이 필요하면
 * 같은 격자·굵기로 PATHS 에 더한다.
 */
import { computed } from 'vue'

const props = defineProps({
  name: { type: String, required: true },
  size: { type: Number, default: 16 },
  /** 채운 모양(별·핀이 켜졌을 때) */
  filled: { type: Boolean, default: false },
  /** 접근성 이름. 비우면 장식으로 보고 읽지 않는다 */
  label: { type: String, default: '' },
})

/* 점 세 개처럼 선이 아니라 면으로 그리는 것 */
const SOLID = new Set(['more', 'grip'])

const PATHS = {
  search: ['M10.25 10.25 13.5 13.5', 'circle:7,7,4.25'],
  folder: ['M2 4.5c0-.55.45-1 1-1h3l1.4 1.5H13c.55 0 1 .45 1 1v6.5c0 .55-.45 1-1 1H3c-.55 0-1-.45-1-1z'],
  notes: ['rect:2.5,2,11,12,1.5', 'M5.5 5.5h5M5.5 8h5M5.5 10.5h3'],
  star: ['M8 2.2l1.75 3.6 3.95.55-2.86 2.8.68 3.95L8 11.25 4.48 13.1l.68-3.95-2.86-2.8 3.95-.55z'],
  pin: ['M9.75 2.25l4 4-1.6.55-2.4 2.4.25 2.55-1.2 1.2-6.75-6.75 1.2-1.2 2.55.25 2.4-2.4z', 'open:M5.25 10.75 2.25 13.75'],
  trash: ['M2.75 4.25h10.5M6.25 4.25v-1.5h3.5v1.5M4.25 4.25l.6 9h6.3l.6-9'],
  inbox: ['M2 9.5V12c0 .55.45 1 1 1h10c.55 0 1-.45 1-1V9.5M2 9.5 4 3.5h8l2 6M2 9.5h3.5l1 1.5h3l1-1.5H14'],
  keyboard: ['rect:1.75,4,12.5,8,1.5', 'M4.5 6.75h.01M7 6.75h.01M9.5 6.75h.01M12 6.75h.01M5 9.5h6'],
  plus: ['M8 3.5v9M3.5 8h9'],
  minus: ['M3.5 8h9'],
  close: ['M4.5 4.5l7 7M11.5 4.5l-7 7'],
  check: ['M3.5 8.5 6.5 11.5 12.5 4.5'],
  more: ['circle:3.5,8,1.15', 'circle:8,8,1.15', 'circle:12.5,8,1.15'],
  'chevron-right': ['M6 4l4 4-4 4'],
  'chevron-down': ['M4 6l4 4 4-4'],
  'chevron-left': ['M10 4 6 8l4 4'],
  'arrow-up': ['M8 12.5v-9M4.5 7 8 3.5 11.5 7'],
  'arrow-down': ['M8 3.5v9M4.5 9 8 12.5 11.5 9'],
  'arrow-left': ['M12.5 8h-9M7 4.5 3.5 8 7 11.5'],
  external: ['M5 11 11 5M6 5h5v5'],
  download: ['M8 2.5v8M4.75 7.25 8 10.5l3.25-3.25M2.75 13.25h10.5'],
  sort: ['M4.5 3v10M2.5 11l2 2 2-2M11.5 13V3M9.5 5l2-2 2 2'],
  filter: ['M2 3.5h12M4.5 8h7M7 12.5h2'],
  bell: ['M4 6.75a4 4 0 0 1 8 0v3l1.25 2H2.75L4 9.75z', 'M6.5 13.75h3'],
  moon: ['M13.25 9.6A5.5 5.5 0 0 1 6.4 2.75a5.5 5.5 0 1 0 6.85 6.85z'],
  sun: ['circle:8,8,2.75', 'M8 1.75v1.5M8 12.75v1.5M1.75 8h1.5M12.75 8h1.5M3.6 3.6l1.05 1.05M11.35 11.35l1.05 1.05M3.6 12.4l1.05-1.05M11.35 4.65l1.05-1.05'],
  calendar: ['rect:2,3,12,11,1.5', 'M2 6.5h12M5.5 1.5v3M10.5 1.5v3'],
  pencil: ['M10.5 2.75l2.75 2.75L5.75 13H3v-2.75z', 'M9 4.25 11.75 7'],
  restore: ['M5.5 4 2.5 7l3 3', 'open:M2.5 7h7a3.5 3.5 0 0 1 0 7H7'],
  type: ['M1.75 12.5 5.25 3.5l3.5 9M3 9.5h4.5', 'open:M10.25 7.6c.45-.4 1-.6 1.6-.6 1 0 1.6.55 1.6 1.5v4M13.45 10.1c-.4-.15-.9-.25-1.4-.25-1.05 0-1.75.55-1.75 1.35s.6 1.3 1.4 1.3c.75 0 1.35-.35 1.75-.9'],
  copy: ['rect:5.5,5.5,8,8,1.25', 'M10.5 5.5V3.5c0-.55-.45-1-1-1h-6c-.55 0-1 .45-1 1v6c0 .55.45 1 1 1h2'],
  grip: ['circle:6,4,1', 'circle:10,4,1', 'circle:6,8,1', 'circle:10,8,1', 'circle:6,12,1', 'circle:10,12,1'],
  refresh: ['M13 8a5 5 0 1 1-1.46-3.54', 'M13 2.75v2.5h-2.5'],
  'corner-down-right': ['M4 3v5.5c0 .55.45 1 1 1h7.5', 'M10 7l2.5 2.5L10 12'],
  file: ['M9.5 1.75H4.25c-.55 0-1 .45-1 1v10.5c0 .55.45 1 1 1h7.5c.55 0 1-.45 1-1V5z', 'M9.5 1.75V5h3.25'],
  image: ['rect:2,3,12,10,1.5', 'circle:5.75,6.5,1.1', 'open:M2.5 11.75 6 8.5l2.25 2 2-1.75 3.25 3'],
  users: ['circle:6,5.5,2.5', 'M1.75 13.5c.5-2.25 2.25-3.5 4.25-3.5s3.75 1.25 4.25 3.5', 'M10.5 3.25a2.5 2.5 0 0 1 0 4.75M12 10.25c1.15.5 2 1.6 2.25 3.25'],
}

/* 'circle:cx,cy,r' · 'rect:x,y,w,h,rx' · 'open:d'(채우지 않는 선) · 그 밖은 path d */
const shapes = computed(() =>
  (PATHS[props.name] ?? []).map((spec) => {
    const [kind, rest] = spec.includes(':') && !spec.startsWith('M') ? spec.split(':') : ['path', spec]
    if (kind === 'circle') {
      const [cx, cy, r] = rest.split(',')
      return { tag: 'circle', attrs: { cx, cy, r } }
    }
    if (kind === 'rect') {
      const [x, y, width, height, rx] = rest.split(',')
      return { tag: 'rect', attrs: { x, y, width, height, rx } }
    }
    if (kind === 'open') return { tag: 'path', attrs: { d: rest, fill: 'none' } }
    return { tag: 'path', attrs: { d: rest } }
  }),
)

const solid = computed(() => SOLID.has(props.name))
</script>

<template>
  <svg
    class="icon"
    :width="size"
    :height="size"
    viewBox="0 0 16 16"
    :fill="solid || filled ? 'currentColor' : 'none'"
    :stroke="solid ? 'none' : 'currentColor'"
    stroke-width="1.5"
    stroke-linecap="round"
    stroke-linejoin="round"
    :role="label ? 'img' : undefined"
    :aria-label="label || undefined"
    :aria-hidden="label ? undefined : 'true'"
    focusable="false"
  >
    <component :is="shape.tag" v-for="(shape, index) in shapes" :key="index" v-bind="shape.attrs" />
  </svg>
</template>

<style scoped>
.icon {
  display: block;
  flex: none;
}
</style>
