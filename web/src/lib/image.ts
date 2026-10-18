export const AVATAR_SIZE_PX = 256

/**
 * Center-crops a picked photo to a square and scales it to 256 px JPEG in the browser, so a 5 MB
 * iPhone photo uploads as ~30 KB. createImageBitmap honours EXIF orientation, so portraits stay upright.
 */
export async function toAvatarJpeg(file: File): Promise<Blob> {
  const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
  const side = Math.min(bitmap.width, bitmap.height)
  const canvas = document.createElement('canvas')
  canvas.width = AVATAR_SIZE_PX
  canvas.height = AVATAR_SIZE_PX
  const context = canvas.getContext('2d')
  if (!context) throw new Error('Canvas not supported')
  context.imageSmoothingQuality = 'high'
  context.drawImage(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side, 0, 0, AVATAR_SIZE_PX, AVATAR_SIZE_PX)
  bitmap.close()
  return new Promise((resolve, reject) =>
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('Encoding failed'))), 'image/jpeg', 0.85),
  )
}
