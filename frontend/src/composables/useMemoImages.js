import { computed, onBeforeUnmount, reactive } from 'vue'
import { memoApi } from '@/api/client'
import { prepareMemoImage } from '@/utils/image'
import { IMAGE_LIMITS, formatBytes, newImageId } from '@/utils/memoImage'

/**
 * 메모 그림의 화면 쪽 상태. 그림 아이디마다 한 칸:
 *
 *   { id, status, progress, preview, full, url, thumbUrl, width, height, contentType, byteSize, name, error }
 *
 * status 는 'preparing'(줄이는 중) · 'uploading' · 'ready' · 'failed'.
 * preview · full 은 올리는 동안 쓸 이 브라우저 안의 주소(blob:)다. 다 올린 뒤에도 남겨 둔다 —
 * 서버 주소로 갈아 끼우면 같은 그림을 한 번 더 받느라 칸이 잠깐 비어 보인다.
 *
 * 메모를 바꿔도 비우지 않는다. 올리던 것은 계속 올라가고, 그 메모로 돌아오면 그대로 보인다.
 * 한 장씩 차례로 올린다 — 여러 장을 한꺼번에 보내면 앞의 것도 늦게 끝난다.
 */
export function useMemoImages() {
  const images = reactive(new Map())
  /** 올릴 것. 실패하면 남겨 두었다가 '다시 올리기'로 그대로 보낸다. id → { memoId, source, prepared } */
  const jobs = new Map()
  const queue = []
  const objectUrls = new Set()
  let running = false

  const pending = computed(() => {
    let n = 0
    for (const entry of images.values()) if (entry.status === 'preparing' || entry.status === 'uploading') n += 1
    return n
  })

  function patch(id, values) {
    const current = images.get(id)
    if (current) images.set(id, { ...current, ...values })
  }

  function localUrl(blob) {
    const url = URL.createObjectURL(blob)
    objectUrls.add(url)
    return url
  }

  /** 서버가 준 그림 정보(메모를 열거나 저장한 응답)를 얹는다. 올리는 중인 것은 건드리지 않는다. */
  function merge(list) {
    for (const view of list ?? []) {
      const current = images.get(view.id)
      if (current && current.status !== 'ready' && jobs.has(view.id)) continue
      images.set(view.id, { ...current, ...view, status: 'ready', progress: 1, error: '' })
    }
  }

  /**
   * 그림 한 장을 받아 아이디를 정하고 올리기를 줄 세운다. 아이디는 바로 돌려준다 —
   * 부르는 쪽은 이 아이디로 토큰을 만들어 곧장 본문에 넣는다.
   */
  function add(memoId, file) {
    const id = newImageId()
    const url = localUrl(file)
    images.set(id, {
      id,
      status: 'preparing',
      progress: 0,
      preview: url,
      full: url,
      url: null,
      thumbUrl: null,
      width: 0,
      height: 0,
      contentType: file.type,
      byteSize: file.size,
      name: file.name,
      error: '',
    })
    jobs.set(id, { memoId, source: file, prepared: null })
    queue.push(id)
    pump()
    return id
  }

  function retry(id) {
    const entry = images.get(id)
    if (!jobs.has(id) || !entry || entry.status !== 'failed') return
    patch(id, { status: 'preparing', error: '', progress: 0 })
    queue.push(id)
    pump()
  }

  async function pump() {
    if (running) return
    running = true
    try {
      while (queue.length) await run(queue.shift())
    } finally {
      running = false
    }
  }

  async function run(id) {
    const job = jobs.get(id)
    if (!job || !images.has(id)) return
    try {
      if (!job.prepared) {
        const prepared = await prepareMemoImage(job.source)
        if (prepared.file.size > IMAGE_LIMITS.bytes) {
          throw new Error(`줄여도 ${formatBytes(prepared.file.size)}라 올릴 수 없습니다. 한 장에 5MB까지입니다.`)
        }
        job.prepared = prepared
        patch(id, {
          preview: localUrl(prepared.thumb),
          full: prepared.file === job.source ? images.get(id).full : localUrl(prepared.file),
          width: prepared.width,
          height: prepared.height,
          contentType: prepared.type,
          byteSize: prepared.file.size,
        })
      }

      patch(id, { status: 'uploading', progress: 0 })
      const { file, thumb, width, height } = job.prepared
      const view = await memoApi.uploadImage(
        job.memoId,
        { id, file, thumb, width, height, name: job.source.name },
        { onProgress: (ratio) => patch(id, { progress: ratio }) },
      )
      jobs.delete(id)
      patch(id, { ...view, status: 'ready', progress: 1, error: '' })
    } catch (error) {
      patch(id, { status: 'failed', error: error.message || '올리지 못했습니다.' })
    }
  }

  onBeforeUnmount(() => {
    for (const url of objectUrls) URL.revokeObjectURL(url)
    objectUrls.clear()
  })

  return { images, pending, merge, add, retry }
}
