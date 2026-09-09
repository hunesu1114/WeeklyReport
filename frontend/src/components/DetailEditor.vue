<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { DETAIL_COLUMN_WIDTH, lineCount } from '@/utils/report'

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '' },
  minRows: { type: Number, default: 4 },
  showMeta: { type: Boolean, default: true },
})
const emit = defineEmits(['update:modelValue'])

const TAGS = ['진행', '완료', '예정', '이슈', '보류']
const INDENT = '  '

const area = ref(null)
const value = computed({
  get: () => props.modelValue ?? '',
  set: (v) => emit('update:modelValue', v),
})

const excelLines = computed(() => lineCount(value.value, DETAIL_COLUMN_WIDTH))
const excelHeight = computed(() => Math.round(excelLines.value * 17))

watch(() => props.modelValue, () => nextTick(autoGrow))

function autoGrow() {
  const el = area.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.max(el.scrollHeight, props.minRows * 24)}px`
}

/** 커서/선택 영역을 바꿔치기하고 커서를 원하는 위치에 다시 놓는다. */
function apply(next, selectionStart, selectionEnd = selectionStart) {
  value.value = next
  nextTick(() => {
    const el = area.value
    if (!el) return
    el.focus()
    el.setSelectionRange(selectionStart, selectionEnd)
    autoGrow()
  })
}

function lineBoundsAt(text, position) {
  const start = text.lastIndexOf('\n', position - 1) + 1
  const endIndex = text.indexOf('\n', position)
  return { start, end: endIndex === -1 ? text.length : endIndex }
}

/** 현재 줄 맨 앞에 문자열을 끼워 넣는다. (태그 / 불릿 버튼) */
function prefixCurrentLine(prefix) {
  const el = area.value
  if (!el) return
  const text = value.value
  const { start } = lineBoundsAt(text, el.selectionStart)
  const next = text.slice(0, start) + prefix + text.slice(start)
  apply(next, el.selectionStart + prefix.length)
}

function insertTag(tag) {
  prefixCurrentLine(`[${tag}] `)
}

function onKeydown(event) {
  const el = event.target
  const text = value.value
  const { selectionStart, selectionEnd } = el

  if (event.key === 'Tab') {
    event.preventDefault()
    const from = lineBoundsAt(text, selectionStart).start
    const to = lineBoundsAt(text, selectionEnd).end
    const block = text.slice(from, to)

    if (event.shiftKey) {
      let removed = 0
      const outdented = block
        .split('\n')
        .map((line) => {
          const cut = line.startsWith(INDENT) ? INDENT.length : line.startsWith(' ') ? 1 : 0
          removed += cut
          return line.slice(cut)
        })
        .join('\n')
      apply(
        text.slice(0, from) + outdented + text.slice(to),
        Math.max(from, selectionStart - Math.min(INDENT.length, removed)),
        Math.max(from, selectionEnd - removed),
      )
      return
    }

    if (selectionStart === selectionEnd) {
      apply(
        text.slice(0, selectionStart) + INDENT + text.slice(selectionStart),
        selectionStart + INDENT.length,
      )
      return
    }
    const lines = block.split('\n')
    const indented = lines.map((line) => INDENT + line).join('\n')
    apply(
      text.slice(0, from) + indented + text.slice(to),
      selectionStart + INDENT.length,
      selectionEnd + INDENT.length * lines.length,
    )
    return
  }

  if (event.key === 'Enter' && !event.shiftKey && selectionStart === selectionEnd) {
    const { start } = lineBoundsAt(text, selectionStart)
    const currentLine = text.slice(start, selectionStart)
    const match = /^(\s*)((?:[-*>]\s)?)/.exec(currentLine)
    const prefix = (match?.[1] ?? '') + (match?.[2] ?? '')
    if (!prefix) return

    // 접두사만 있고 내용이 없으면 빈 불릿을 지우고 줄을 닫는다.
    if (currentLine.trimEnd() === prefix.trimEnd() && prefix.trim() !== '') {
      event.preventDefault()
      apply(text.slice(0, start) + text.slice(selectionStart), start)
      return
    }
    event.preventDefault()
    const insert = `\n${prefix}`
    apply(text.slice(0, selectionStart) + insert + text.slice(selectionStart), selectionStart + insert.length)
  }
}
</script>

<template>
  <div class="detail-editor">
    <div class="detail-editor__toolbar">
      <span class="detail-editor__group">
        <button
          v-for="tag in TAGS"
          :key="tag"
          type="button"
          class="chip"
          :title="`현재 줄 앞에 [${tag}] 붙이기`"
          @click="insertTag(tag)"
        >
          [{{ tag }}]
        </button>
      </span>
      <span class="detail-editor__group">
        <button type="button" class="chip" title="현재 줄을 항목으로" @click="prefixCurrentLine('- ')">
          − 항목
        </button>
        <button
          type="button"
          class="chip"
          title="현재 줄을 하위 항목으로"
          @click="prefixCurrentLine('   - ')"
        >
          ↳ 하위
        </button>
      </span>
      <span class="detail-editor__hint tiny muted">Tab 들여쓰기 · Enter 목록 이어쓰기</span>
    </div>

    <textarea
      ref="area"
      v-model="value"
      class="control detail-editor__area"
      :placeholder="placeholder"
      :rows="minRows"
      spellcheck="false"
      @keydown="onKeydown"
      @input="autoGrow"
      @focus="autoGrow"
    ></textarea>

    <p v-if="showMeta" class="detail-editor__meta tiny muted">
      엑셀에서 약 <strong>{{ excelLines }}줄</strong> ({{ excelHeight }}pt)
    </p>
  </div>
</template>

<style scoped>
.detail-editor {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.detail-editor__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.detail-editor__group {
  display: inline-flex;
  gap: 4px;
}

.detail-editor__hint {
  margin-left: auto;
}

.chip {
  padding: 3px 8px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--surface);
  color: var(--text-3);
  font-size: 11px;
  font-weight: 700;
  line-height: 1.5;
  transition: background 0.12s, color 0.12s, border-color 0.12s;
}

.chip:hover {
  background: var(--brand-soft);
  border-color: var(--brand-border);
  color: var(--brand-strong);
}

.detail-editor__area {
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.62;
  min-height: 96px;
  overflow-y: hidden;
  tab-size: 2;
}

.detail-editor__meta {
  margin: 0;
  text-align: right;
}
</style>
