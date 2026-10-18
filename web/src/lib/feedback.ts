// Haptics and sounds. Safari on iPhone ignores navigator.vibrate, so a short tone is the reliable signal there.

export function vibrate(pattern: number | number[]) {
  if ('vibrate' in navigator) navigator.vibrate(pattern)
}

let audioContext: AudioContext | null = null

/**
 * iOS only lets a page play sound after a user gesture, so this is called once from a tap
 * (e.g. saving a set) to unlock audio before the rest timer needs it.
 */
export function unlockAudio() {
  audioContext ??= new AudioContext()
  if (audioContext.state === 'suspended') void audioContext.resume()
}

export function beep(times = 3) {
  if (!audioContext) return
  const start = audioContext.currentTime
  for (let i = 0; i < times; i++) {
    const oscillator = audioContext.createOscillator()
    const gain = audioContext.createGain()
    oscillator.frequency.value = 880
    gain.gain.setValueAtTime(0.0001, start + i * 0.3)
    gain.gain.exponentialRampToValueAtTime(0.3, start + i * 0.3 + 0.02)
    gain.gain.exponentialRampToValueAtTime(0.0001, start + i * 0.3 + 0.2)
    oscillator.connect(gain).connect(audioContext.destination)
    oscillator.start(start + i * 0.3)
    oscillator.stop(start + i * 0.3 + 0.22)
  }
}
