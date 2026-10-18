import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { PersonalRecord, ProgressPoint, WeeklyVolume } from '../api/types'
import { ErrorState, Loading, MessageState } from '../components/StateViews'
import { formatKg, formatShortDate } from '../lib/format'
import { t } from '../lib/i18n'
import { chooseExercise, yDomain } from '../lib/workoutLogic'

const WEEKS = 8

export function ProgressPage() {
  const records = useQuery({ queryKey: [...queryKeys.stats, 'prs'], queryFn: api.personalRecords })
  const volume = useQuery({ queryKey: [...queryKeys.stats, 'volume', WEEKS], queryFn: () => api.weeklyVolume(WEEKS) })
  const [picked, setPicked] = useState<number | null>(null)

  const selectedId = records.data ? chooseExercise(records.data, picked) : null
  const progress = useQuery({
    queryKey: [...queryKeys.stats, 'progress', selectedId],
    queryFn: () => api.progress(selectedId!),
    enabled: selectedId !== null,
  })

  const header = (
    <header className="page-header">
      <h1 className="page-title">{t.progressTitle}</h1>
      <p className="page-subtitle">{t.progressSubtitle}</p>
    </header>
  )

  if (records.isPending || volume.isPending) return <div className="page">{header}<Loading /></div>
  if (records.isError || volume.isError) {
    return (
      <div className="page">
        {header}
        <ErrorState error={records.error ?? volume.error} onRetry={() => (records.refetch(), volume.refetch())} />
      </div>
    )
  }
  if (records.data.length === 0) {
    return (
      <div className="page">
        {header}
        <MessageState icon="📈" title={t.progressEmptyTitle} body={t.progressEmptyBody} />
      </div>
    )
  }

  const selected = records.data.find((r) => r.exerciseId === selectedId)
  return (
    <div className="page">
      {header}
      <div className="chip-row" role="tablist">
        {records.data.map((record) => (
          <button
            key={record.exerciseId}
            role="tab"
            aria-selected={record.exerciseId === selectedId}
            className={record.exerciseId === selectedId ? 'filter-chip selected' : 'filter-chip'}
            onClick={() => setPicked(record.exerciseId)}
          >
            {record.exerciseName}
          </button>
        ))}
      </div>

      {selected && <RecordHighlight record={selected} />}

      <section className="chart-card">
        <h2>{t.e1rmTitle}</h2>
        <p className="muted">{t.e1rmCaption}</p>
        {progress.isPending ? (
          <div className="spinner" style={{ margin: '40px auto' }} />
        ) : progress.isError ? (
          <ErrorState error={progress.error} onRetry={() => progress.refetch()} />
        ) : progress.data.length < 2 ? (
          <p className="muted">{t.needMoreData}</p>
        ) : (
          <OneRepMaxChart points={progress.data} />
        )}
      </section>

      <section className="chart-card">
        <h2>{t.volumeTitle}</h2>
        <p className="muted">{t.volumeCaption}</p>
        <VolumeChart weeks={volume.data} />
      </section>

      <h2 className="section-title">{t.allRecords}</h2>
      {records.data.map((record) => (
        <div key={record.exerciseId} className="record-row">
          <div className="record-row-main">
            <strong>{record.exerciseName}</strong>
            <div className="muted" style={{ fontSize: 13 }}>
              {t.weightTimesReps(formatKg(record.heaviestWeightKg), record.heaviestReps)}
            </div>
          </div>
          <div className="record-row-side">
            <div className="accent num">{t.kg(formatKg(record.bestEstimated1RmKg))}</div>
            <small className="muted">e1RM</small>
          </div>
        </div>
      ))}
    </div>
  )
}

/** Two stat tiles; the trophy badge overlaps the corner of the first one. */
function RecordHighlight({ record }: { record: PersonalRecord }) {
  return (
    <div className="record-highlight">
      <div className="stat-tile">
        <small>{t.heaviestSet}</small>
        <strong>{t.weightTimesReps(formatKg(record.heaviestWeightKg), record.heaviestReps)}</strong>
        <span>{formatShortDate(record.heaviestDate)}</span>
      </div>
      <div className="stat-tile emphasized">
        <small>{t.bestE1rm}</small>
        <strong>{t.kg(formatKg(record.bestEstimated1RmKg))}</strong>
        <span>{formatShortDate(record.bestEstimated1RmDate)}</span>
      </div>
      <span className="trophy" aria-hidden>
        🏆
      </span>
    </div>
  )
}

const axisStyle = { fill: 'var(--text-muted)', fontSize: 12 }

function OneRepMaxChart({ points }: { points: ProgressPoint[] }) {
  const data = points.map((p) => ({ label: formatShortDate(p.date), value: p.estimated1RmKg }))
  return (
    <div style={{ height: 220 }} data-testid="e1rm-chart">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
          <CartesianGrid stroke="var(--outline-variant)" strokeDasharray="4 4" vertical={false} />
          <XAxis dataKey="label" tick={axisStyle} tickLine={false} axisLine={false} minTickGap={24} />
          <YAxis domain={yDomain(data.map((d) => d.value))} tick={axisStyle} tickLine={false} axisLine={false} />
          <Tooltip
            formatter={(value) => [t.kg(formatKg(Number(value))), 'e1RM']}
            contentStyle={{ background: 'var(--surface-highest)', border: 'none', borderRadius: 12 }}
            labelStyle={{ color: 'var(--text-muted)' }}
          />
          <Line type="monotone" dataKey="value" stroke="var(--primary)" strokeWidth={3} dot={{ r: 3, fill: 'var(--primary)' }} activeDot={{ r: 6 }} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}

function VolumeChart({ weeks }: { weeks: WeeklyVolume[] }) {
  const data = weeks.map((w) => ({ label: formatShortDate(w.weekStart), value: w.volumeKg }))
  return (
    <div style={{ height: 200 }} data-testid="volume-chart">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: -8 }}>
          <CartesianGrid stroke="var(--outline-variant)" strokeDasharray="4 4" vertical={false} />
          <XAxis dataKey="label" tick={axisStyle} tickLine={false} axisLine={false} minTickGap={12} />
          <YAxis tick={axisStyle} tickLine={false} axisLine={false} tickFormatter={(v: number) => (v >= 1000 ? `${Math.round(v / 1000)}k` : String(v))} />
          <Tooltip
            formatter={(value) => [t.kg(formatKg(Number(value))), t.volumeTitle]}
            contentStyle={{ background: 'var(--surface-highest)', border: 'none', borderRadius: 12 }}
            cursor={{ fill: 'var(--surface-high)' }}
          />
          <Bar dataKey="value" fill="var(--primary)" radius={[6, 6, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
