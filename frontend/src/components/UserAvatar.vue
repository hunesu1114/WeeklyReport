<script setup>
import { computed, ref, watch } from 'vue'
import { avatarUrl } from '@/api/client'

const props = defineProps({
  /** UserBrief(id) 든 MemberView(userId) 든 그대로 넘기면 된다 */
  user: { type: Object, default: null },
  size: { type: Number, default: 28 },
  title: { type: String, default: '' },
})

/** 사진이 깨져도 자리는 지켜야 한다. 실패하면 이니셜로 돌아간다. */
const broken = ref(false)
const src = computed(() => (broken.value ? null : avatarUrl(props.user)))

watch(() => avatarUrl(props.user), () => (broken.value = false))

const initial = computed(() => (props.user?.displayName || props.user?.username || '?').slice(0, 1))

const label = computed(
  () => props.title || props.user?.displayName || props.user?.username || '담당 없음',
)

const style = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  fontSize: `${Math.max(10, Math.round(props.size * 0.42))}px`,
}))
</script>

<template>
  <span class="avatar" :class="{ 'avatar--empty': !user }" :style="style" :title="label">
    <img v-if="src" :src="src" :alt="label" @error="broken = true" />
    <span v-else-if="user" aria-hidden="true">{{ initial }}</span>
    <span v-else aria-hidden="true">–</span>
  </span>
</template>

<style scoped>
.avatar {
  display: inline-grid;
  place-items: center;
  flex: none;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 50%;
  background: var(--brand-soft);
  color: var(--brand-strong);
  font-weight: 800;
  line-height: 1;
  user-select: none;
}

.avatar--empty {
  background: var(--bg-subtle);
  color: var(--text-4);
  border-style: dashed;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
</style>
