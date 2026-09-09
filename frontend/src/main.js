import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { initTheme } from './composables/useTheme'
import { setUnauthorizedHandler } from './api/client'
import { useAuthStore } from './stores/auth'
import { datePicker } from './directives/datePicker'
import './assets/main.css'

// 첫 페인트 전에 테마를 적용해야 라이트 화면이 번쩍이지 않는다.
initTheme()

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)

// 토큰이 만료되면 어느 화면에 있든 로그인으로 돌려보낸다.
// 라우터보다 먼저 심어야 첫 요청이 401 일 때도 동작한다.
setUnauthorizedHandler(() => {
  const auth = useAuthStore(pinia)
  const wasLoggedIn = auth.isLoggedIn
  auth.logout()

  const current = router.currentRoute.value
  if (!wasLoggedIn || current.name === 'login') return

  // 한 화면에서 여러 요청이 동시에 401 이 되면 같은 곳으로 여러 번 이동하게 된다.
  // replace 로 히스토리를 쌓지 않고, 중복 이동 거부는 조용히 넘긴다.
  router.replace({ name: 'login', query: { redirect: current.fullPath } }).catch(() => {})
})

// 날짜 입력은 칸 아무 데나 눌러도 달력이 열려야 한다
app.directive('date-picker', datePicker)

app.use(router).mount('#app')
