/**
 * Saving a file the app fetched itself (the API needs the bearer token, so a plain link cannot work).
 *
 * - iPhone/iPad: Safari, and above all the home-screen app, ignore or mishandle `<a download>` with a
 *   blob URL, so the file goes to the share sheet instead ("Save to Files", Mail, AirDrop...).
 * - Everywhere else: a temporary `<a download>` link, the normal browser download. Desktop Chrome can
 *   share files too, but a download is what people expect there.
 */

export type SaveOutcome =
  /** The share sheet opened and the person picked a target. */
  | 'shared'
  /** The share sheet was closed without choosing anything: not an error. */
  | 'cancelled'
  /** The browser started a regular download. */
  | 'downloaded'
  /**
   * Safari refused to open the share sheet because too much time passed since the tap (the download
   * took a while). A second tap on a "ready" button opens it.
   */
  | 'needsTap'

/** iPhone, iPod, or an iPad (which reports itself as a Mac but has a touch screen). */
export function isAppleMobile(nav: Navigator = navigator): boolean {
  return /iPhone|iPad|iPod/.test(nav.userAgent) || (/Macintosh/.test(nav.userAgent) && nav.maxTouchPoints > 1)
}

function canShareFile(file: File, nav: Navigator): boolean {
  try {
    return typeof nav.share === 'function' && typeof nav.canShare === 'function' && nav.canShare({ files: [file] })
  } catch {
    return false
  }
}

export async function saveFile(blob: Blob, filename: string, nav: Navigator = navigator): Promise<SaveOutcome> {
  const file = new File([blob], filename, { type: blob.type || 'application/octet-stream' })
  if (isAppleMobile(nav) && canShareFile(file, nav)) {
    try {
      await nav.share({ files: [file] })
      return 'shared'
    } catch (error) {
      const name = (error as Error).name
      if (name === 'AbortError') return 'cancelled'
      if (name === 'NotAllowedError') return 'needsTap'
      throw error
    }
  }
  downloadWithLink(blob, filename)
  return 'downloaded'
}

function downloadWithLink(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.rel = 'noopener'
  link.style.display = 'none'
  // Firefox only follows links that are in the document
  document.body.appendChild(link)
  link.click()
  link.remove()
  // Revoking at once can cancel the download before the browser has read the blob
  setTimeout(() => URL.revokeObjectURL(url), 30_000)
}
