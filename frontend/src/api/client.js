import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 20000,
})

/** 서버가 내려주는 message 를 사람이 읽을 수 있는 에러로 바꾼다. */
http.interceptors.response.use(
  (response) => response,
  (error) => {
    const data = error.response?.data
    let message = data?.message || error.message || '알 수 없는 오류가 발생했습니다.'
    if (data?.fields) {
      message += ' (' + Object.entries(data.fields).map(([k, v]) => `${k}: ${v}`).join(', ') + ')'
    }
    return Promise.reject(new Error(message))
  },
)

export const reportApi = {
  list: (params) => http.get('/reports', { params }).then((r) => r.data),
  defaults: (params) => http.get('/reports/defaults', { params }).then((r) => r.data),
  get: (id) => http.get(`/reports/${id}`).then((r) => r.data),
  create: (payload) => http.post('/reports', payload).then((r) => r.data),
  update: (id, payload) => http.put(`/reports/${id}`, payload).then((r) => r.data),
  remove: (id) => http.delete(`/reports/${id}`),
  followUp: (id) => http.post(`/reports/${id}/follow-up`).then((r) => r.data),

  /** 엑셀을 내려받는다. 파일명은 Content-Disposition 에서 뽑아 쓴다. */
  async download(id, templateKey) {
    const response = await http.get(`/reports/${id}/export`, {
      params: templateKey ? { templateKey } : undefined,
      responseType: 'blob',
    })
    const filename =
      parseFilename(response.headers['content-disposition']) || `weekly-report-${id}.xlsx`
    saveBlob(response.data, filename)
    return filename
  },
}

export const metaApi = {
  templates: () => http.get('/meta/templates').then((r) => r.data),
  statuses: () => http.get('/meta/statuses').then((r) => r.data),
  taskNames: (query) => http.get('/meta/task-names', { params: { query } }).then((r) => r.data),
  authors: () => http.get('/meta/authors').then((r) => r.data),
}

function parseFilename(disposition) {
  if (!disposition) return null
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  if (utf8) return decodeURIComponent(utf8[1])
  const plain = /filename="?([^";]+)"?/i.exec(disposition)
  return plain ? plain[1] : null
}

function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
