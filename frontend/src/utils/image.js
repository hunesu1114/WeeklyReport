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
