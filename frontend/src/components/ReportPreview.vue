<script setup>
import { computed } from 'vue'
import { formatRange, roundHours, sumHours } from '@/utils/report'

const props = defineProps({
  form: { type: Object, required: true },
  thisWeekItems: { type: Array, default: () => [] },
  nextWeekItems: { type: Array, default: () => [] },
})

const title = computed(
  () => props.form.titleOverride?.trim() || `주간보고 - ${props.form.authorName || ''}`.trim(),
)
const total = computed(() => roundHours(sumHours(props.thisWeekItems)))
const excluded = computed(() => roundHours((Number(props.form.baseHours) || 0) - total.value))
</script>

<template>
  <div class="preview">
    <table class="sheet">
      <colgroup>
        <col style="width: 5.7%" />
        <col style="width: 18%" />
        <col style="width: 59.6%" />
        <col style="width: 7.5%" />
        <col style="width: 9.2%" />
      </colgroup>
      <tbody>
        <tr class="sheet__title-row">
          <td class="cell cell--title" colspan="2">{{ title }}</td>
          <td class="cell cell--void" colspan="3"></td>
        </tr>
        <tr class="sheet__spacer">
          <td class="cell cell--void" colspan="5"></td>
        </tr>

        <tr>
          <td class="cell cell--section" colspan="5">
            금주 진행 내용 {{ formatRange(form.thisWeekStart, form.thisWeekEnd) }}
          </td>
        </tr>
        <tr>
          <th class="cell cell--head">NO</th>
          <th class="cell cell--head">업무명</th>
          <th class="cell cell--head">업무상세</th>
          <th class="cell cell--head">진행상태</th>
          <th class="cell cell--head">소요시간(H)</th>
        </tr>
        <tr v-for="(item, index) in thisWeekItems" :key="item.key">
          <td class="cell cell--center">{{ index + 1 }}</td>
          <td class="cell">{{ item.taskName }}</td>
          <td class="cell cell--detail">{{ item.detail }}</td>
          <td class="cell cell--center">{{ item.status }}</td>
          <td class="cell cell--center">{{ item.hours }}</td>
        </tr>
        <tr v-if="!thisWeekItems.length">
          <td class="cell cell--empty" colspan="5">금주 진행 항목이 없습니다</td>
        </tr>

        <tr>
          <td class="cell cell--void" colspan="3"></td>
          <td class="cell cell--head">근무시간</td>
          <td class="cell cell--center">{{ total }}</td>
        </tr>
        <tr>
          <td class="cell cell--void" colspan="3"></td>
          <td class="cell cell--head">제외시간</td>
          <td class="cell cell--center" :class="{ 'cell--negative': excluded < 0 }">{{ excluded }}</td>
        </tr>
        <tr class="sheet__spacer">
          <td class="cell cell--void" colspan="5"></td>
        </tr>

        <tr>
          <td class="cell cell--head">비고</td>
          <td class="cell cell--detail" colspan="4">{{ form.note }}</td>
        </tr>
        <tr class="sheet__spacer">
          <td class="cell cell--void" colspan="5"></td>
        </tr>

        <tr>
          <td class="cell cell--section" colspan="5">
            차주 진행 예정 {{ formatRange(form.nextWeekStart, form.nextWeekEnd) }}
          </td>
        </tr>
        <tr>
          <th class="cell cell--head">NO</th>
          <th class="cell cell--head">업무명</th>
          <th class="cell cell--head">업무상세</th>
          <th class="cell cell--head">진행상태</th>
          <th class="cell cell--head">소요시간(H)</th>
        </tr>
        <tr v-for="(item, index) in nextWeekItems" :key="item.key">
          <td class="cell cell--center">{{ index + 1 }}</td>
          <td class="cell">{{ item.taskName }}</td>
          <td class="cell cell--detail">{{ item.detail }}</td>
          <td class="cell cell--center">{{ item.status }}</td>
          <td class="cell cell--blocked"></td>
        </tr>
        <tr v-if="!nextWeekItems.length">
          <td class="cell cell--empty" colspan="5">차주 예정 항목이 없습니다</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.preview {
  overflow-x: auto;
  padding: 14px;
  background: #fff;
}

.sheet {
  width: 100%;
  min-width: 620px;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 11px;
  line-height: 1.5;
}

.cell {
  border: 1px solid #b7bcc4;
  padding: 4px 5px;
  vertical-align: middle;
  word-break: break-word;
  font-weight: 400;
}

.cell--void {
  border: 0;
}

.cell--title {
  text-align: center;
  font-size: 13px;
  padding: 7px 5px;
}

.cell--section {
  background: #1f3864;
  color: #fff;
  font-weight: 700;
  text-align: center;
  padding: 5px;
}

.cell--head {
  background: #bdd7ee;
  font-weight: 700;
  text-align: center;
}

.cell--center {
  text-align: center;
}

.cell--detail {
  white-space: pre-wrap;
  font-weight: 700;
}

.cell--blocked {
  background: #808080;
}

.cell--negative {
  color: var(--danger);
  font-weight: 700;
}

.cell--empty {
  text-align: center;
  color: var(--text-4);
  padding: 14px;
}

.sheet__spacer td {
  height: 12px;
}
</style>
