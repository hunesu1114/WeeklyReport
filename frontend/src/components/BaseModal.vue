<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' },
  width: { type: String, default: '560px' },
})
const emit = defineEmits(['close'])

const panel = ref(null)

function onKeydown(event) {
  if (event.key === 'Escape') {
    event.stopPropagation()
    emit('close')
  }
}

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  // 배경 스크롤이 같이 움직이면 모달이 떠 있는 느낌이 깨진다
  document.body.style.overflow = 'hidden'
  panel.value?.querySelector('input, textarea, select, button')?.focus()
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <div class="modal" @mousedown.self="emit('close')">
      <div ref="panel" class="modal__panel" :style="{ maxWidth: props.width }" role="dialog" aria-modal="true">
        <header class="modal__head">
          <div>
            <h2 class="modal__title">{{ title }}</h2>
            <p v-if="subtitle" class="modal__subtitle tiny muted">{{ subtitle }}</p>
          </div>
          <button class="btn btn--ghost btn--icon" type="button" aria-label="닫기" @click="emit('close')">
            ✕
          </button>
        </header>

        <div class="modal__body">
          <slot />
        </div>

        <footer v-if="$slots.footer" class="modal__foot">
          <slot name="footer" />
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 6vh 16px 16px;
  background: rgba(9, 11, 15, 0.45);
  backdrop-filter: blur(2px);
  overflow-y: auto;
  animation: modal-fade 0.14s ease-out;
}

.modal__panel {
  width: 100%;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow-3);
  animation: modal-rise 0.16s ease-out;
}

.modal__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--line);
}

.modal__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.modal__subtitle {
  margin: 2px 0 0;
}

.modal__body {
  padding: 18px;
}

.modal__foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 14px 18px;
  border-top: 1px solid var(--line);
  background: var(--surface-2);
  border-radius: 0 0 var(--radius) var(--radius);
}

@keyframes modal-fade {
  from {
    opacity: 0;
  }
}

@keyframes modal-rise {
  from {
    transform: translateY(-8px);
    opacity: 0;
  }
}
</style>
