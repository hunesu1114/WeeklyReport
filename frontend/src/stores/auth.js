import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi, setAuthToken } from '@/api/client'
import { expiresAt as tokenExpiresAt } from '@/utils/jwt'

const TOKEN_KEY = 'weekly-report:token'
const USER_KEY = 'weekly-report:user'

function readStored(key) {
  try {
    const raw = localStorage.getItem(key)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function write(key, value) {
  try {
    if (value === null) localStorage.removeItem(key)
    else localStorage.setItem(key, JSON.stringify(value))
  } catch {
    /* 저장이 막혀 있어도 이번 세션은 동작해야 한다 */
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(readStored(TOKEN_KEY))
  const user = ref(readStored(USER_KEY))

  const isLoggedIn = computed(() => Boolean(token.value))
  /** 이 세션이 끝나는 시각(ms). 알 수 없으면 null. */
  const expiresAt = computed(() => (token.value ? tokenExpiresAt(token.value) : null))
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  // 새로고침 직후에도 요청에 토큰이 실리도록 즉시 반영한다
  setAuthToken(token.value)

  function apply({ token: nextToken, user: nextUser }) {
    token.value = nextToken
    user.value = nextUser
    write(TOKEN_KEY, nextToken)
    write(USER_KEY, nextUser)
    setAuthToken(nextToken)
  }

  /**
   * 사용자 정보만 갈아끼운다. 마이페이지에서 이름·사진을 바꾸면
   * 토큰은 그대로 두고 화면에 보이는 값만 따라가야 한다.
   */
  function applyUser(next) {
    user.value = next
    write(USER_KEY, next)
  }

  async function login(payload) {
    apply(await authApi.login(payload))
    return user.value
  }

  async function register(payload) {
    apply(await authApi.register(payload))
    return user.value
  }

  /**
   * 세션을 연장한다. 서버가 토큰을 새로 끊어 준다.
   *
   * 자동으로 부르지 않는다. 열어만 둔 탭이 영원히 로그인 상태로 남으면
   * 만료 시간을 두는 의미가 없다. 사람이 버튼을 눌렀을 때만 연장한다.
   */
  async function extend() {
    apply(await authApi.refresh())
    return expiresAt.value
  }

  function logout() {
    token.value = null
    user.value = null
    write(TOKEN_KEY, null)
    write(USER_KEY, null)
    setAuthToken(null)
  }

  /** 토큰이 아직 살아 있는지 확인하고 사용자 정보를 최신으로 맞춘다. */
  async function refreshMe() {
    if (!token.value) return null
    try {
      user.value = await authApi.me()
      write(USER_KEY, user.value)
      return user.value
    } catch {
      // 만료됐거나 계정이 사라졌다
      logout()
      return null
    }
  }

  return {
    token,
    user,
    isLoggedIn,
    isAdmin,
    expiresAt,
    applyUser,
    extend,
    login,
    register,
    logout,
    refreshMe,
  }
})
