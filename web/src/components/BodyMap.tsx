import type { KeyboardEvent } from 'react'
import type { MuscleSets } from '../api/types'
import { t } from '../lib/i18n'
import { bandFor, formatSets, muscleName, SIDE_MUSCLES, type BandScope, type BodySide, type MapMuscle } from '../lib/muscles'
import { BODY_VIEWBOX, MUSCLE_SHAPES, SILHOUETTE_SHAPES } from './bodyMapShapes'

// Flips a left-half path onto the right half around the centre line x = 100
const MIRROR = 'matrix(-1 0 0 1 200 0)'

interface BodyMapProps {
  side: BodySide
  /** Folded sets per map muscle (see foldMuscleStats); missing muscles are drawn as not trained */
  muscles: ReadonlyMap<MapMuscle, MuscleSets>
  scope: BandScope
  selected?: MapMuscle | null
  /** Makes each muscle a button; without it the map is a picture */
  onSelect?: (muscle: MapMuscle) => void
  className?: string
}

/**
 * Front or back body figure with each muscle coloured by how much it was trained. Layered: a darker copy of
 * the silhouette sits offset behind the body, the neutral body (head, hands, joints) on it, the muscles on top
 * with thin separator strokes.
 */
export function BodyMap({ side, muscles, scope, selected = null, onSelect, className }: BodyMapProps) {
  return (
    <svg
      viewBox={BODY_VIEWBOX}
      className={className ? `body-map ${className}` : 'body-map'}
      role="group"
      aria-label={t.muscleMapFigure(side)}
      data-side={side}
    >
      <g className="body-map-shadow" transform="translate(5 6)" aria-hidden>
        <Halves paths={SILHOUETTE_SHAPES} />
      </g>
      <g className="body-map-body" aria-hidden>
        <Halves paths={SILHOUETTE_SHAPES} />
      </g>
      {SIDE_MUSCLES[side].map((muscle) => (
        <MuscleShape
          key={muscle}
          muscle={muscle}
          paths={MUSCLE_SHAPES[side][muscle] ?? []}
          sets={muscles.get(muscle)?.sets ?? 0}
          scope={scope}
          selected={muscle === selected}
          onSelect={onSelect}
        />
      ))}
    </svg>
  )
}

interface MuscleShapeProps {
  muscle: MapMuscle
  paths: string[]
  sets: number
  scope: BandScope
  selected: boolean
  onSelect?: (muscle: MapMuscle) => void
}

function MuscleShape({ muscle, paths, sets, scope, selected, onSelect }: MuscleShapeProps) {
  const label = t.muscleAria(muscleName(muscle), sets > 0 ? t.muscleSets(formatSets(sets)) : t.muscleNotTrained)
  const common = { 'data-muscle': muscle, 'data-band': bandFor(sets, scope), 'aria-label': label }
  if (!onSelect) {
    return (
      <g {...common} role="img">
        <Halves paths={paths} />
      </g>
    )
  }
  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key !== 'Enter' && event.key !== ' ') return
    event.preventDefault()
    onSelect(muscle)
  }
  return (
    <g
      {...common}
      role="button"
      tabIndex={0}
      aria-pressed={selected}
      data-selected={selected || undefined}
      onClick={() => onSelect(muscle)}
      onKeyDown={onKeyDown}
    >
      <Halves paths={paths} />
    </g>
  )
}

function Halves({ paths }: { paths: string[] }) {
  const drawn = paths.map((d) => <path key={d} d={d} />)
  return (
    <>
      {drawn}
      <g transform={MIRROR}>{drawn}</g>
    </>
  )
}
