import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { initTheme } from './composables/useTheme'
import { SESSION_EXPIRED_MESSAGE, setUnauthorizedHandler } from './api/client'
import { muteErrors } from './composables/useToast'
import { useAuthStore } from './stores/auth'
import { datePicker } from './directives/datePicker'
import './assets/main.css'

// 첫 페인트 전에 테마를 적용해야 라이트 화면이 번쩍이지 않는다.
initTheme()

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)

// 세션이 끝나면 알리고 로그인으로 돌려보낸다.
// 라우터보다 먼저 심어야 첫 요청이 401 일 때도 동작한다.
setUnauthorizedHandler(() => {
  const auth = useAuthStore(pinia)
  const wasLoggedIn = auth.isLoggedIn

  // alert 보다 먼저 비운다. 한 화면에서 여러 요청이 동시에 실패해도
  // 두 번째부터는 wasLoggedIn 이 false 라 여기서 멈춘다 — 경고창이 겹치지 않는다.
  auth.logout()

  const current = router.currentRoute.value
  if (!wasLoggedIn || current.name === 'login') return

  // 조용히 로그인 화면으로 튕기면 사용자는 자기가 뭘 잘못했는지 모른다.
  // 확인을 누르기 전까지 화면이 멈춰 있어야 놓치지 않는다.
  window.alert(SESSION_EXPIRED_MESSAGE)

  // alert 가 닫힌 뒤 실패한 요청들의 catch 가 줄줄이 돌며 같은 말을 또 띄운다
  muteErrors()

  // replace 로 히스토리를 쌓지 않고, 중복 이동 거부는 조용히 넘긴다.
  router.replace({ name: 'login', query: { redirect: current.fullPath } }).catch(() => {})
})

// 날짜 입력은 칸 아무 데나 눌러도 달력이 열려야 한다
app.directive('date-picker', datePicker)

app.use(router).mount('#app')
