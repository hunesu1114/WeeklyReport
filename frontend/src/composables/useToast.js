import { ref } from 'vue'

const toasts = ref([])
let seq = 0

/**
 * 세션 만료처럼 이미 alert 로 알린 일은 토스트까지 띄우면 중복이다.
 * alert 는 스레드를 멈추므로, 닫힌 뒤에야 실패한 요청들의 catch 가 줄줄이 돈다.
 * 그 순간만 잠깐 막는다.
 */
let muteErrorsUntil = 0

export function muteErrors(ms = 1000) {
  muteErrorsUntil = Date.now() + ms
}

export function useToast() {
  function push(message, tone = 'info', timeout = 3200) {
    const id = ++seq
    toasts.value.push({ id, message, tone })
    setTimeout(() => dismiss(id), timeout)
  }

  function dismiss(id) {
    toasts.value = toasts.value.filter((t) => t.id !== id)
  }

  return {
    toasts,
    dismiss,
    success: (message) => push(message, 'success'),
    error: (message) => {
      if (Date.now() < muteErrorsUntil) return
      push(message, 'error', 5200)
    },
    info: (message) => push(message, 'info'),
  }
}
