/** 프로필 사진 한 변의 길이(px). 화면에서 가장 크게 쓰는 곳이 96px 이라 여유 있다. */
const AVATAR_SIZE = 256

/**
 * 고른 이미지를 정사각형으로 잘라 줄인다.
 *
 * <p>원본을 그대로 올리면 휴대폰 사진 한 장이 수 MB 다. 아바타는 원형으로
 * 잘려 나오므로 가운데를 정사각형으로 따고 {@link AVATAR_SIZE} 로 맞춘다.
 * 서버로는 줄인 결과만 올라간다.
 *
 * @returns {Promise<File>} 줄인 이미지
 */
export async function resizeAvatar(file) {
  const bitmap = await loadImage(file)

  // 짧은 변을 기준으로 가운데를 정사각형으로 딴다
  const side = Math.min(bitmap.width, bitmap.height)
  const sx = (bitmap.width - side) / 2
  const sy = (bitmap.height - side) / 2

  const canvas = document.createElement('canvas')
  canvas.width = AVATAR_SIZE
  canvas.height = AVATAR_SIZE
  const ctx = canvas.getContext('2d')
  ctx.imageSmoothingQuality = 'high'

  // PNG 는 투명한 부분이 있을 수 있어 그대로 두고, 나머지는 JPEG 로 내보낸다
  const keepAlpha = file.type === 'image/png'
  if (!keepAlpha) {
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, AVATAR_SIZE, AVATAR_SIZE)
  }
  ctx.drawImage(bitmap, sx, sy, side, side, 0, 0, AVATAR_SIZE, AVATAR_SIZE)
  bitmap.close?.()

  const type = keepAlpha ? 'image/png' : 'image/jpeg'
  const blob = await new Promise((resolve, reject) => {
    canvas.toBlob(
      (result) => (result ? resolve(result) : reject(new Error('이미지를 변환하지 못했습니다.'))),
      type,
      0.9,
    )
  })

  return new File([blob], keepAlpha ? 'avatar.png' : 'avatar.jpg', { type })
}

/** 메모 그림의 긴 변. 화면 너비 760px 단의 2배보다 넉넉하고, 스크린샷 글자가 읽힌다. */
const MEMO_LONG_SIDE = 2000
/** 섬네일의 긴 변. 그림 줄 칸(112×70)을 고해상도 화면에서 그려도 흐리지 않다. */
const THUMB_LONG_SIDE = 320
/** 이보다 작은 PNG 는 다시 굽지 않는다. 스크린샷 글자가 뭉개지지 않게. */
const KEEP_PNG_BYTES = 1.5 * 1024 * 1024

/**
 * 메모에 넣을 그림을 올리기 좋게 만든다. 서버는 그림을 고치지 않으므로 여기서 다 한다.
 *
 * - 긴 변을 2000px 로 줄인다. 휴대폰 사진 한 장이 수 MB 라서다.
 * - 작은 PNG(스크린샷)는 그대로 둔다. 다시 구우면 글자가 흐려진다.
 * - GIF 는 그대로 둔다. 캔버스를 거치면 움직임이 사라진다.
 * - 나머지는 WEBP 로, 못 만드는 브라우저는 JPEG 로 다시 굽는다. 이때 사진에 붙은
 *   위치 정보(EXIF)가 함께 빠진다.
 *
 * @returns {Promise<{ file: Blob, thumb: Blob, width: number, height: number, type: string }>}
 */
export async function prepareMemoImage(file) {
  const bitmap = await loadImage(file)
  try {
    const width = bitmap.width
    const height = bitmap.height
    if (!width || !height) throw new Error('그림 크기를 읽지 못했습니다.')

    const thumb = await encode(bitmap, fit(width, height, THUMB_LONG_SIDE), ['image/webp', 'image/jpeg'], 0.8)
    const longSide = Math.max(width, height)

    if (file.type === 'image/gif' || (file.type === 'image/png' && longSide <= MEMO_LONG_SIDE && file.size <= KEEP_PNG_BYTES)) {
      return { file, thumb, width, height, type: file.type }
    }

    const size = fit(width, height, MEMO_LONG_SIDE)
    let out
    if (file.type === 'image/png') {
      // 스크린샷은 PNG 로 줄여 보고, 그래도 크면 그때 WEBP 로 간다
      out = await encode(bitmap, size, ['image/png'])
      if (out.size > KEEP_PNG_BYTES) out = await encode(bitmap, size, ['image/webp', 'image/png'], 0.92)
    } else {
      out = await encode(bitmap, size, ['image/webp', 'image/jpeg'], 0.86)
    }
    return { file: out, thumb, width: size.width, height: size.height, type: out.type }
  } finally {
    bitmap.close?.()
  }
}

function fit(width, height, longSide) {
  const scale = Math.min(1, longSide / Math.max(width, height))
  return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) }
}

/**
 * 캔버스에 그려 원하는 형식으로 굽는다. 형식을 못 만드는 브라우저는 조용히 PNG 를 주므로,
 * 돌아온 형식을 보고 다음 후보로 넘어간다. JPEG 는 투명한 곳이 검게 나오니 흰 바탕을 깐다.
 */
async function encode(bitmap, { width, height }, types, quality) {
  for (const type of types) {
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const ctx = canvas.getContext('2d')
    ctx.imageSmoothingQuality = 'high'
    if (type === 'image/jpeg') {
      ctx.fillStyle = '#ffffff'
      ctx.fillRect(0, 0, width, height)
    }
    ctx.drawImage(bitmap, 0, 0, width, height)
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, type, quality))
    if (blob && blob.type === type) return blob
  }
  throw new Error('그림을 변환하지 못했습니다.')
}

/** createImageBitmap 이 없는 브라우저를 위해 <img> 로도 읽을 수 있게 둔다. */
async function loadImage(file) {
  if (typeof createImageBitmap === 'function') {
    try {
      return await createImageBitmap(file)
    } catch {
      /* 아래 경로로 내려간다 */
    }
  }
  const url = URL.createObjectURL(file)
  try {
    return await new Promise((resolve, reject) => {
      const img = new Image()
      img.onload = () => resolve(img)
      img.onerror = () => reject(new Error('이미지를 읽지 못했습니다.'))
      img.src = url
    })
  } finally {
    URL.revokeObjectURL(url)
  }
}
