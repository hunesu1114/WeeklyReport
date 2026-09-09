<script setup>
import { computed, onMounted, ref } from 'vue'
import { authApi } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'

const emit = defineEmits(['claimed'])

const auth = useAuthStore()
const toast = useToast()

const summary = ref(null)
const busy = ref(false)
const dismissed = ref(false)

/** 관리자에게만, 그리고 가져올 것이 실제로 있을 때만 보여준다. */
const visible = computed(
  () =>
    auth.isAdmin &&
    !dismissed.value &&
    summary.value &&
    (summary.value.reports > 0 || summary.value.projects > 0),
)

/** "보고서 2건, 프로젝트 1개(카드 5장)" 처럼 있는 것만 이어 붙인다. */
const orphanText = computed(() => {
  const parts = []
  if (summary.value?.reports) parts.push(`보고서 ${summary.value.reports}건`)
  if (summary.value?.projects) {
    parts.push(`프로젝트 ${summary.value.projects}개(카드 ${summary.value.cards}장)`)
  }
  return parts.join(', ')
})

onMounted(load)

async function load() {
  if (!auth.isAdmin) return
  try {
    summary.value = await authApi.orphans()
  } catch {
    // 부가 안내다. 실패해도 화면이 막히면 안 된다.
  }
}

async function claim() {
  busy.value = true
  try {
    const result = await authApi.claimOrphans()
    toast.success(result.message)
    summary.value = await authApi.orphans()
    emit('claimed', result)
  } catch (e) {
    toast.error(e.message)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <aside v-if="visible" class="orphan">
    <div class="orphan__text">
      <strong>주인 없는 데이터가 있습니다.</strong>
      <span class="tiny">
        로그인 도입 이전에 쌓인 <b>{{ orphanText }}</b>을(를) 지금 계정으로 가져올 수 있습니다.
        가져오기 전에는 목록에 보이지 않습니다.
      </span>
    </div>
    <div class="orphan__actions">
      <button class="btn btn--sm" type="button" @click="dismissed = true">나중에</button>
      <button class="btn btn--sm btn--primary" type="button" :disabled="busy" @click="claim">
        <span v-if="busy" class="spinner"></span>
        내 계정으로 가져오기
      </button>
    </div>
  </aside>
</template>

<style scoped>
.orphan {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 13px 16px;
  border: 1px solid var(--brand-border);
  border-left: 3px solid var(--brand);
  border-radius: var(--radius);
  background: var(--brand-soft);
}

.orphan__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.orphan__text strong {
  color: var(--brand-strong);
}

.orphan__text span {
  color: var(--text-2);
  line-height: 1.6;
}

.orphan__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
