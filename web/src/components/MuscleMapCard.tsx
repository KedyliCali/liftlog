import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { AppError } from '../api/client'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { MuscleSets, MuscleStat } from '../api/types'
import { todayIso } from '../lib/nutrition'
import { t } from '../lib/i18n'
import { bandFor, bandThresholds, foldMuscleStats, formatSets, muscleName, type BandScope, type BodySide, type MapMuscle } from '../lib/muscles'
import { BodyMap } from './BodyMap'
import { CloseIcon } from './Icons'

type Days = 1 | 7 | 30

/** Progress page: which muscles the last 7 or 30 days trained, on a front or back figure. */
export function MuscleMapCard() {
  const [side, setSide] = useState<BodySide>('front')
  const [days, setDays] = useState<Days>(7)
  const [selected, setSelected] = useState<MapMuscle | null>(null)
  // The phone's date, so "last 7 days" ends on the user's today even when the server's clock is already tomorrow
  const [today] = useState(todayIso)
  const query = useQuery({
    queryKey: [...queryKeys.stats, 'muscles', days, today],
    queryFn: () => api.muscleStats(days, today),
  })

  // A server from before the muscle map has no such endpoint: leave the card out
  if (query.error instanceof AppError && query.error.kind === 'notFound') return null

  const muscles = foldMuscleStats(query.data?.muscles ?? [])
  const empty = query.isSuccess && muscles.size === 0
  const toggleMuscle = (muscle: MapMuscle) => setSelected((current) => (current === muscle ? null : muscle))

  return (
    <section className="muscle-card" aria-labelledby="muscle-card-title" data-testid="muscle-map-card">
      <div className="muscle-card-front">
        <small className="muscle-card-kicker">{t.muscleMapDays(days)}</small>
        <h2 id="muscle-card-title">{t.muscleMapTitle}</h2>
        <p className="muscle-card-caption">{t.muscleMapCaption}</p>

        <div className="muscle-toggles">
          <Toggle
            label={t.muscleMapSide}
            options={[
              ['front', t.muscleMapFront],
              ['back', t.muscleMapBack],
            ]}
            value={side}
            onChange={setSide}
          />
          <Toggle
            label={t.muscleMapPeriod}
            options={[
              [1, t.muscleMapDays(1)],
              [7, t.muscleMapDays(7)],
              [30, t.muscleMapDays(30)],
            ]}
            value={days}
            onChange={setDays}
          />
        </div>

        <div className={empty ? 'muscle-stage empty' : 'muscle-stage'} aria-busy={query.isPending}>
          <BodyMap side={side} muscles={muscles} scope={days} selected={selected} onSelect={empty ? undefined : toggleMuscle} />
        </div>

        {query.isError ? (
          <div className="muscle-empty" role="alert">
            <strong>{t.errorUnknown}</strong>
            <button className="btn btn-tonal" style={{ marginTop: 8 }} onClick={() => query.refetch()}>
              {t.retry}
            </button>
          </div>
        ) : empty ? (
          <div className="muscle-empty">
            <strong>{t.muscleMapEmptyTitle(days)}</strong>
            <p>{t.muscleMapEmptyBody}</p>
          </div>
        ) : (
          <MuscleLegend scope={days} />
        )}

        {selected && !empty && (
          <MuscleDetail
            muscle={selected}
            stat={muscles.get(selected) ?? { muscle: selected, sets: 0, exercises: [] }}
            scope={days}
            onClose={() => setSelected(null)}
          />
        )}
      </div>
    </section>
  )
}

function Toggle<T extends string | number>({ label, options, value, onChange }: {
  label: string
  options: [T, string][]
  value: T
  onChange: (value: T) => void
}) {
  return (
    <div className="muscle-seg" role="group" aria-label={label}>
      {options.map(([option, text]) => (
        <button key={option} type="button" aria-pressed={option === value} onClick={() => onChange(option)}>
          {text}
        </button>
      ))}
    </div>
  )
}

/** Az / Orta / Çok with the set counts where each band starts. */
export function MuscleLegend({ scope }: { scope: BandScope }) {
  const [mid, high] = bandThresholds(scope)
  return (
    <ul className="muscle-legend">
      <li>
        <span className="muscle-swatch low" />
        {t.muscleBandLow} <small>&lt;{mid}</small>
      </li>
      <li>
        <span className="muscle-swatch mid" />
        {t.muscleBandMid} <small>{mid}+</small>
      </li>
      <li>
        <span className="muscle-swatch high" />
        {t.muscleBandHigh} <small>{high}+</small>
      </li>
    </ul>
  )
}

function MuscleDetail({ muscle, stat, scope, onClose }: { muscle: MapMuscle; stat: MuscleStat; scope: BandScope; onClose: () => void }) {
  const name = muscleName(muscle)
  return (
    <div className="muscle-detail" data-band={bandFor(stat.sets, scope)} role="region" aria-label={name} data-testid="muscle-detail">
      <div className="muscle-detail-head">
        <strong>{name}</strong>
        <span className="num">{stat.sets > 0 ? t.muscleSets(formatSets(stat.sets)) : t.muscleNotTrained}</span>
      </div>
      {stat.exercises.length > 0 && (
        <ul>
          {stat.exercises.map((exercise) => (
            <li key={exercise.name}>
              <span>{exercise.name}</span>
              <span className="num">{t.muscleSets(formatSets(exercise.sets))}</span>
            </li>
          ))}
        </ul>
      )}
      <button className="muscle-detail-close" onClick={onClose} aria-label={t.close}>
        <CloseIcon size={18} />
      </button>
    </div>
  )
}

/** Workout summary: front and back side by side for one workout's muscles; nothing when there are none. */
export function RecapMuscles({ muscles }: { muscles: readonly MuscleSets[] | undefined }) {
  const folded = foldMuscleStats(muscles ?? [])
  if (folded.size === 0) return null
  return (
    <section className="recap-muscles" aria-label={t.muscleRecapTitle} data-testid="recap-muscles">
      <h3 className="recap-muscles-title">{t.muscleRecapTitle}</h3>
      <div className="recap-muscles-figures">
        <BodyMap side="front" muscles={folded} scope="workout" />
        <BodyMap side="back" muscles={folded} scope="workout" />
      </div>
      <MuscleLegend scope="workout" />
    </section>
  )
}
