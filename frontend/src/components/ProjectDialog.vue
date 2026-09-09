<script setup>
import { computed, reactive, ref } from 'vue'
import BaseModal from '@/components/BaseModal.vue'

const props = defineProps({
  project: { type: Object, required: true },
  saving: { type: Boolean, default: false },
})
const emit = defineEmits(['save', 'remove', 'close'])

const PRESET_COLORS = ['#22c55e', '#2563eb', '#7c3aed', '#f59e0b', '#ef4444', '#0ea5e9', '#64748b']

const form = reactive({ ...props.project })
const error = ref('')

const isNew = computed(() => !form.id)

function submit() {
  if (!form.name.trim()) {
    error.value = '프로젝트 이름은 반드시 입력해야 합니다.'
    return
  }
  error.value = ''
  emit('save', { ...form })
}
</script>

<template>
  <BaseModal
    :title="isNew ? '새 프로젝트' : '프로젝트 설정'"
    subtitle="프로젝트 하나가 칸반 보드 하나를 갖습니다"
    width="520px"
    @close="emit('close')"
  >
    <form class="pform" @submit.prevent="submit">
      <div class="field">
        <label for="project-name">이름</label>
        <input
          id="project-name"
          v-model="form.name"
          class="control"
          type="text"
          placeholder="예: 42dot 마이그레이션"
          maxlength="100"
        />
      </div>

      <div class="field">
        <label for="project-desc">설명</label>
        <input
          id="project-desc"
          v-model="form.description"
          class="control"
          type="text"
          placeholder="이 보드가 다루는 범위"
          maxlength="500"
        />
      </div>

      <div class="field">
        <span class="field-label">색</span>
        <div class="swatches">
          <button
            v-for="color in PRESET_COLORS"
            :key="color"
            class="swatch"
            :class="{ 'swatch--on': form.color === color }"
            :style="{ background: color }"
            type="button"
            :aria-label="color"
            :aria-pressed="form.color === color"
            @click="form.color = color"
          ></button>
        </div>
      </div>

      <label v-if="!isNew" class="pform__check">
        <input v-model="form.active" type="checkbox" />
        <span>
          활성
          <em class="tiny muted">끄면 보드 선택 목록에서 숨겨집니다. 카드는 지워지지 않습니다.</em>
        </span>
      </label>

      <p v-if="error" class="pform__error tiny">{{ error }}</p>
    </form>

    <template #footer>
      <button
        v-if="!isNew"
        class="btn btn--danger"
        type="button"
        :disabled="saving"
        title="이 보드의 카드도 함께 삭제됩니다"
        @click="emit('remove', form)"
      >
        삭제
      </button>
      <span class="pform__spacer"></span>
      <button class="btn" type="button" :disabled="saving" @click="emit('close')">취소</button>
      <button class="btn btn--primary" type="button" :disabled="saving" @click="submit">
        <span v-if="saving" class="spinner"></span>
        {{ isNew ? '만들기' : '저장' }}
      </button>
    </template>
  </BaseModal>
</template>

<style scoped>
.pform {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.pform__spacer {
  flex: 1;
}

.pform__error {
  margin: 0;
  padding: 7px 10px;
  border-radius: var(--radius-xs);
  background: var(--danger-soft);
  color: var(--danger);
  font-weight: 600;
}

.pform__check {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  cursor: pointer;
}

.pform__check em {
  display: block;
  font-style: normal;
}

.swatches {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.swatch {
  width: 28px;
  height: 28px;
  border: 2px solid transparent;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.12);
  transition: transform 0.1s, border-color 0.12s;
}

.swatch:hover {
  transform: scale(1.1);
}

.swatch--on {
  border-color: var(--text);
}
</style>
