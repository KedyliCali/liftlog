import type { IScannerControls } from '@zxing/browser'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { vibrate } from '../lib/feedback'
import { t } from '../lib/i18n'
import { isValidBarcode } from '../lib/openFoodFacts'

type CameraState = 'starting' | 'scanning' | 'denied' | 'unavailable'
type TorchState = 'unsupported' | 'off' | 'on'

/** Rear camera; 720p is plenty for a barcode and keeps decoding fast on older iPhones. */
const CAMERA_CONSTRAINTS: MediaStreamConstraints = {
  audio: false,
  video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } },
}

const canUseCamera = () => typeof navigator !== 'undefined' && typeof navigator.mediaDevices?.getUserMedia === 'function'

/** A refused permission is the user's choice; anything else means there is no camera we can use. */
function stateForError(error: unknown): CameraState {
  const name = (error as { name?: string } | null)?.name
  return name === 'NotAllowedError' || name === 'SecurityError' ? 'denied' : 'unavailable'
}

/** With the torch on, the library's stop() also turns it off and returns a promise that may reject. */
function stopQuietly(controls: IScannerControls | null) {
  if (!controls) return
  try {
    void Promise.resolve(controls.stop() as unknown).catch(() => {})
  } catch {
    // Already stopped
  }
}

/**
 * Camera barcode reader with a typed-in fallback. iOS Safari has no BarcodeDetector, so decoding runs in
 * JavaScript (ZXing) on frames of the video. The library is loaded only when the scanner opens.
 */
export function BarcodeScanner({ onDetected, onBack }: { onDetected: (code: string) => void; onBack: () => void }) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const controlsRef = useRef<IScannerControls | null>(null)
  const onDetectedRef = useRef(onDetected)
  const [camera, setCamera] = useState<CameraState>(() => (canUseCamera() ? 'starting' : 'unavailable'))
  const [torch, setTorch] = useState<TorchState>('unsupported')
  const [manualCode, setManualCode] = useState('')
  const [manualError, setManualError] = useState(false)

  useEffect(() => {
    onDetectedRef.current = onDetected
  })

  useEffect(() => {
    if (!canUseCamera() || !videoRef.current) return
    const video = videoRef.current
    let cancelled = false
    let detected = false

    const start = async () => {
      try {
        const { BrowserMultiFormatOneDReader } = await import('@zxing/browser')
        const reader = new BrowserMultiFormatOneDReader(undefined, { delayBetweenScanAttempts: 120 })
        const controls = await reader.decodeFromConstraints(CAMERA_CONSTRAINTS, video, (result, _error, liveControls) => {
          const code = result?.getText()
          // Partial reads of a damaged code can come back as short or non-numeric text; wait for a clean one
          if (!code || detected || cancelled || !isValidBarcode(code)) return
          detected = true
          stopQuietly(liveControls)
          controlsRef.current = null
          vibrate(30)
          onDetectedRef.current(code)
        })
        // Closed while the permission prompt was up: release the camera at once
        if (cancelled || detected) {
          stopQuietly(controls)
          return
        }
        controlsRef.current = controls
        setCamera('scanning')
        if (controls.switchTorch) setTorch('off')
      } catch (error) {
        if (!cancelled) setCamera(stateForError(error))
      }
    }
    void start()

    return () => {
      cancelled = true
      stopQuietly(controlsRef.current)
      controlsRef.current = null
    }
  }, [])

  const toggleTorch = async () => {
    const switchTorch = controlsRef.current?.switchTorch
    if (!switchTorch || torch === 'unsupported') return
    const next = torch === 'on' ? 'off' : 'on'
    try {
      await switchTorch(next === 'on')
      setTorch(next)
    } catch {
      setTorch('unsupported')
    }
  }

  const submitManual = (event: FormEvent) => {
    event.preventDefault()
    if (!isValidBarcode(manualCode)) {
      setManualError(true)
      return
    }
    stopQuietly(controlsRef.current)
    controlsRef.current = null
    onDetected(manualCode)
  }

  const blocked = camera === 'denied' || camera === 'unavailable'

  return (
    <div className="scanner stack">
      <div className={`scanner-stage ${camera}`} data-testid="scanner-stage">
        {/* Clips the dimmed surround; the plate and the tag outside it may overhang the edges */}
        <div className="scanner-viewport">
          <video ref={videoRef} className="scanner-video" playsInline muted autoPlay aria-hidden />
          {!blocked && (
            <div className="scanner-frame" aria-hidden>
              <span className="corner tl" />
              <span className="corner tr" />
              <span className="corner bl" />
              <span className="corner br" />
              {camera === 'scanning' && <span className="scanner-laser" />}
            </div>
          )}
        </div>
        <span className="scanner-tag">EAN · UPC</span>
        <div className="scanner-controls">
          {torch !== 'unsupported' && (
            <button type="button" className={torch === 'on' ? 'scanner-btn on' : 'scanner-btn'} onClick={toggleTorch} aria-pressed={torch === 'on'} aria-label={t.torch}>
              <TorchIcon />
            </button>
          )}
          <button type="button" className="scanner-btn" onClick={onBack} aria-label={t.closeScanner}>
            ✕
          </button>
        </div>
        {camera === 'starting' && (
          <div className="scanner-message" role="status">
            <span className="spinner small" />
            {t.cameraStarting}
          </div>
        )}
        {blocked && (
          <div className="scanner-message" role="alert">
            <strong>{camera === 'denied' ? t.cameraDeniedTitle : t.cameraUnavailableTitle}</strong>
            <span>{camera === 'denied' ? t.cameraDeniedBody : t.cameraUnavailableBody}</span>
          </div>
        )}
      </div>
      {camera === 'scanning' && <p className="muted scanner-hint">{t.scanHint}</p>}

      <form className="scanner-manual" onSubmit={submitManual}>
        <div className={manualError ? 'field invalid' : 'field'}>
          <label htmlFor="barcode-manual">{t.barcodeManualLabel}</label>
          <div className="scanner-manual-row">
            <input
              id="barcode-manual"
              inputMode="numeric"
              autoComplete="off"
              placeholder="8690000000000"
              autoFocus={blocked}
              value={manualCode}
              onChange={(e) => {
                setManualCode(e.target.value.replace(/\D/g, '').slice(0, 14))
                setManualError(false)
              }}
            />
            <button type="submit" className="btn btn-primary">
              {t.lookUp}
            </button>
          </div>
          {manualError && <span className="field-error">{t.errorBarcode}</span>}
        </div>
      </form>
    </div>
  )
}

function TorchIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M7 2h10v4l-3 4v11a1 1 0 0 1-1 1h-2a1 1 0 0 1-1-1V10L7 6z" />
      <path d="M7 6h10" />
    </svg>
  )
}
