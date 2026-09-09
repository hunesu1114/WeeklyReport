<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useKanbanStore } from '@/stores/kanban'
import { PRIORITY_META, dueLabel, dueTone } from '@/utils/kanban'

const store = useKanbanStore()
const router = useRouter()

const open = ref(false)
const root = ref(null)

const cards = computed(() => store.dueSoon)
const count = computed(() => store.dueSoonCount)
const overdue = computed(() => store.overdueCount)

function onDocClick(event) {
  if (open.value && root.value && !root.value.contains(event.target)) {
    open.value = false
  }
}

onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))

function goTo(card) {
  open.value = false
  router.push({ name: 'kanban-board', params: { projectId: card.projectId } })
}
</script>

<template>
  <div ref="root" class="bell">
    <button
      class="bell__btn"
      :class="{ 'bell__btn--alert': count > 0, 'bell__btn--overdue': overdue > 0 }"
      type="button"
      :title="count ? `완료일 임박 ${count}건` : '임박한 카드가 없습니다'"
      :aria-expanded="open"
      @click="open = !open"
    >
      <span aria-hidden="true">🔔</span>
      <span v-if="count" class="bell__count">{{ count }}</span>
      <span class="sr-only">완료일 임박 카드 {{ count }}건</span>
    </button>

    <div v-if="open" class="pop">
      <header class="pop__head">
        <strong>완료일 임박</strong>
        <span class="tiny muted">3일 이내 · 지난 것 포함</span>
      </header>

      <p v-if="!cards.length" class="pop__empty muted tiny">
        임박한 카드가 없습니다. 여유 있네요.
      </p>

      <ul v-else class="pop__list">
        <li v-for="card in cards" :key="card.id">
          <button class="pop__item" type="button" @click="goTo(card)">
            <span class="pop__row">
              <span class="pop__title">{{ card.title }}</span>
              <span class="pop__due" :class="`pop__due--${dueTone(card.daysUntilDue)}`">
                {{ dueLabel(card.daysUntilDue) }}
              </span>
            </span>
            <span class="pop__meta tiny muted">
              {{ card.projectName }} · {{ card.status }} ·
              {{ PRIORITY_META[card.priority]?.label }}
            </span>
          </button>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.bell {
  position: relative;
}

.bell__btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--surface);
  transition: border-color 0.12s, background 0.12s;
}

.bell__btn:hover {
  background: var(--surface-hover);
  border-color: var(--line-strong);
}

.bell__btn--alert {
  border-color: var(--warn-border);
  background: var(--warn-soft);
}

.bell__btn--overdue {
  border-color: var(--danger-border);
  background: var(--danger-soft);
}

.bell__count {
  position: absolute;
  top: -6px;
  right: -6px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--danger-bright);
  color: #fff;
  font-size: 11px;
  font-weight: 800;
  line-height: 18px;
}

.pop {
  position: absolute;
  right: 0;
  top: calc(100% + 8px);
  z-index: 50;
  width: 340px;
  max-height: 60vh;
  overflow-y: auto;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: var(--shadow-3);
  animation: pop-in 0.13s ease-out;
}

.pop__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--line);
}

.pop__empty {
  margin: 0;
  padding: 22px 14px;
  text-align: center;
}

.pop__list {
  margin: 0;
  padding: 6px;
  list-style: none;
}

.pop__item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  width: 100%;
  padding: 9px 10px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  text-align: left;
}

.pop__item:hover {
  background: var(--surface-hover);
}

.pop__row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.pop__title {
  font-weight: 700;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pop__due {
  flex: none;
  font-size: 11px;
  font-weight: 800;
}

.pop__due--warn {
  color: var(--warn);
}

.pop__due--danger {
  color: var(--danger);
}

.pop__due--neutral {
  color: var(--text-3);
}

@keyframes pop-in {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
}
</style>
