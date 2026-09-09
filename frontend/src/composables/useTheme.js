import { computed, ref } from 'vue'

const STORAGE_KEY = 'weekly-report:theme'
const MODES = ['light', 'dark', 'system']

/**
 * 'system' 은 html 에 data-theme 를 붙이지 않는다.
 * 그러면 CSS 의 prefers-color-scheme 규칙이 그대로 동작한다.
 */
const mode = ref(readStored())

function readStored() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    return MODES.includes(saved) ? saved : 'system'
  } catch {
    return 'system'
  }
}

function systemPrefersDark() {
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
}

function paint() {
  const root = document.documentElement
  if (mode.value === 'system') {
    root.removeAttribute('data-theme')
  } else {
    root.setAttribute('data-theme', mode.value)
  }
}

/** 테마를 바꾸는 순간 개별 트랜지션이 제각각 돌면 지저분하다. 잠깐 꺼둔다. */
function paintWithoutFlicker() {
  const root = document.documentElement
  root.classList.add('theme-switching')
  paint()
  window.setTimeout(() => root.classList.remove('theme-switching'), 60)
}

export function initTheme() {
  paint()
  // 'system' 인 동안에는 OS 설정을 따라 즉시 바뀌어야 한다.
  window.matchMedia?.('(prefers-color-scheme: dark)').addEventListener?.('change', () => {
    if (mode.value === 'system') paint()
  })
}

export function useTheme() {
  const isDark = computed(() =>
    mode.value === 'dark' || (mode.value === 'system' && systemPrefersDark()),
  )

  function setMode(next) {
    if (!MODES.includes(next)) return
    mode.value = next
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      /* 저장이 막혀 있어도 이번 세션은 동작해야 한다 */
    }
    paintWithoutFlicker()
  }

  /** 지금 보이는 것의 반대로. 'system' 이었다면 그 결과의 반대를 고정한다. */
  function toggle() {
    setMode(isDark.value ? 'light' : 'dark')
  }

  return { mode, isDark, setMode, toggle, MODES }
}
