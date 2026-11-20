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
  function push(message, tone = 'info', timeout = 3200, action = null) {
    const id = ++seq
    toasts.value.push({ id, message, tone, action })
    setTimeout(() => dismiss(id), timeout)
    return id
  }

  function dismiss(id) {
    toasts.value = toasts.value.filter((t) => t.id !== id)
  }

  /** 토스트 안의 버튼을 눌렀다. 한 번 누르면 할 일이 끝났으니 닫는다. */
  function act(toast) {
    dismiss(toast.id)
    toast.action?.run()
  }

  return {
    toasts,
    dismiss,
    act,
    success: (message) => push(message, 'success'),
    error: (message) => {
      if (Date.now() < muteErrorsUntil) return
      push(message, 'error', 5200)
    },
    info: (message) => push(message, 'info'),
    /**
     * 되돌릴 수 있는 일을 했다고 알린다. 버튼을 누를 시간이 필요하니 오래 띄운다.
     * action = { label: '되돌리기', run: () => ... }
     */
    undoable: (message, action) => push(message, 'info', 7000, action),
  }
}
