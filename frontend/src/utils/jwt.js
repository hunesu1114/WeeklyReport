/**
 * 토큰의 만료 시각만 읽는다. 서명은 확인하지 않는다.
 *
 * 검증은 서버가 한다. 여기서는 "이미 지난 토큰으로 서버를 찔러 401 을 받아오는"
 * 헛걸음을 줄이려는 것뿐이라, 페이로드를 그대로 믿어도 문제가 없다.
 * 누가 페이로드를 위조해 exp 를 늘려도 서버에서 막힌다.
 */
export function decodePayload(token) {
  try {
    const part = String(token).split('.')[1]
    if (!part) return null

    // JWT 는 base64url 이다. atob 가 읽을 수 있게 되돌리고 패딩을 채운다.
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=')

    // atob 는 바이트를 주므로 UTF-8 로 다시 읽어야 한글 클레임이 깨지지 않는다
    const json = decodeURIComponent(
      Array.from(atob(padded), (c) => `%${c.charCodeAt(0).toString(16).padStart(2, '0')}`).join(''),
    )
    return JSON.parse(json)
  } catch {
    return null
  }
}

/** 만료 시각(ms). 알 수 없으면 null. */
export function expiresAt(token) {
  const exp = decodePayload(token)?.exp
  return typeof exp === 'number' ? exp * 1000 : null
}

/**
 * 이미 만료됐는지. exp 를 읽을 수 없으면 판단하지 않고 false 를 준다.
 * 확신이 없을 때 멋대로 로그아웃시키는 것보다 서버에 물어보는 편이 낫다.
 *
 * @param leewaySeconds 시계 오차만큼 미리 만료로 본다.
 */
export function isExpired(token, leewaySeconds = 5) {
  const at = expiresAt(token)
  if (at === null) return false
  return at <= Date.now() + leewaySeconds * 1000
}
