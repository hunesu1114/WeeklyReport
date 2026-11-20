<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import OrphanClaimBanner from '@/components/OrphanClaimBanner.vue'
import { reportApi } from '@/api/client'
import { useToast } from '@/composables/useToast'
import { weekdayOf } from '@/utils/report'

const router = useRouter()
const toast = useToast()

/** 근무시간 막대의 기준. 넘으면 호박색으로 칠한다. */
const BASE_HOURS = 40

const reports = ref([])
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const query = ref('')
const loading = ref(false)
const busyId = ref(null)
/** ⋯ 메뉴가 열린 보고서 */
const menuId = ref(null)
const searchInput = ref(null)

onMounted(() => {
  load()
  document.addEventListener('pointerdown', onDocPointer)
  document.addEventListener('keydown', onDocKey)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocPointer)
  document.removeEventListener('keydown', onDocKey)
})

function onDocPointer(event) {
  if (menuId.value != null && !event.target.closest?.('.rowmenu')) menuId.value = null
}

/** '/' 로 검색창. 메모 화면과 같은 약속이다 */
function onDocKey(event) {
  if (event.key === 'Escape') menuId.value = null
  const el = event.target
  const typing = el instanceof HTMLElement && /^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName)
  if (event.key === '/' && !typing && !event.ctrlKey && !event.metaKey) {
    event.preventDefault()
    searchInput.value?.focus()
  }
}

async function load(nextPage = 0) {
  loading.value = true
  try {
    const data = await reportApi.list({ query: query.value || undefined, page: nextPage, size: 20 })
    reports.value = data.content
    page.value = data.number
    totalPages.value = data.totalPages
    totalElements.value = data.totalElements
  } catch (error) {
    toast.error(`목록을 불러오지 못했습니다. ${error.message}`)
  } finally {
    loading.value = false
  }
}

async function download(report) {
  busyId.value = report.id
  try {
    const filename = await reportApi.download(report.id, report.templateKey)
    toast.success(`${filename} 을(를) 내려받았습니다.`)
  } catch (error) {
    toast.error(`다운로드에 실패했습니다. ${error.message}`)
  } finally {
    busyId.value = null
  }
}

async function followUp(report) {
  busyId.value = report.id
  try {
    const created = await reportApi.followUp(report.id)
    toast.success('차주 예정 항목을 옮겨 다음 주 보고서를 만들었습니다.')
    await router.push({ name: 'report-edit', params: { id: created.id } })
  } catch (error) {
    toast.error(error.message)
  } finally {
    busyId.value = null
  }
}

async function remove(report) {
  menuId.value = null
  if (!window.confirm(`${report.title} (${report.reportDate}) 를 삭제할까요?\n되돌릴 수 없습니다.`)) return
  try {
    await reportApi.remove(report.id)
    toast.success('삭제했습니다.')
    await load(page.value)
  } catch (error) {
    toast.error(error.message)
  }
}

// ── 보여주는 모양 ───────────────────────────────────────

const shortDate = (iso) => {
  if (!iso) return ''
  const [, m, d] = iso.split('-')
  return `${m}.${d}`
}

/** 달별로 묶는다. 매주 한 장씩 쌓이는 목록이라 달이 가장 자연스러운 마디다. */
const months = computed(() => {
  const out = []
  for (const report of reports.value) {
    const key = (report.reportDate ?? '').slice(0, 7)
    let group = out.find((g) => g.key === key)
    if (!group) {
      group = { key, label: key.replace('-', '.'), rows: [] }
      out.push(group)
    }
    group.rows.push(report)
  }
  return out
})

function hoursOf(report) {
  const hours = Number(report.totalHours) || 0
  return {
    text: `${hours}h`,
    width: `${Math.min(100, Math.round((hours / BASE_HOURS) * 100))}%`,
    over: hours > BASE_HOURS,
  }
}

/**
 * 이번 주 보고서가 아직 없으면 맨 위에 한 줄로 알린다.
 *
 * 가장 최근 보고서의 보고일이 지났고 2주 안쪽이면, 그다음 주 것을 만들 차례다.
 * 매주 하는 일이 "지난주 것을 이어서 만들기"라서, 그 버튼을 목록의 맨 앞에 둔다.
 */
const nextUp = computed(() => {
  if (query.value || page.value !== 0 || !reports.value.length) return null
  const latest = reports.value[0]
  if (!latest?.reportDate) return null

  const [y, m, d] = latest.reportDate.split('-').map(Number)
  const last = new Date(y, m - 1, d)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const days = Math.round((today - last) / 86400000)
  if (days <= 0 || days > 14) return null

  const next = new Date(last)
  next.setDate(last.getDate() + 7)
  const iso = `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, '0')}-${String(next.getDate()).padStart(2, '0')}`
  return { latest, date: shortDate(iso), weekday: weekdayOf(iso), carry: latest.nextWeekItemCount }
})
</script>

<template>
  <div class="list">
    <OrphanClaimBanner @claimed="load(0)" />

    <header class="list__head">
      <div>
        <h1 class="page-title">보고서</h1>
        <p class="list__sub num">총 {{ totalElements }}건</p>
      </div>
      <form class="list__search" role="search" @submit.prevent="load(0)">
        <AppIcon name="search" :size="15" />
        <input
          ref="searchInput"
          v-model="query"
          type="search"
          placeholder="작성자 · 업무명 · 업무상세"
          aria-label="보고서 검색"
        />
        <kbd aria-hidden="true">/</kbd>
      </form>
    </header>

    <div v-if="nextUp" class="nextup">
      <span class="nextup__icon" aria-hidden="true"><AppIcon name="calendar" :size="17" /></span>
      <div class="nextup__text">
        <strong>{{ nextUp.date }}({{ nextUp.weekday }}) 보고서가 아직 없습니다</strong>
        <span>
          지난 보고서의 ‘차주 진행 예정’
          <span class="num">{{ nextUp.carry }}</span>건이 이번 ‘금주 진행 내용’으로 넘어옵니다.
        </span>
      </div>
      <RouterLink class="btn" to="/reports/new">빈 보고서로</RouterLink>
      <button class="btn btn--primary" type="button" :disabled="busyId === nextUp.latest.id" @click="followUp(nextUp.latest)">
        지난주 것 이어서 만들기
      </button>
    </div>

    <div class="sheet">
      <div v-if="loading" class="list__state muted">불러오는 중…</div>
      <div v-else-if="!reports.length" class="list__state">
        <p class="muted">{{ query ? '찾는 보고서가 없습니다.' : '아직 작성한 주간보고가 없습니다.' }}</p>
        <RouterLink v-if="!query" class="btn btn--primary" to="/reports/new">첫 주간보고 작성하기</RouterLink>
      </div>

      <div v-else class="sheet__scroll">
        <table class="table">
          <thead>
            <tr>
              <th scope="col" class="table__date">보고일</th>
              <th scope="col">제목</th>
              <th scope="col" class="table__range">금주 기간</th>
              <th scope="col" class="table__num">항목 금주/차주</th>
              <th scope="col" class="table__hours">근무시간</th>
              <th scope="col" class="table__actions"><span class="sr-only">작업</span></th>
            </tr>
          </thead>
          <tbody v-for="month in months" :key="month.key">
            <tr class="table__month">
              <th scope="rowgroup" colspan="6">{{ month.label }}</th>
            </tr>
            <tr v-for="report in month.rows" :key="report.id">
              <td class="table__date num">
                <RouterLink :to="{ name: 'report-edit', params: { id: report.id } }">
                  {{ shortDate(report.reportDate) }}
                </RouterLink>
                <span class="muted">{{ weekdayOf(report.reportDate) }}</span>
              </td>
              <td class="table__title">
                <RouterLink :to="{ name: 'report-edit', params: { id: report.id } }">{{ report.title }}</RouterLink>
              </td>
              <td class="table__range num">{{ shortDate(report.thisWeekStart) }} – {{ shortDate(report.thisWeekEnd) }}</td>
              <td class="table__num num">{{ report.thisWeekItemCount }} / {{ report.nextWeekItemCount }}</td>
              <td class="table__hours">
                <span class="hours" :class="{ 'hours--over': hoursOf(report).over }">
                  <span class="hours__bar"><span :style="{ width: hoursOf(report).width }"></span></span>
                  <span class="num">{{ hoursOf(report).text }}</span>
                </span>
              </td>
              <td class="table__actions">
                <button
                  class="btn btn--sm btn--ghost"
                  type="button"
                  :disabled="busyId === report.id"
                  title="이 보고서의 차주 예정을 금주 진행으로 옮겨 다음 주 보고서를 만듭니다"
                  @click="followUp(report)"
                >
                  다음 주 만들기
                </button>
                <button
                  class="btn btn--icon"
                  type="button"
                  :disabled="busyId === report.id"
                  title="엑셀로 내려받기"
                  :aria-label="`${report.reportDate} 보고서 엑셀로 내려받기`"
                  @click="download(report)"
                >
                  <span v-if="busyId === report.id" class="spinner"></span>
                  <AppIcon v-else name="download" :size="15" />
                </button>
                <!-- 삭제는 한 번 더 들어가야 보인다. 줄마다 빨간 버튼이 서 있으면 목록이 경고판이 된다 -->
                <span class="rowmenu">
                  <button
                    class="btn btn--icon"
                    type="button"
                    title="더 보기"
                    :aria-label="`${report.reportDate} 보고서 더 보기`"
                    :aria-expanded="menuId === report.id"
                    @click="menuId = menuId === report.id ? null : report.id"
                  >
                    <AppIcon name="more" :size="15" />
                  </button>
                  <span v-if="menuId === report.id" class="rowmenu__pop" role="menu">
                    <button class="rowmenu__item" type="button" role="menuitem" @click="download(report)">
                      <AppIcon name="download" :size="14" />
                      엑셀로 내려받기
                    </button>
                    <button class="rowmenu__item rowmenu__item--danger" type="button" role="menuitem" @click="remove(report)">
                      <AppIcon name="trash" :size="14" />
                      삭제
                    </button>
                  </span>
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="totalPages > 1" class="pager">
        <span class="num muted">{{ page + 1 }} / {{ totalPages }} 쪽</span>
        <span class="pager__btns">
          <button class="btn btn--sm" type="button" :disabled="page === 0" @click="load(page - 1)">이전</button>
          <button class="btn btn--sm" type="button" :disabled="page + 1 >= totalPages" @click="load(page + 1)">다음</button>
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.list {
  display: flex;
  flex-direction: column;
  gap: 18px;
  max-width: 1160px;
  margin: 0 auto;
}

.list__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.list__sub {
  margin: 4px 0 0;
  color: var(--text-3);
  font-size: 12px;
}

.list__search {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 340px;
  max-width: 100%;
  height: 34px;
  padding: 0 8px 0 10px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text-3);
  transition: border-color 0.12s, box-shadow 0.12s;
}

.list__search:focus-within {
  border-color: var(--brand);
  box-shadow: var(--ring);
}

.list__search input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--text);
  font-size: 13px;
}

/* ---------- 이번 주 알림 줄 ---------- */
.nextup {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 14px;
  padding: 14px 16px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--surface);
}

.nextup__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  background: var(--brand-soft);
  color: var(--brand-strong);
}

.nextup__text {
  display: flex;
  flex: 1 1 320px;
  flex-direction: column;
  min-width: 0;
}

.nextup__text > span {
  color: var(--text-3);
  font-size: 12.5px;
}

.nextup .btn {
  text-decoration: none;
}

/* ---------- 표 ---------- */
.sheet {
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--surface);
}

/* 좁은 화면에서만 옆으로 굴린다. 늘 굴리게 두면 ⋯ 메뉴가 표 아래 끝에서 잘린다 */
@media (max-width: 960px) {
  .sheet__scroll {
    overflow-x: auto;
  }
}

.list__state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 56px 20px;
  text-align: center;
}

.list__state .btn {
  text-decoration: none;
}

.table {
  width: 100%;
  min-width: 860px;
  border-collapse: collapse;
}

.table th {
  padding: 10px 12px;
  border-bottom: 1px solid var(--line);
  color: var(--text-3);
  font-size: 11.5px;
  font-weight: 600;
  text-align: left;
  white-space: nowrap;
}

.table th:first-child,
.table td:first-child {
  padding-left: 16px;
}

/* 달 머리. 표 안의 작은 띠로, 고정폭 숫자로 쓴다 */
.table__month th {
  padding: 7px 16px 6px;
  background: var(--surface-2);
  color: var(--text-2);
  font-family: var(--font-num);
  font-weight: 500;
}

.table td {
  height: 46px;
  padding: 0 12px;
  border-bottom: 1px solid var(--line);
  vertical-align: middle;
}

.table tbody:last-of-type tr:last-child td {
  border-bottom: 0;
}

.table tbody tr:not(.table__month):hover td {
  background: var(--surface-hover);
}

.table a {
  color: var(--text);
  text-decoration: none;
}

.table a:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.table__date {
  width: 96px;
  white-space: nowrap;
}

.table__date a {
  margin-right: 5px;
  font-weight: 500;
}

.table__title {
  font-weight: 500;
}

.table__range {
  width: 130px;
  color: var(--text-3);
  white-space: nowrap;
}

.table__num {
  width: 110px;
  text-align: right !important;
  white-space: nowrap;
}

.table__hours {
  width: 150px;
}

.hours {
  display: flex;
  align-items: center;
  gap: 10px;
}

.hours__bar {
  overflow: hidden;
  width: 64px;
  height: 4px;
  border-radius: 2px;
  background: var(--bg-subtle);
}

.hours__bar > span {
  display: block;
  height: 4px;
  background: var(--text-2);
}

.hours--over .hours__bar > span {
  background: var(--star);
}

.hours--over .num {
  color: var(--warn);
  font-weight: 500;
}

.table__actions {
  width: 210px;
  padding-right: 10px !important;
  text-align: right !important;
  white-space: nowrap;
}

.table__actions > * {
  vertical-align: middle;
}

.rowmenu {
  position: relative;
  display: inline-block;
}

.rowmenu__pop {
  position: absolute;
  top: calc(100% + 4px);
  right: 0;
  z-index: 20;
  min-width: 170px;
  padding: 4px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: var(--shadow-3);
  text-align: left;
}

.rowmenu__item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 7px 8px;
  border: 0;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--text);
  font-size: 13px;
}

.rowmenu__item:hover {
  background: var(--surface-hover);
}

.rowmenu__item--danger {
  color: var(--danger);
}

.rowmenu__item--danger:hover {
  background: var(--danger-soft);
}

.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  border-top: 1px solid var(--line);
  font-size: 12px;
}

.pager__btns {
  display: inline-flex;
  gap: 4px;
}
</style>
