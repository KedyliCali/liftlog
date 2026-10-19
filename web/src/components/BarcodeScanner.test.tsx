import { act, cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { t } from '../lib/i18n'
import { BarcodeScanner } from './BarcodeScanner'

// Stand-in for ZXing: the test decides when the camera opens, fails, or sees a code
type Callback = (result: { getText: () => string } | undefined, error: unknown, controls: unknown) => void
const zxing = vi.hoisted(() => ({
  decode: vi.fn(),
  constructed: vi.fn(),
}))
vi.mock('@zxing/browser', () => ({
  BrowserMultiFormatOneDReader: class {
    constructor(...args: unknown[]) {
      zxing.constructed(...args)
    }
    decodeFromConstraints(...args: unknown[]) {
      return zxing.decode(...args)
    }
  },
}))

function fakeControls(withTorch: boolean) {
  return { stop: vi.fn(), ...(withTorch ? { switchTorch: vi.fn(async () => {}) } : {}) }
}

function stubCamera(available: boolean) {
  Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: available ? { getUserMedia: vi.fn() } : undefined })
}

beforeEach(() => {
  zxing.decode.mockReset()
  zxing.constructed.mockReset()
  stubCamera(true)
})

afterEach(() => {
  cleanup()
  stubCamera(false)
})

describe('BarcodeScanner', () => {
  it('opens the rear camera, reports a scanned code once, and stops the camera', async () => {
    const controls = fakeControls(false)
    let deliver: Callback = () => {}
    zxing.decode.mockImplementation(async (_constraints, _video, callback: Callback) => {
      deliver = callback
      return controls
    })
    const onDetected = vi.fn()
    render(<BarcodeScanner onDetected={onDetected} onBack={() => {}} />)

    expect(screen.getByText(t.cameraStarting)).toBeInTheDocument()
    await screen.findByText(t.scanHint)
    const [constraints, video] = zxing.decode.mock.calls[0]
    expect(constraints).toMatchObject({ audio: false, video: { facingMode: { ideal: 'environment' } } })
    expect(video).toBeInstanceOf(HTMLVideoElement)
    expect((video as HTMLVideoElement).hasAttribute('playsinline')).toBe(true)
    // No torch support reported: no torch button
    expect(screen.queryByRole('button', { name: t.torch })).not.toBeInTheDocument()

    act(() => deliver({ getText: () => '12AB' }, undefined, controls))
    expect(onDetected).not.toHaveBeenCalled()
    act(() => deliver({ getText: () => '8690504011234' }, undefined, controls))
    act(() => deliver({ getText: () => '8690504011234' }, undefined, controls))
    expect(onDetected).toHaveBeenCalledTimes(1)
    expect(onDetected).toHaveBeenCalledWith('8690504011234')
    expect(controls.stop).toHaveBeenCalled()
  })

  it('stops the camera when closed', async () => {
    const controls = fakeControls(false)
    zxing.decode.mockResolvedValue(controls)
    const onBack = vi.fn()
    const view = render(<BarcodeScanner onDetected={() => {}} onBack={onBack} />)
    await screen.findByText(t.scanHint)

    await userEvent.click(screen.getByRole('button', { name: t.closeScanner }))
    expect(onBack).toHaveBeenCalled()
    view.unmount()
    expect(controls.stop).toHaveBeenCalledTimes(1)
  })

  it('releases a camera that opens after the scanner was already closed', async () => {
    const controls = fakeControls(false)
    let open: (c: unknown) => void = () => {}
    zxing.decode.mockReturnValue(new Promise((resolve) => (open = resolve)))
    const view = render(<BarcodeScanner onDetected={() => {}} onBack={() => {}} />)
    await waitFor(() => expect(zxing.decode).toHaveBeenCalled())
    view.unmount()
    await act(async () => open(controls))
    expect(controls.stop).toHaveBeenCalled()
  })

  it('shows the torch toggle only when the camera supports it', async () => {
    const controls = fakeControls(true)
    zxing.decode.mockResolvedValue(controls)
    render(<BarcodeScanner onDetected={() => {}} onBack={() => {}} />)
    const torch = await screen.findByRole('button', { name: t.torch })
    expect(torch).toHaveAttribute('aria-pressed', 'false')

    await userEvent.click(torch)
    expect(controls.switchTorch).toHaveBeenCalledWith(true)
    expect(torch).toHaveAttribute('aria-pressed', 'true')
    await userEvent.click(torch)
    expect(controls.switchTorch).toHaveBeenLastCalledWith(false)
  })

  it('falls back to typing the code when the camera permission is refused', async () => {
    zxing.decode.mockRejectedValue(Object.assign(new Error('denied'), { name: 'NotAllowedError' }))
    const onDetected = vi.fn()
    render(<BarcodeScanner onDetected={onDetected} onBack={() => {}} />)
    expect(await screen.findByText(t.cameraDeniedTitle)).toBeInTheDocument()

    const input = screen.getByLabelText(t.barcodeManualLabel)
    await userEvent.type(input, '1234')
    await userEvent.click(screen.getByRole('button', { name: t.lookUp }))
    expect(screen.getByText(t.errorBarcode)).toBeInTheDocument()
    expect(onDetected).not.toHaveBeenCalled()

    // Letters and spaces are dropped as they are typed
    await userEvent.type(input, ' 5678x')
    await userEvent.click(screen.getByRole('button', { name: t.lookUp }))
    expect(onDetected).toHaveBeenCalledWith('12345678')
  })

  it('goes straight to the typed fallback without a camera API', async () => {
    stubCamera(false)
    render(<BarcodeScanner onDetected={() => {}} onBack={() => {}} />)
    expect(screen.getByText(t.cameraUnavailableTitle)).toBeInTheDocument()
    expect(screen.getByLabelText(t.barcodeManualLabel)).toHaveFocus()
    expect(zxing.decode).not.toHaveBeenCalled()
  })

  it('treats a missing camera as unavailable rather than refused', async () => {
    zxing.decode.mockRejectedValue(Object.assign(new Error('none'), { name: 'NotFoundError' }))
    render(<BarcodeScanner onDetected={() => {}} onBack={() => {}} />)
    expect(await screen.findByText(t.cameraUnavailableTitle)).toBeInTheDocument()
  })
})
