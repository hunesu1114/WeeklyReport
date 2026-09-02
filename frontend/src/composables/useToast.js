import { ref } from 'vue'

const toasts = ref([])
let seq = 0

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
    error: (message) => push(message, 'error', 5200),
    info: (message) => push(message, 'info'),
  }
}
