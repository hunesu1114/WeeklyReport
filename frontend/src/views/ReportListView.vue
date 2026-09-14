<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import OrphanClaimBanner from '@/components/OrphanClaimBanner.vue'
import { reportApi } from '@/api/client'
import { useToast } from '@/composables/useToast'
import { formatExcelDate, weekdayOf } from '@/utils/report'

const router = useRouter()
const toast = useToast()

const reports = ref([])
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const query = ref('')
const loading = ref(false)
const busyId = ref(null)

onMounted(load)

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
  if (!window.confirm(`${report.title} (${report.reportDate}) 를 삭제할까요?`)) return
  try {
    await reportApi.remove(report.id)
    toast.success('삭제했습니다.')
    await load(page.value)
  } catch (error) {
    toast.error(error.message)
  }
}
</script>

<template>
  <div class="list">
    <OrphanClaimBanner @claimed="load(0)" />

    <div class="list__head">
      <div>
        <h1>보고서 목록</h1>
        <p class="muted tiny">총 {{ totalElements }}건</p>
      </div>
      <form class="list__search" @submit.prevent="load(0)">
        <input
          v-model="query"
          class="control"
          type="search"
          placeholder="작성자, 업무명, 업무상세 검색"
        />
        <button class="btn" type="submit">검색</button>
        <RouterLink class="btn btn--primary" to="/reports/new">새 주간보고</RouterLink>
      </form>
    </div>

    <div class="card">
      <div v-if="loading" class="list__state muted">불러오는 중…</div>
      <div v-else-if="!reports.length" class="list__state">
        <p class="muted">아직 작성한 주간보고가 없습니다.</p>
        <RouterLink class="btn btn--primary" to="/reports/new">첫 주간보고 작성하기</RouterLink>
      </div>

      <table v-else class="table">
        <thead>
          <tr>
            <th>보고일</th>
            <th>제목</th>
            <th>금주 기간</th>
            <th class="table__num">항목</th>
            <th class="table__num">근무시간</th>
            <th>양식</th>
            <th class="table__actions">작업</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="report in reports" :key="report.id">
            <td>
              <RouterLink :to="{ name: 'report-edit', params: { id: report.id } }">
                {{ report.reportDate }}
              </RouterLink>
              <span class="tiny muted">({{ weekdayOf(report.reportDate) }})</span>
            </td>
            <td class="table__title">{{ report.title }}</td>
            <td class="tiny muted">
              {{ formatExcelDate(report.thisWeekStart) }} ~ {{ formatExcelDate(report.thisWeekEnd) }}
            </td>
            <td class="table__num">
              금주 {{ report.thisWeekItemCount }} / 차주 {{ report.nextWeekItemCount }}
            </td>
            <td class="table__num">{{ Number(report.totalHours) }}H</td>
            <td class="tiny muted">{{ report.templateKey }}</td>
            <td class="table__actions">
              <button
                class="btn btn--sm"
                type="button"
                :disabled="busyId === report.id"
                title="이 보고서의 차주 예정을 금주 진행으로 옮겨 다음 주 보고서를 만듭니다"
                @click="followUp(report)"
              >
                다음 주 만들기
              </button>
              <button
                class="btn btn--sm btn--primary"
                type="button"
                :disabled="busyId === report.id"
                @click="download(report)"
              >
                엑셀
              </button>
              <button class="btn btn--sm btn--danger" type="button" @click="remove(report)">
                삭제
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <div v-if="totalPages > 1" class="pager">
        <button class="btn btn--sm" type="button" :disabled="page === 0" @click="load(page - 1)">
          이전
        </button>
        <span class="tiny muted">{{ page + 1 }} / {{ totalPages }}</span>
        <button
          class="btn btn--sm"
          type="button"
          :disabled="page + 1 >= totalPages"
          @click="load(page + 1)"
        >
          다음
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.list__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.list__head h1 {
  margin: 0;
  font-size: 19px;
  letter-spacing: -0.02em;
}

.list__head p {
  margin: 2px 0 0;
}

.list__search {
  display: flex;
  align-items: center;
  gap: 8px;
}

/*
 * 검색창·검색·새 주간보고의 높이를 맞춘다.
 * input 은 브라우저 기본 line-height 를 써서 버튼과 몇 px 씩 어긋난다.
 * 셋 다 같은 값을 못박는 편이 확실하다.
 */
.list__search .control,
.list__search .btn {
  height: 38px;
}

.list__search .control {
  width: 280px;
}

.list__search .btn {
  text-decoration: none;
}

.list__state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 56px 20px;
  text-align: center;
}

.table {
  width: 100%;
  border-collapse: collapse;
}

.table th {
  padding: 11px 14px;
  border-bottom: 1px solid var(--line);
  background: var(--bg-subtle);
  color: var(--text-3);
  font-size: 12px;
  font-weight: 700;
  text-align: left;
  white-space: nowrap;
}

.table td {
  padding: 11px 14px;
  border-bottom: 1px solid var(--line);
  vertical-align: middle;
}

.table tr:last-child td {
  border-bottom: 0;
}

.table tbody tr:hover {
  background: var(--surface-hover);
}

.table__title {
  font-weight: 600;
}

.table__num {
  text-align: right;
  white-space: nowrap;
}

.table__actions {
  text-align: right;
  white-space: nowrap;
}

.table__actions .btn + .btn {
  margin-left: 5px;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 14px;
  border-top: 1px solid var(--line);
}
</style>
