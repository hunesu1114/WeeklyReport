<script setup>
import { nextTick, ref, watch } from 'vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { teamApi } from '@/api/client'
import { activityText, activityTone, timeAgo } from '@/utils/team'

const props = defineProps({
  projectId: { type: Number, default: null },
})

const PAGE_SIZE = 20

const tab = ref('project')
const logs = ref([])
const page = ref(0)
const last = ref(true)
const loading = ref(false)
const error = ref('')
const collapsed = ref(true)
const root = ref(null)

/** 접혀 있는 동안에는 읽지 않는다. 보드를 옮길 때마다 쓸데없이 부르게 된다. */
watch([tab, () => props.projectId], () => {
  if (!collapsed.value) load(0)
  else logs.value = []
})

watch(collapsed, async (value) => {
  if (value) return

  // 기록이 다 들어온 뒤에 옮긴다. 불러오는 중일 때 옮기면 그때는 패널이
  // 한 줄짜리라, 목록이 채워지는 순간 다시 화면 밖으로 밀려난다.
  await load(0)
  await nextTick()

  // 펼쳤는데 화면 밖에 있으면 직접 내려가야 한다. 열었으면 보여주는 게 맞다.
  root.value?.scrollIntoView({
    behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
    block: 'end',
  })
})

async function load(next = 0) {
  if (tab.value === 'project' && !props.projectId) {
    logs.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    const result =
      tab.value === 'project'
        ? await teamApi.projectActivities(props.projectId, next, PAGE_SIZE)
        : await teamApi.myActivities(next, PAGE_SIZE)

    logs.value = next === 0 ? result.content : [...logs.value, ...result.content]
    page.value = result.number ?? next
    last.value = result.last ?? true
  } catch (e) {
    error.value = e.message
    if (next === 0) logs.value = []
  } finally {
    loading.value = false
  }
}

/** 보드에 변경이 생기면 바깥에서 부른다. 펼쳐 놓았을 때만 다시 읽는다. */
function reload() {
  if (!collapsed.value) load(0)
}

defineExpose({ reload })
</script>

<template>
  <section ref="root" class="card alog">
    <div class="card__head">
      <div>
        <h2 class="card__title">활동 기록</h2>
        <p class="tiny muted alog__hint">누가 무엇을 언제 바꿨는지 남습니다.</p>
      </div>

      <div class="alog__actions">
        <div v-if="!collapsed" class="alog__tabs" role="tablist">
          <button
            class="alog__tab"
            :class="{ 'alog__tab--on': tab === 'project' }"
            type="button"
            role="tab"
            :aria-selected="tab === 'project'"
            @click="tab = 'project'"
          >
            이 보드
          </button>
          <button
            class="alog__tab"
            :class="{ 'alog__tab--on': tab === 'all' }"
            type="button"
            role="tab"
            :aria-selected="tab === 'all'"
            @click="tab = 'all'"
          >
            전체
          </button>
        </div>
        <button class="btn btn--ghost btn--sm" type="button" @click="collapsed = !collapsed">
          {{ collapsed ? '펼치기' : '접기' }}
        </button>
      </div>
    </div>

    <div v-if="!collapsed" class="alog__body">
      <p v-if="error" class="alog__state alog__state--error tiny">{{ error }}</p>
      <p v-else-if="loading && !logs.length" class="alog__state muted tiny">불러오는 중…</p>
      <p v-else-if="!logs.length" class="alog__state muted tiny">아직 기록이 없습니다.</p>

      <ul v-else class="alog__list">
        <li v-for="log in logs" :key="log.id" class="alog__row">
          <UserAvatar :user="log.actor" :size="24" />
          <span class="alog__text" :class="`alog__text--${activityTone(log)}`">
            {{ activityText(log) }}
            <span v-if="log.detail" class="tiny muted">· {{ log.detail }}</span>
          </span>
          <span class="alog__when tiny muted">
            <template v-if="tab === 'all'">{{ log.projectName }} · </template>
            {{ timeAgo(log.createdAt) }}
          </span>
        </li>
      </ul>

      <button
        v-if="!last"
        class="btn btn--sm alog__more"
        type="button"
        :disabled="loading"
        @click="load(page + 1)"
      >
        {{ loading ? '불러오는 중…' : '더 보기' }}
      </button>
    </div>
  </section>
</template>

<style scoped>
.alog__hint {
  margin: 2px 0 0;
}

.alog__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.alog__tabs {
  display: flex;
  gap: 2px;
  padding: 2px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--surface-2);
}

.alog__tab {
  padding: 4px 10px;
  border: 0;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--text-3);
  font-size: 12px;
  font-weight: 600;
}

.alog__tab:hover {
  color: var(--text);
}

.alog__tab--on {
  background: var(--surface);
  color: var(--brand-strong);
  box-shadow: var(--shadow-1);
}

/*
 * 340px 로 박아 두면 큰 화면에서도 열 줄 남짓만 보인다. 화면을 따라가게 해서
 * 넓은 모니터에서는 한 번에 더 보이고, 작은 화면에서는 보드를 가리지 않게 한다.
 */
.alog__body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px 14px;
  max-height: clamp(260px, 52vh, 680px);
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  scrollbar-color: var(--line-strong) transparent;
}

.alog__body::-webkit-scrollbar {
  width: 8px;
}

.alog__body::-webkit-scrollbar-thumb {
  border: 2px solid transparent;
  border-radius: 999px;
  background: var(--line-strong);
  background-clip: content-box;
}

.alog__body::-webkit-scrollbar-track {
  background: transparent;
}

.alog__state {
  margin: 0;
  padding: 18px 6px;
  text-align: center;
}

.alog__state--error {
  color: var(--danger);
}

.alog__list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.alog__row {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 6px 7px;
  border-radius: var(--radius-xs);
}

.alog__row:hover {
  background: var(--surface-hover);
}

.alog__text {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.5;
  word-break: break-word;
}

.alog__text--ok {
  color: var(--brand-strong);
}
.alog__text--info {
  color: var(--text);
}
.alog__text--danger {
  color: var(--danger);
}
.alog__text--neutral {
  color: var(--text-2);
}

.alog__when {
  flex: none;
}

.alog__more {
  align-self: center;
}
</style>
