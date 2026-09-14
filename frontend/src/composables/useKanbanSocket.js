import { onBeforeUnmount, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'

/**
 * 칸반 실시간 갱신용 WebSocket.
 *
 * <p>서버는 "무엇이 바뀌었다"만 보낸다. 바뀐 내용은 싣지 않는다. 받은 쪽은
 * REST 로 다시 읽는다 — 이벤트 하나를 놓쳐도 다음 신호에 복구되고, 사람마다
 * 보여줄 것이 다른 문제도 조회 시점에 풀린다.
 *
 * <p>프로토콜:
 * <pre>
 *   →  {"type":"auth","token":"..."}
 *   ←  {"type":"ready"}
 *   →  {"type":"subscribe","projectId":3}
 *   ←  {"type":"board-changed","projectId":3,...}
 * </pre>
 *
 * 토큰을 첫 메시지로 보내는 이유: 브라우저 WebSocket 은 헤더를 붙일 수 없고,
 * 쿼리스트링에 실으면 nginx 액세스 로그에 토큰이 그대로 남는다.
 */
export function useKanbanSocket(handlers = {}) {
  const auth = useAuthStore()

  const connected = ref(false)
  let socket = null
  let pendingProjectId = null
  let retryDelay = 1000
  let retryTimer = null
  let pingTimer = null
  let closedByUs = false

  function url() {
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws'
    return `${scheme}://${location.host}/ws/kanban`
  }

  function connect() {
    if (!auth.token || socket) return
    closedByUs = false

    try {
      socket = new WebSocket(url())
    } catch {
      scheduleRetry()
      return
    }

    socket.onopen = () => {
      send({ type: 'auth', token: auth.token })
    }

    socket.onmessage = (event) => {
      let data
      try {
        data = JSON.parse(event.data)
      } catch {
        return
      }
      if (data.type === 'ready') {
        connected.value = true
        retryDelay = 1000
        if (pendingProjectId) send({ type: 'subscribe', projectId: pendingProjectId })
        return
      }
      // 내가 일으킨 변경으로 내 화면을 다시 그릴 필요는 없다.
      // 이미 저장 직후에 새로 읽었고, 두 번 그리면 스크롤이 튄다.
      if (data.actorId && data.actorId === auth.user?.id) return

      handlers[data.type]?.(data)
    }

    socket.onclose = () => {
      connected.value = false
      socket = null
      stopPing()
      if (!closedByUs) scheduleRetry()
    }

    socket.onerror = () => {
      // onclose 가 이어서 불린다. 여기서는 아무것도 하지 않는다.
    }

    startPing()
  }

  function scheduleRetry() {
    clearTimeout(retryTimer)
    // 서버가 잠깐 내려간 사이 브라우저 수십 개가 몰려들지 않게 점점 늦춘다
    retryTimer = setTimeout(connect, retryDelay)
    retryDelay = Math.min(retryDelay * 2, 30000)
  }

  /** 조용한 연결을 프록시가 끊지 않도록 주기적으로 한 번씩 오간다. */
  function startPing() {
    stopPing()
    pingTimer = setInterval(() => send({ type: 'ping' }), 45000)
  }

  function stopPing() {
    clearInterval(pingTimer)
    pingTimer = null
  }

  function send(payload) {
    if (socket?.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify(payload))
    }
  }

  /** 보고 있는 보드를 바꾼다. 연결 전이면 준비되는 대로 보낸다. */
  function watchProject(projectId) {
    pendingProjectId = projectId ?? null
    if (!projectId) {
      send({ type: 'unsubscribe' })
      return
    }
    if (!socket) connect()
    else if (connected.value) send({ type: 'subscribe', projectId })
  }

  function close() {
    closedByUs = true
    clearTimeout(retryTimer)
    stopPing()
    socket?.close()
    socket = null
    connected.value = false
  }

  onBeforeUnmount(close)

  return { connected, connect, watchProject, close }
}
