import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { MuscleSets } from '../api/types'
import { t } from '../lib/i18n'
import { foldMuscleStats, MAP_MUSCLES, SIDE_MUSCLES, type BodySide, type MapMuscle } from '../lib/muscles'
import { BodyMap } from './BodyMap'

const week: MuscleSets[] = [
  { muscle: 'CHEST', sets: 12 },
  { muscle: 'TRICEPS', sets: 6 },
  { muscle: 'SIDE_DELTS', sets: 2.5 },
  { muscle: 'ADDUCTORS', sets: 3 },
]

function drawnMuscles(container: HTMLElement): string[] {
  return [...container.querySelectorAll<SVGGElement>('g[data-muscle]')].map((g) => g.dataset.muscle!)
}

function muscleGroup(container: HTMLElement, muscle: MapMuscle) {
  return container.querySelector<SVGGElement>(`g[data-muscle="${muscle}"]`)
}

afterEach(cleanup)

describe('BodyMap', () => {
  it.each(['front', 'back'] as BodySide[])('draws exactly the %s muscles, each mirrored onto both halves', (side) => {
    const { container } = render(<BodyMap side={side} muscles={new Map()} scope={7} />)
    expect(drawnMuscles(container).sort()).toEqual([...SIDE_MUSCLES[side]].sort())
    for (const muscle of SIDE_MUSCLES[side]) {
      const group = muscleGroup(container, muscle)!
      const paths = group.querySelectorAll('path')
      // Left-half paths plus their mirrored copies
      expect(paths.length).toBeGreaterThanOrEqual(2)
      expect(paths.length % 2).toBe(0)
    }
  })

  it('covers all 16 muscles across the two figures', () => {
    const front = render(<BodyMap side="front" muscles={new Map()} scope={7} />)
    const back = render(<BodyMap side="back" muscles={new Map()} scope={7} />)
    const all = new Set([...drawnMuscles(front.container), ...drawnMuscles(back.container)])
    expect(all.size).toBe(MAP_MUSCLES.length)
  })

  it('puts chest on the front and lats on the back only', () => {
    const front = render(<BodyMap side="front" muscles={new Map()} scope={7} />).container
    expect(muscleGroup(front, 'CHEST')).not.toBeNull()
    expect(muscleGroup(front, 'LATS')).toBeNull()
    cleanup()
    const back = render(<BodyMap side="back" muscles={new Map()} scope={7} />).container
    expect(muscleGroup(back, 'LATS')).not.toBeNull()
    expect(muscleGroup(back, 'CHEST')).toBeNull()
  })

  it('colours each muscle by its band', () => {
    const muscles = foldMuscleStats(week)
    const { container } = render(<BodyMap side="front" muscles={muscles} scope={7} />)
    expect(muscleGroup(container, 'CHEST')!.dataset.band).toBe('high')
    expect(muscleGroup(container, 'SIDE_DELTS')!.dataset.band).toBe('low')
    // Adductors fold into the quads: 3 sets is still light for a week
    expect(muscleGroup(container, 'QUADS')!.dataset.band).toBe('low')
    expect(muscleGroup(container, 'ABS')!.dataset.band).toBe('none')
  })

  it('uses the single-workout thresholds for a summary', () => {
    const { container } = render(<BodyMap side="back" muscles={foldMuscleStats(week)} scope="workout" />)
    expect(muscleGroup(container, 'SIDE_DELTS')!.dataset.band).toBe('mid')
    expect(muscleGroup(container, 'TRICEPS')!.dataset.band).toBe('high')
  })

  it('names each muscle with its sets, and is a picture when not tappable', () => {
    render(<BodyMap side="front" muscles={foldMuscleStats(week)} scope={7} />)
    expect(screen.getByRole('img', { name: t.muscleAria(t.muscleNames.CHEST, t.muscleSets('12')) })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: t.muscleAria(t.muscleNames.ABS, t.muscleNotTrained) })).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('selects a muscle by tap or keyboard', async () => {
    const onSelect = vi.fn()
    const user = userEvent.setup()
    render(<BodyMap side="back" muscles={foldMuscleStats(week)} scope={7} selected="TRICEPS" onSelect={onSelect} />)

    const triceps = screen.getByRole('button', { name: t.muscleAria(t.muscleNames.TRICEPS, t.muscleSets('6')) })
    expect(triceps).toHaveAttribute('aria-pressed', 'true')

    await user.click(screen.getByRole('button', { name: new RegExp(t.muscleNames.LATS) }))
    expect(onSelect).toHaveBeenLastCalledWith('LATS')

    // Tab order follows the drawing order: lats, then the lower back
    await user.tab()
    expect(screen.getByRole('button', { name: new RegExp(t.muscleNames.LOWER_BACK) })).toHaveFocus()
    await user.keyboard('{Enter}')
    expect(onSelect).toHaveBeenLastCalledWith('LOWER_BACK')
    await user.keyboard(' ')
    expect(onSelect).toHaveBeenCalledTimes(3)
  })
})
