import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { filenameFromDisposition } from '../api/client'
import { isAppleMobile, saveFile } from './download'

const IPHONE = 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1'
const IPAD_AS_MAC = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15'
const DESKTOP_CHROME = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36'

function fakeNavigator(userAgent: string, sharing: { canShare?: boolean; share?: () => Promise<void> } = {}, maxTouchPoints = 0) {
  const share = vi.fn<(data: ShareData) => Promise<void>>(sharing.share ?? (async () => {}))
  const canShare = vi.fn(() => sharing.canShare ?? false)
  const nav = { userAgent, maxTouchPoints, ...(sharing.canShare === undefined ? {} : { share, canShare }) } as unknown as Navigator
  return { nav, share, canShare }
}

function domError(name: string) {
  return Object.assign(new Error(name), { name })
}

const csv = new Blob(['﻿Tarih;Yemek\r\n'], { type: 'text/csv' })

describe('saveFile', () => {
  let click: ReturnType<typeof vi.spyOn>
  let clickedLinks: HTMLAnchorElement[]

  beforeEach(() => {
    vi.useFakeTimers()
    clickedLinks = []
    // jsdom cannot navigate, so record the link instead of following it
    click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      clickedLinks.push(this)
    })
    URL.createObjectURL = vi.fn(() => 'blob:liftlog/1')
    URL.revokeObjectURL = vi.fn()
  })

  afterEach(() => {
    click.mockRestore()
    vi.useRealTimers()
  })

  it('opens the share sheet with the file on an iPhone', async () => {
    const { nav, share, canShare } = fakeNavigator(IPHONE, { canShare: true })

    await expect(saveFile(csv, 'liftlog-antrenman-2026-10-19.csv', nav)).resolves.toBe('shared')

    const shared = share.mock.calls[0][0]
    expect(shared.files).toHaveLength(1)
    expect(shared.files![0].name).toBe('liftlog-antrenman-2026-10-19.csv')
    expect(shared.files![0].type).toBe('text/csv')
    expect(canShare).toHaveBeenCalled()
    expect(clickedLinks).toHaveLength(0)
    expect(URL.createObjectURL).not.toHaveBeenCalled()
  })

  it('treats an iPad that reports itself as a Mac like an iPhone', async () => {
    const { nav, share } = fakeNavigator(IPAD_AS_MAC, { canShare: true }, 5)
    expect(isAppleMobile(nav)).toBe(true)

    await saveFile(csv, 'a.csv', nav)
    expect(share).toHaveBeenCalledOnce()
  })

  it('downloads through a temporary link when files cannot be shared', async () => {
    const { nav } = fakeNavigator(IPHONE, { canShare: false })

    await expect(saveFile(csv, 'liftlog-beslenme-2026-10-19.csv', nav)).resolves.toBe('downloaded')

    expect(clickedLinks).toHaveLength(1)
    expect(clickedLinks[0].download).toBe('liftlog-beslenme-2026-10-19.csv')
    expect(clickedLinks[0].href).toBe('blob:liftlog/1')
    expect(document.body.contains(clickedLinks[0])).toBe(false)
    // The object URL lives on long enough for the download to start, then is released
    expect(URL.revokeObjectURL).not.toHaveBeenCalled()
    vi.runAllTimers()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:liftlog/1')
  })

  it('downloads through a link in browsers without the Web Share API', async () => {
    const { nav } = fakeNavigator(IPHONE)
    await expect(saveFile(csv, 'a.csv', nav)).resolves.toBe('downloaded')
    expect(clickedLinks).toHaveLength(1)
  })

  it('downloads on desktop even when the browser could share files', async () => {
    const { nav, share } = fakeNavigator(DESKTOP_CHROME, { canShare: true })

    await expect(saveFile(csv, 'a.zip', nav)).resolves.toBe('downloaded')
    expect(share).not.toHaveBeenCalled()
    expect(clickedLinks).toHaveLength(1)
  })

  it('closing the share sheet is a cancel, not an error, and never falls back to a link', async () => {
    const { nav } = fakeNavigator(IPHONE, { canShare: true, share: () => Promise.reject(domError('AbortError')) })
    await expect(saveFile(csv, 'a.csv', nav)).resolves.toBe('cancelled')
    expect(clickedLinks).toHaveLength(0)
  })

  it('asks for another tap when Safari says the tap is too old', async () => {
    const { nav } = fakeNavigator(IPHONE, { canShare: true, share: () => Promise.reject(domError('NotAllowedError')) })
    await expect(saveFile(csv, 'a.csv', nav)).resolves.toBe('needsTap')
  })

  it('passes other share failures on', async () => {
    const { nav } = fakeNavigator(IPHONE, { canShare: true, share: () => Promise.reject(domError('DataError')) })
    await expect(saveFile(csv, 'a.csv', nav)).rejects.toThrow('DataError')
  })
})

describe('filenameFromDisposition', () => {
  it('reads the quoted, bare and RFC 5987 forms', () => {
    expect(filenameFromDisposition('attachment; filename="liftlog-antrenman-2026-10-19.csv"')).toBe('liftlog-antrenman-2026-10-19.csv')
    expect(filenameFromDisposition('attachment; filename=liftlog.zip')).toBe('liftlog.zip')
    expect(filenameFromDisposition("attachment; filename*=UTF-8''beslenme%20%C3%B6zet.csv")).toBe('beslenme özet.csv')
  })

  it('is null without a usable name', () => {
    expect(filenameFromDisposition(null)).toBeNull()
    expect(filenameFromDisposition('attachment')).toBeNull()
    expect(filenameFromDisposition('attachment; filename=""')).toBeNull()
  })
})
