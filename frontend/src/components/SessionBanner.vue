<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'

/** 이만큼 남았을 때부터 알린다. 저장할 시간은 되면서, 너무 일찍 성가시지도 않은 지점. */
const WARN_MS = 10 * 60 * 1000

const auth = useAuthStore()
const toast = useToast()

const now = ref(Date.now())
const extending = ref(false)
let timer = null

onMounted(() => {
  // 1초마다 본다. 마지막 1분은 초 단위로 줄어드는 게 보여야 한다.
  timer = setInterval(() => (now.value = Date.now()), 1000)
})

onBeforeUnmount(() => clearInterval(timer))

const remaining = computed(() => {
  if (!auth.isLoggedIn || !auth.expiresAt) return null
  return auth.expiresAt - now.value
})

const show = computed(() => remaining.value !== null && remaining.value > 0 && remaining.value <= WARN_MS)

/** 1분이 넘으면 분만, 그 아래로는 초까지. 마지막에 숫자가 멈춰 있으면 불안하다. */
const label = computed(() => {
  const left = remaining.value ?? 0
  const seconds = Math.ceil(left / 1000)
  if (seconds >= 60) return `${Math.ceil(seconds / 60)}분`
  return `${seconds}초`
})

const urgent = computed(() => (remaining.value ?? 0) <= 60 * 1000)

async function extend() {
  extending.value = true
  try {
    await auth.extend()
    toast.success('로그인 세션을 연장했습니다.')
  } catch (error) {
    // 이미 만료됐다면 client.js 가 로그인 화면으로 보낸다. 여기선 알리기만 한다.
    if (!error.sessionExpired) toast.error(`연장하지 못했습니다. ${error.message}`)
  } finally {
    extending.value = false
  }
}
</script>

<template>
  <div v-if="show" class="session" :class="{ 'session--urgent': urgent }" role="status">
    <span class="session__dot" aria-hidden="true"></span>
    <span class="session__text">
      로그인 세션이 <strong class="num">{{ label }}</strong> 뒤에 만료됩니다.
      <span class="session__hint">지금 연장하지 않으면 작성 중인 내용을 잃을 수 있습니다.</span>
    </span>
    <button class="btn btn--sm btn--primary" type="button" :disabled="extending" @click="extend">
      <span v-if="extending" class="spinner"></span>
      세션 연장
    </button>
  </div>
</template>

<style scoped>
.session {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 8px 20px;
  border-bottom: 1px solid var(--warn-border);
  background: var(--warn-soft);
  color: var(--warn);
  font-size: var(--fs-sm);
  font-weight: 600;
}

/* 남은 시간이 1분 아래로 내려가면 색을 바꿔 눈에 띄게 한다 */
.session--urgent {
  border-bottom-color: var(--danger-border);
  background: var(--danger-soft);
  color: var(--danger);
}

.session__dot {
  flex: none;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
}

.session--urgent .session__dot {
  animation: session-pulse 1s ease-in-out infinite;
}

.session__text {
  flex: 1;
  min-width: 0;
}

.session__hint {
  color: var(--text-3);
  font-weight: 400;
}

@keyframes session-pulse {
  50% {
    opacity: 0.25;
  }
}

@media (prefers-reduced-motion: reduce) {
  .session--urgent .session__dot {
    animation: none;
  }
}

@media (max-width: 720px) {
  .session {
    padding: 8px 12px;
  }

  .session__hint {
    display: none;
  }
}
</style>
