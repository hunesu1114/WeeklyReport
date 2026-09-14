<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ItemSection from '@/components/ItemSection.vue'
import ReportMetaCard from '@/components/ReportMetaCard.vue'
import ReportPreview from '@/components/ReportPreview.vue'
import DetailEditor from '@/components/DetailEditor.vue'
import KanbanLinkPanel from '@/components/KanbanLinkPanel.vue'
import { reportApi } from '@/api/client'
import { useMetaStore } from '@/stores/meta'
import { useToast } from '@/composables/useToast'
import {
  NEXT_WEEK,
  THIS_WEEK,
  fromServerItem,
  newItem,
  roundHours,
  sumHours,
  todayIso,
  toSavePayload,
  weekDefaults,
} from '@/utils/report'

const DRAFT_KEY = 'weekly-report:draft'

const route = useRoute()
const router = useRouter()
const meta = useMetaStore()
const toast = useToast()

const reportId = ref(null)
const loading = ref(true)
const saving = ref(false)
const downloading = ref(false)
const dirty = ref(false)
const showPreview = ref(true)
const restoredDraft = ref(false)

const form = reactive({
  reportDate: todayIso(),
  authorName: '',
  titleOverride: '',
  ...weekDefaults(todayIso()),
  baseHours: 40,
  note: '',
  templateKey: 'DEFAULT_V1',
})

const thisWeekItems = ref([])
const nextWeekItems = ref([])

const isNew = computed(() => reportId.value === null)
const totalHours = computed(() => roundHours(sumHours(thisWeekItems.value)))
const excludedHours = computed(() => roundHours((Number(form.baseHours) || 0) - totalHours.value))
const canSave = computed(() => Boolean(form.authorName?.trim()) && Boolean(form.reportDate))

onMounted(async () => {
  window.addEventListener('keydown', onGlobalKeydown)
  try {
    await meta.load()
  } catch (error) {
    toast.error(`기준 정보를 불러오지 못했습니다. ${error.message}`)
  }
  await bootstrap()
  loading.value = false
  // 초기 로딩으로 생긴 변경은 dirty 로 치지 않는다.
  setTimeout(() => (dirty.value = false), 0)
})

onBeforeUnmount(() => window.removeEventListener('keydown', onGlobalKeydown))

watch(() => route.params.id, bootstrap)

watch(
  [form, thisWeekItems, nextWeekItems],
  () => {
    if (loading.value) return
    dirty.value = true
    if (isNew.value) persistDraft()
  },
  { deep: true },
)

async function bootstrap() {
  const id = route.params.id
  if (id) {
    reportId.value = Number(id)
    await loadReport(reportId.value)
    return
  }
  reportId.value = null
  if (restoreDraft()) {
    restoredDraft.value = true
    toast.info('임시 저장된 작성 내용을 불러왔습니다.')
    return
  }
  restoredDraft.value = false
  await loadDefaults()
}

async function loadReport(id) {
  loading.value = true
  try {
    const data = await reportApi.get(id)
    Object.assign(form, {
      reportDate: data.reportDate,
      authorName: data.authorName,
      titleOverride: data.titleOverride ?? '',
      thisWeekStart: data.thisWeekStart,
      thisWeekEnd: data.thisWeekEnd,
      nextWeekStart: data.nextWeekStart,
      nextWeekEnd: data.nextWeekEnd,
      baseHours: Number(data.baseHours),
      note: data.note ?? '',
      templateKey: data.templateKey,
    })
    thisWeekItems.value = data.thisWeekItems.map(fromServerItem)
    nextWeekItems.value = data.nextWeekItems.map(fromServerItem)
  } catch (error) {
    toast.error(error.message)
  } finally {
    loading.value = false
    dirty.value = false
  }
}

async function loadDefaults() {
  loading.value = true
  try {
    const data = await reportApi.defaults({ reportDate: form.reportDate })
    Object.assign(form, {
      reportDate: data.reportDate,
      authorName: data.authorName ?? '',
      thisWeekStart: data.thisWeekStart,
      thisWeekEnd: data.thisWeekEnd,
      nextWeekStart: data.nextWeekStart,
      nextWeekEnd: data.nextWeekEnd,
      baseHours: Number(data.baseHours),
      templateKey: data.templateKey,
    })
    thisWeekItems.value = data.thisWeekItems.map(fromServerItem)
    nextWeekItems.value = []
    if (data.thisWeekItems.length) {
      toast.info(`직전 보고서의 차주 예정 ${data.thisWeekItems.length}건을 금주 항목으로 가져왔습니다.`)
    }
    if (!thisWeekItems.value.length) thisWeekItems.value = [newItem(THIS_WEEK)]
    nextWeekItems.value = [newItem(NEXT_WEEK)]
  } catch (error) {
    toast.error(`기본값을 불러오지 못했습니다. ${error.message}`)
    thisWeekItems.value = [newItem(THIS_WEEK)]
    nextWeekItems.value = [newItem(NEXT_WEEK)]
  } finally {
    loading.value = false
  }
}

function persistDraft() {
  try {
    localStorage.setItem(
      DRAFT_KEY,
      JSON.stringify({ form, thisWeekItems: thisWeekItems.value, nextWeekItems: nextWeekItems.value }),
    )
  } catch {
    /* 용량 초과 등은 무시한다 */
  }
}

function restoreDraft() {
  try {
    const raw = localStorage.getItem(DRAFT_KEY)
    if (!raw) return false
    const draft = JSON.parse(raw)
    if (!draft?.form) return false
    Object.assign(form, draft.form)
    thisWeekItems.value = draft.thisWeekItems ?? []
    nextWeekItems.value = draft.nextWeekItems ?? []
    return true
  } catch {
    return false
  }
}

function clearDraft() {
  localStorage.removeItem(DRAFT_KEY)
}

/** 되살린 임시 저장본을 버리고 기본값에서 다시 시작한다. */
async function discardDraft() {
  if (!window.confirm('임시 저장된 내용을 버리고 새로 시작할까요?')) return
  clearDraft()
  restoredDraft.value = false
  await loadDefaults()
  dirty.value = false
  toast.info('임시 저장 내용을 지웠습니다.')
}

async function save() {
  if (!canSave.value) {
    toast.error('작성자와 보고일은 반드시 입력해야 합니다.')
    return null
  }
  saving.value = true
  try {
    const payload = toSavePayload(form, thisWeekItems.value, nextWeekItems.value)
    const saved = isNew.value
      ? await reportApi.create(payload)
      : await reportApi.update(reportId.value, payload)

    if (isNew.value) {
      clearDraft()
      reportId.value = saved.id
      await router.replace({ name: 'report-edit', params: { id: saved.id } })
    }
    dirty.value = false
    await meta.load(true)
    toast.success('저장했습니다.')
    return saved.id
  } catch (error) {
    toast.error(error.message)
    return null
  } finally {
    saving.value = false
  }
}

/** 저장되지 않은 내용이 있으면 저장한 뒤 내려받는다. */
async function download() {
  let id = reportId.value
  if (id === null || dirty.value) {
    id = await save()
    if (id === null) return
  }
  downloading.value = true
  try {
    const filename = await reportApi.download(id, form.templateKey)
    toast.success(`${filename} 을(를) 내려받았습니다.`)
  } catch (error) {
    toast.error(`다운로드에 실패했습니다. ${error.message}`)
  } finally {
    downloading.value = false
  }
}

async function removeReport() {
  if (isNew.value) return
  if (!window.confirm('이 주간보고를 삭제할까요? 되돌릴 수 없습니다.')) return
  try {
    await reportApi.remove(reportId.value)
    toast.success('삭제했습니다.')
    await router.push({ name: 'reports' })
  } catch (error) {
    toast.error(error.message)
  }
}

/**
 * 칸반 카드를 금주 진행 항목으로 옮긴다.
 *
 * 보고서의 '업무명'은 프로젝트 단위라, 같은 프로젝트 이름의 항목이 이미 있으면
 * 새 항목을 만들지 않고 그 항목의 업무상세에 줄만 덧붙인다.
 */
function addFromKanban({ taskName, lines }) {
  let item = thisWeekItems.value.find((it) => (it.taskName || '').trim() === taskName.trim())
  if (!item) {
    item = newItem(THIS_WEEK)
    item.taskName = taskName
    item.detail = ''
    thisWeekItems.value.push(item)
  }
  const head = item.detail ? `${item.detail.replace(/\s+$/, '')}\n` : ''
  item.detail = head + lines.join('\n')
  toast.success(`'${taskName}' 항목에 ${lines.length}줄을 넣었습니다.`)
}

function onGlobalKeydown(event) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    save()
  }
}
</script>

<template>
  <div class="editor" :class="{ 'editor--split': showPreview }">
    <div class="editor__actions">
      <div class="editor__heading">
        <h1>{{ isNew ? '새 주간보고' : '주간보고 수정' }}</h1>
        <span v-if="dirty" class="badge badge--warn">저장 안 됨</span>
        <span v-else-if="!isNew" class="badge badge--ok">저장됨</span>
      </div>
      <div class="editor__buttons">
        <button
          v-if="restoredDraft"
          class="btn btn--sm btn--danger"
          type="button"
          @click="discardDraft"
        >
          임시본 버리기
        </button>
        <button class="btn btn--sm" type="button" @click="showPreview = !showPreview">
          {{ showPreview ? '미리보기 숨기기' : '미리보기 보기' }}
        </button>
        <button v-if="!isNew" class="btn btn--sm btn--danger" type="button" @click="removeReport">
          삭제
        </button>
        <button class="btn" type="button" :disabled="saving || !canSave" @click="save">
          <span v-if="saving" class="spinner"></span>
          {{ saving ? '저장 중' : '저장 (Ctrl+S)' }}
        </button>
        <button
          class="btn btn--primary"
          type="button"
          :disabled="downloading || saving || !canSave"
          @click="download"
        >
          <span v-if="downloading" class="spinner"></span>
          엑셀 다운로드
        </button>
      </div>
    </div>

    <div class="editor__grid">
      <div class="editor__form">
        <ReportMetaCard :form="form" :templates="meta.templates" :authors="meta.authors" />

        <ItemSection
          title="금주 진행 내용"
          hint="이번 주에 실제로 한 일과 소요시간을 적습니다."
          :section="THIS_WEEK"
          :items="thisWeekItems"
          :statuses="meta.statuses"
          :task-names="meta.taskNames"
        />

        <section class="card">
          <div class="card__head">
            <h2 class="card__title">비고</h2>
            <span class="tiny muted">공휴일, 근무시간 예외 같은 설명</span>
          </div>
          <div class="card__body">
            <DetailEditor
              v-model="form.note"
              placeholder="예: 5/1, 5/5 공휴일로 인해 24H 근무"
              :min-rows="2"
              :show-meta="false"
            />
          </div>
        </section>

        <ItemSection
          title="차주 진행 예정"
          hint="다음 주 계획입니다. 소요시간 칸은 엑셀에서 회색으로 막힙니다."
          :section="NEXT_WEEK"
          :items="nextWeekItems"
          :statuses="meta.statuses"
          :task-names="meta.taskNames"
        />
      </div>

      <aside v-if="showPreview" class="editor__preview">
        <div class="card editor__preview-card">
          <div class="card__head">
            <h2 class="card__title">엑셀 미리보기</h2>
            <span class="tiny muted">다운로드될 모양</span>
          </div>
          <ReportPreview
            :form="form"
            :this-week-items="thisWeekItems"
            :next-week-items="nextWeekItems"
          />
        </div>

        <KanbanLinkPanel
          :from="form.thisWeekStart"
          :to="form.thisWeekEnd"
          :used-task-names="thisWeekItems.map((it) => it.taskName)"
          @add="addFromKanban"
        />
      </aside>
    </div>

    <div class="summary">
      <div class="summary__cell">
        <span class="summary__label">금주 항목</span>
        <strong>{{ thisWeekItems.length }}건</strong>
      </div>
      <div class="summary__cell">
        <span class="summary__label">근무시간 합계</span>
        <strong>{{ totalHours }}H</strong>
      </div>
      <div class="summary__cell">
        <span class="summary__label">제외시간</span>
        <strong :class="{ 'summary__negative': excludedHours < 0 }">{{ excludedHours }}H</strong>
      </div>
      <div class="summary__cell">
        <span class="summary__label">차주 예정</span>
        <strong>{{ nextWeekItems.length }}건</strong>
      </div>
      <p v-if="excludedHours < 0" class="summary__warn tiny">
        기준 근무시간({{ form.baseHours }}H)을 {{ Math.abs(excludedHours) }}H 초과했습니다.
      </p>
    </div>
  </div>
</template>

<style scoped>
.editor {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-bottom: 68px;
}

.editor__actions {
  position: sticky;
  top: 56px;
  z-index: 20;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
  background: var(--bg);
}

.editor__heading {
  display: flex;
  align-items: center;
  gap: 10px;
}

.editor__heading h1 {
  margin: 0;
  font-size: 19px;
  letter-spacing: -0.02em;
}

.editor__buttons {
  display: flex;
  /*
   * 한 줄에 btn--sm(보조)과 btn(저장·다운로드)이 섞여 있어 높이가 어긋난다.
   * stretch 로 두면 가장 큰 버튼에 나머지가 맞춰진다 — 숫자를 박지 않으므로
   * 글꼴이나 여백을 바꿔도 따라온다. 글자 크기 차이는 그대로 둬서
   * 주 동작과 보조 동작이 여전히 구분된다.
   */
  align-items: stretch;
  gap: 8px;
}

.editor__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.editor--split .editor__grid {
  grid-template-columns: minmax(0, 1fr) minmax(360px, 520px);
}

.editor__form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.editor__preview {
  position: sticky;
  top: 118px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: calc(100vh - 140px);
  overflow-y: auto;
}

.editor__preview-card {
  overflow: hidden;
  display: flex;
  flex-direction: column;
  flex: none;
}

.editor__preview-card :deep(.preview) {
  overflow: auto;
}

.summary {
  position: fixed;
  left: 50%;
  bottom: 14px;
  transform: translateX(-50%);
  z-index: 25;
  display: flex;
  align-items: center;
  gap: 22px;
  padding: 10px 20px;
  border: 1px solid var(--line);
  border-radius: 999px;
  /* 흰색을 박아 두면 다크에서 밝은 글자가 흰 바탕에 얹혀 읽히지 않는다 */
  background: var(--surface-glass);
  color: var(--text);
  backdrop-filter: blur(6px);
  box-shadow: var(--shadow-2);
}

.summary__cell {
  display: flex;
  align-items: baseline;
  gap: 6px;
  white-space: nowrap;
}

.summary__label {
  font-size: 11px;
  color: var(--text-3);
}

.summary__cell strong {
  font-size: 15px;
  letter-spacing: -0.02em;
}

.summary__negative {
  color: var(--danger);
}

.summary__warn {
  margin: 0;
  padding-left: 14px;
  border-left: 1px solid var(--line);
  color: var(--danger);
  font-weight: 600;
}

@media (max-width: 1279px) {
  .editor--split .editor__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .editor__preview {
    position: static;
  }
}

@media (max-width: 720px) {
  .summary {
    gap: 14px;
    padding: 8px 14px;
  }
}
</style>
