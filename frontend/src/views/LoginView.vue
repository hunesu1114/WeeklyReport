<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BrandMark from '@/components/BrandMark.vue'
import { authApi } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const toast = useToast()

/** 가입한 사람이 아무도 없으면 '첫 계정 만들기'로 시작한다. */
const hasAnyUser = ref(true)
const mode = ref('login') // 'login' | 'register'
const busy = ref(false)
const error = ref('')

const form = reactive({ username: '', password: '', passwordConfirm: '', displayName: '' })

const isRegister = computed(() => mode.value === 'register')

/** 확인란을 치는 도중에는 조용하고, 다 치고 나서 다르면 그때 알려준다. */
const mismatch = computed(
  () =>
    isRegister.value &&
    form.passwordConfirm.length > 0 &&
    form.password !== form.passwordConfirm,
)

/** 모드를 오갈 때 비밀번호는 남기지 않는다. */
function switchMode(next) {
  mode.value = next
  error.value = ''
  form.password = ''
  form.passwordConfirm = ''
}
const title = computed(() => {
  if (!hasAnyUser.value) return '첫 계정 만들기'
  return isRegister.value ? '계정 만들기' : '로그인'
})

onMounted(async () => {
  try {
    const state = await authApi.setupState()
    hasAnyUser.value = state.hasAnyUser
    if (!state.hasAnyUser) mode.value = 'register'
  } catch {
    // 서버가 아직 안 떴을 수 있다. 로그인 화면은 그대로 보여준다.
  }
})

async function submit() {
  if (!form.username.trim() || !form.password) {
    error.value = '아이디와 비밀번호를 입력해주세요.'
    return
  }
  if (isRegister.value && form.password.length < 8) {
    error.value = '비밀번호는 8자 이상이어야 합니다.'
    return
  }
  if (isRegister.value && form.password !== form.passwordConfirm) {
    error.value = '비밀번호가 서로 다릅니다.'
    return
  }

  busy.value = true
  error.value = ''
  try {
    const payload = {
      username: form.username.trim(),
      password: form.password,
      ...(isRegister.value ? { displayName: form.displayName.trim() || undefined } : {}),
    }
    const user = isRegister.value ? await auth.register(payload) : await auth.login(payload)
    toast.success(`${user.displayName}님, 반갑습니다.`)
    await router.replace(route.query.redirect || '/')
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="login">
    <div class="login__card card">
      <div class="login__brand">
        <BrandMark :size="40" />
        <div>
          <h1>{{ title }}</h1>
          <p class="tiny muted">주간보고/칸반</p>
        </div>
      </div>

      <p v-if="!hasAnyUser" class="login__notice tiny">
        아직 계정이 없습니다. 지금 만드는 첫 계정이 <strong>관리자</strong>가 되고,
        로그인 이전에 쌓인 데이터를 가져올 수 있습니다.
      </p>

      <form class="login__form" @submit.prevent="submit">
        <div class="field">
          <label for="login-username">아이디</label>
          <input
            id="login-username"
            v-model="form.username"
            class="control"
            type="text"
            autocomplete="username"
          />
        </div>

        <div v-if="isRegister" class="field">
          <label for="login-display">표시 이름 <span class="muted">(선택)</span></label>
          <input
            id="login-display"
            v-model="form.displayName"
            class="control"
            type="text"
            placeholder="비우면 아이디를 씁니다"
          />
        </div>

        <div class="field">
          <label for="login-password">비밀번호</label>
          <input
            id="login-password"
            v-model="form.password"
            class="control"
            type="password"
            :autocomplete="isRegister ? 'new-password' : 'current-password'"
            :placeholder="isRegister ? '8자 이상' : ''"
          />
        </div>

        <div v-if="isRegister" class="field">
          <label for="login-password2">비밀번호 확인</label>
          <input
            id="login-password2"
            v-model="form.passwordConfirm"
            class="control"
            :class="{ 'control--bad': mismatch }"
            type="password"
            autocomplete="new-password"
            @keydown.enter="submit"
          />
          <span v-if="mismatch" class="tiny login__mismatch">비밀번호가 서로 다릅니다.</span>
          <span
            v-else-if="form.passwordConfirm && form.password === form.passwordConfirm"
            class="tiny login__match"
          >
            비밀번호가 일치합니다.
          </span>
        </div>

        <p v-if="error" class="login__error tiny">{{ error }}</p>

        <button class="btn btn--primary login__submit" type="submit" :disabled="busy || mismatch">
          <span v-if="busy" class="spinner"></span>
          {{ isRegister ? '계정 만들기' : '로그인' }}
        </button>
      </form>

      <p v-if="hasAnyUser" class="login__switch tiny muted">
        <template v-if="isRegister">
          이미 계정이 있나요?
          <button class="linklike" type="button" @click="switchMode('login')">로그인</button>
        </template>
        <template v-else>
          계정이 없나요?
          <button class="linklike" type="button" @click="switchMode('register')">
            계정 만들기
          </button>
        </template>
      </p>
    </div>
  </div>
</template>

<style scoped>
.login {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 80px);
  padding: 20px;
}

.login__card {
  width: 100%;
  max-width: 400px;
  padding: 28px;
}

.login__brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.login__brand h1 {
  margin: 0;
  font-size: 19px;
  letter-spacing: -0.02em;
}

.login__brand p {
  margin: 1px 0 0;
}

.login__notice {
  margin: 0 0 16px;
  padding: 10px 12px;
  border: 1px solid var(--brand-border);
  border-radius: var(--radius-sm);
  background: var(--brand-soft);
  color: var(--brand-strong);
  line-height: 1.6;
}

.login__form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.login__submit {
  margin-top: 4px;
  padding: 10px;
}

.login__error {
  margin: 0;
  padding: 8px 10px;
  border-radius: var(--radius-xs);
  background: var(--danger-soft);
  color: var(--danger);
  font-weight: 600;
}

.login__mismatch {
  color: var(--danger);
  font-weight: 600;
}

.login__match {
  color: var(--brand-strong);
  font-weight: 600;
}

.login__switch {
  margin: 16px 0 0;
  text-align: center;
}

.linklike {
  border: 0;
  background: none;
  padding: 0;
  color: var(--brand-strong);
  font-weight: 700;
  text-decoration: underline;
  font-size: inherit;
}
</style>
