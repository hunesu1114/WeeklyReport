import { defineStore } from 'pinia'
import { ref } from 'vue'
import { metaApi } from '@/api/client'

/** 양식 목록 / 상태 후보 / 업무명 자동완성처럼 화면 전체가 공유하는 값. */
export const useMetaStore = defineStore('meta', () => {
  const templates = ref([])
  const statuses = ref(['진행', '완료', '예정', '보류', '취소'])
  const taskNames = ref([])
  const authors = ref([])
  const loaded = ref(false)

  async function load(force = false) {
    if (loaded.value && !force) return
    const [t, s, n, a] = await Promise.all([
      metaApi.templates(),
      metaApi.statuses(),
      metaApi.taskNames(),
      metaApi.authors(),
    ])
    templates.value = t
    statuses.value = s.length ? s : statuses.value
    taskNames.value = n
    authors.value = a
    loaded.value = true
  }

  return { templates, statuses, taskNames, authors, loaded, load }
})
