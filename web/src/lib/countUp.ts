/** Fast start, gentle landing: the number "settles" on its value instead of stopping abruptly. */
export function easeOutCubic(progress: number): number {
  const clamped = Math.min(1, Math.max(0, progress))
  return 1 - (1 - clamped) ** 3
}

/** Value shown `elapsedMs` into an animation from `from` to `to`. */
export function countUpValue(from: number, to: number, elapsedMs: number, durationMs: number): number {
  if (durationMs <= 0) return to
  return from + (to - from) * easeOutCubic(elapsedMs / durationMs)
}
