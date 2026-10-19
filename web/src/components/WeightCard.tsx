import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { WeightEntry } from '../api/types'
import { useConfirm, useToast } from './contexts'
import { ErrorState } from './StateViews'
import { vibrate } from '../lib/feedback'
import { errorMessage, formatKg, formatShortDate } from '../lib/format'
import { t } from '../lib/i18n'
import { todayIso } from '../lib/nutrition'
import {
  WEIGHT_RANGES,
  changeDirection,
  formatWeight,
  formatWeightChange,
  parseWeight,
  startingWeight,
  stepWeight,
  weightDomain,
  weightText,
  withMovingAverage,
  type WeightRange,
} from '../lib/weight'

/** The Nutrition page's weight chip links here with this anchor. */
export const WEIGHT_CARD_ID = 'weight'

const STEPS = [-0.5, -0.1, 0.1, 0.5] as const

/**
 * Profile's weight log: the latest weight with its change chips, a quick entry for today and the trend
 * chart. Saving refreshes the profile too, because the server keeps the profile weight equal to the
 * newest entry (BMI and bodyweight-exercise stats read it).
 */
export function WeightCard({ profileWeightKg }: { profileWeightKg: number | null }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const confirm = useConfirm()
  const today = todayIso()
  const [range, setRange] = useState<WeightRange>(30)
  // null until the user types or steps: until then the field follows the loaded data
  const [draft, setDraft] = useState<string | null>(null)
  const [invalid, setInvalid] = useState(false)
  const [busy, setBusy] = useState(false)
  const cardRef = useRef<HTMLElement>(null)

  const history = useQuery({ queryKey: queryKeys.weightHistory(range), queryFn: () => api.weightHistory(range, today) })

  // Arriving from the Nutrition chip (#weight): bring the card into view once it has its real height
  const loaded = history.isSuccess
  useEffect(() => {
    if (loaded && window.location.hash === `#${WEIGHT_CARD_ID}`) cardRef.current?.scrollIntoView?.({ block: 'start' })
  }, [loaded])

  const entries = history.data?.entries ?? []
  const latest = history.data?.latest ?? null
  const todaysEntry = latest?.date === today ? latest : entries.find((e) => e.date === today)
  const start = startingWeight(entries, latest, today, profileWeightKg)
  const text = draft ?? (start != null ? weightText(start) : '')

  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.weight }),
      queryClient.invalidateQueries({ queryKey: queryKeys.profile }),
    ])

  const step = (delta: number) => {
    // An empty or broken field steps from the last known weight, or a typical adult one
    const base = parseWeight(text) ?? start ?? 75
    setDraft(weightText(stepWeight(base, delta)))
    setInvalid(false)
  }

  const save = async () => {
    const weightKg = parseWeight(text)
    if (weightKg === null) {
      setInvalid(true)
      return
    }
    setBusy(true)
    try {
      await api.saveWeight(today, weightKg)
      vibrate(15)
      await refresh()
      setDraft(null)
      toast({ message: t.weightSaved })
    } catch (error) {
      toast({ message: errorMessage(error) })
    } finally {
      setBusy(false)
    }
  }

  const deleteToday = async () => {
    const item = todaysEntry ? `${t.weightToday} · ${t.kg(formatKg(todaysEntry.weightKg))}` : t.weightToday
    if (!(await confirm({ item }))) return
    setBusy(true)
    try {
      await api.deleteWeight(today)
      await refresh()
      setDraft(null)
      toast({ message: t.weightDeleted })
    } catch (error) {
      toast({ message: errorMessage(error) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="weight-card" id={WEIGHT_CARD_ID} ref={cardRef} data-testid="weight-card">
      <header className="weight-head">
        <div>
          <h2>{t.weightTitle}</h2>
          <p className="muted">{t.weightCaption}</p>
        </div>
        {latest && (
          <div className="weight-latest" data-testid="weight-latest">
            <span className="weight-latest-tag">{latest.date === today ? t.weightToday : t.weightLatest(formatShortDate(latest.date))}</span>
            <strong className="num">{formatKg(latest.weightKg)}</strong>
            <small>kg</small>
          </div>
        )}
      </header>

      {history.data && (history.data.change7d != null || history.data.change30d != null || history.data.average7d != null) && (
        <div className="weight-chips">
          {history.data.change7d != null && <ChangeChip days={7} kg={history.data.change7d} />}
          {history.data.change30d != null && <ChangeChip days={30} kg={history.data.change30d} />}
          {history.data.average7d != null && <span className="weight-chip average num">{t.weightAverage(formatWeight(history.data.average7d))}</span>}
        </div>
      )}

      <form className="weight-entry" onSubmit={(e) => (e.preventDefault(), void save())}>
        <div className={invalid ? 'weight-stepper invalid' : 'weight-stepper'} role="group" aria-label={t.weightInput}>
          {STEPS.slice(0, 2).map((delta) => (
            <StepButton key={delta} delta={delta} onStep={step} />
          ))}
          <input
            className="weight-input num"
            aria-label={t.weightInput}
            inputMode="decimal"
            autoComplete="off"
            value={text}
            placeholder="—"
            onChange={(e) => (setDraft(e.target.value), setInvalid(false))}
          />
          {STEPS.slice(2).map((delta) => (
            <StepButton key={delta} delta={delta} onStep={step} />
          ))}
        </div>
        {invalid && <span className="field-error">{t.weightInvalid}</span>}
        <div className="weight-actions">
          <button type="submit" className="btn btn-primary" disabled={busy} data-testid="save-weight">
            {busy ? <span className="spinner small" /> : todaysEntry ? t.weightUpdate : t.weightSave}
          </button>
          {todaysEntry && (
            <button type="button" className="btn btn-text" disabled={busy} onClick={() => void deleteToday()}>
              {t.weightDeleteToday}
            </button>
          )}
        </div>
      </form>

      <div className="segmented weight-ranges" role="tablist" aria-label={t.weightRangeLabel}>
        {WEIGHT_RANGES.map((days) => (
          <button key={days} type="button" role="tab" aria-selected={range === days} className={range === days ? 'selected' : ''} onClick={() => setRange(days)}>
            {t.weightRange(days)}
          </button>
        ))}
      </div>

      {history.isPending ? (
        <div className="spinner" style={{ margin: '40px auto' }} />
      ) : history.isError ? (
        <ErrorState error={history.error} onRetry={() => history.refetch()} />
      ) : entries.length === 0 ? (
        <p className="muted weight-note">{t.weightEmpty}</p>
      ) : entries.length === 1 ? (
        <p className="muted weight-note">{t.weightNeedMore}</p>
      ) : (
        <WeightChart entries={entries} />
      )}
    </section>
  )
}

function StepButton({ delta, onStep }: { delta: number; onStep: (delta: number) => void }) {
  const label = formatWeightChange(delta).replace(' kg', '')
  return (
    <button type="button" className={Math.abs(delta) >= 0.5 ? 'weight-step coarse' : 'weight-step'} onClick={() => onStep(delta)} aria-label={t.weightStep(label)}>
      {label}
    </button>
  )
}

function ChangeChip({ days, kg }: { days: number; kg: number }) {
  return (
    <span className={`weight-chip ${changeDirection(kg)} num`} data-testid={`weight-change-${days}`}>
      {t.weightChange(days, formatWeightChange(kg))}
    </span>
  )
}

const axisStyle = { fill: 'var(--text-muted)', fontSize: 12 }

function WeightChart({ entries }: { entries: WeightEntry[] }) {
  const data = withMovingAverage(entries).map((p) => ({ ...p, label: formatShortDate(p.date) }))
  return (
    <div className="weight-chart" data-testid="weight-chart">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
          <CartesianGrid stroke="var(--outline-variant)" strokeDasharray="4 4" vertical={false} />
          <XAxis dataKey="label" tick={axisStyle} tickLine={false} axisLine={false} minTickGap={24} />
          <YAxis domain={weightDomain(data.flatMap((d) => [d.weightKg, d.averageKg]))} tick={axisStyle} tickLine={false} axisLine={false} />
          <Tooltip
            formatter={(value, name) => [t.kg(formatWeight(Number(value))), name === 'averageKg' ? t.weightAverageSeries : t.weightSeries]}
            contentStyle={{ background: 'var(--surface-highest)', border: 'none', borderRadius: 12 }}
            labelStyle={{ color: 'var(--text-muted)' }}
          />
          {/* Daily weigh-ins stay quiet; the smoothed average is the line to read */}
          <Line type="monotone" dataKey="weightKg" stroke="var(--text-muted)" strokeWidth={1.5} dot={{ r: 2.5, fill: 'var(--text)', strokeWidth: 0 }} activeDot={{ r: 5 }} isAnimationActive={false} />
          <Line type="monotone" dataKey="averageKg" stroke="var(--primary)" strokeWidth={3} dot={false} activeDot={{ r: 6 }} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
