import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { initTheme } from './composables/useTheme'
import './assets/main.css'

// 첫 페인트 전에 테마를 적용해야 라이트 화면이 번쩍이지 않는다.
initTheme()

createApp(App).use(createPinia()).use(router).mount('#app')
