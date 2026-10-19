import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type CSSProperties, type ReactNode } from 'react'
import { Link } from 'react-router'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { FoodEntry, FoodInput, FrequentFood, NutritionGoals } from '../api/types'
import { useConfirm, useToast } from '../components/contexts'
import { CountUp } from '../components/CountUp'
import { Sheet } from '../components/Sheet'
import { NutritionSkeleton } from '../components/Skeletons'
import { ErrorState, MessageState } from '../components/StateViews'
import { SwipeToDelete } from '../components/SwipeToDelete'
import { AddFoodSheet } from './AddFoodSheet'
import { vibrate } from '../lib/feedback'
import { errorMessage, formatKg, formatLongDate, formatShortDate } from '../lib/format'
import { t } from '../lib/i18n'
import { goalProgress, macroEnergySplit, shiftDay, suggestedProteinGoal, todayIso, validateGoals, type GoalField } from '../lib/nutrition'
import { PlateIcon } from '../components/Icons'
import { WEIGHT_CARD_ID } from '../lib/weight'

export function NutritionPage() {
  const queryClient = useQueryClient()
  const toast = useToast()
  const confirm = useConfirm()
  const today = todayIso()
  const [date, setDate] = useState(today)
  const [adding, setAdding] = useState(false)
  const [editingGoals, setEditingGoals] = useState(false)
  const [pendingDelete, setPendingDelete] = useState<Set<number>>(new Set())
  const [menuOpen, setMenuOpen] = useState(false)
  const [copying, setCopying] = useState(false)

  const day = useQuery({ queryKey: queryKeys.nutritionDay(date), queryFn: () => api.nutritionDay(date) })
  const frequent = useQuery({ queryKey: queryKeys.frequentFoods, queryFn: api.frequentFoods, staleTime: 5 * 60_000 })
  const profile = useQuery({ queryKey: queryKeys.profile, queryFn: api.profile, staleTime: 5 * 60_000 })

  const refresh = () => queryClient.invalidateQueries({ queryKey: queryKeys.nutrition })

  const add = async (input: Omit<FoodInput, 'entryDate'>, viaQuickAdd: boolean) => {
    try {
      const saved = await api.addFood({ ...input, entryDate: date })
      vibrate(15)
      await refresh()
      if (viaQuickAdd) {
        // Quick-add has no confirmation step, so undo is the safety net for a mis-tap
        toast({
          message: t.quickAdded(saved.name),
          actionLabel: t.undo,
          durationMs: 4000,
          onAction: () => void api.deleteFood(saved.id).then(refresh),
        })
      }
      return true
    } catch (error) {
      toast({ message: errorMessage(error) })
      return false
    }
  }

  /**
   * One request copies the whole previous day on the server (all or nothing). Undo deletes exactly the
   * copies it returned, so entries that were already on this day are never touched.
   */
  const copyPreviousDay = async () => {
    setMenuOpen(false)
    setCopying(true)
    try {
      const copied = await api.copyDay(date, shiftDay(date, -1))
      await refresh()
      if (copied.length === 0) {
        toast({ message: t.nothingToCopy })
        return
      }
      vibrate(15)
      toast({
        message: t.copiedEntries(copied.length),
        actionLabel: t.undo,
        durationMs: 6000,
        onAction: () =>
          void Promise.allSettled(copied.map((entry) => api.deleteFood(entry.id))).then(async (results) => {
            await refresh()
            const failed = results.find((r) => r.status === 'rejected')
            if (failed) toast({ message: errorMessage(failed.reason) })
          }),
      })
    } catch (error) {
      toast({ message: errorMessage(error) })
    } finally {
      setCopying(false)
    }
  }
  const copyLabel = date === today ? t.copyYesterday : t.copyPreviousDay

  /** Same pattern as sets: hide at once, delete on the server when the undo window closes. */
  const remove = (entry: FoodEntry) => {
    setPendingDelete((s) => new Set(s).add(entry.id))
    const restore = () =>
      setPendingDelete((s) => {
        const next = new Set(s)
        next.delete(entry.id)
        return next
      })
    toast({
      message: t.foodDeleted,
      actionLabel: t.undo,
      durationMs: 5000,
      onAction: restore,
      onTimeout: () =>
        api
          .deleteFood(entry.id)
          .then(refresh)
          .catch((error) => toast({ message: errorMessage(error) }))
          .finally(restore),
    })
  }

  const visibleEntries = day.data?.entries.filter((e) => !pendingDelete.has(e.id)) ?? []
  const totals = {
    calories: visibleEntries.reduce((sum, e) => sum + e.caloriesKcal, 0),
    protein: visibleEntries.reduce((sum, e) => sum + e.proteinG, 0),
    carbs: visibleEntries.reduce((sum, e) => sum + (e.carbsG ?? 0), 0),
    fat: visibleEntries.reduce((sum, e) => sum + (e.fatG ?? 0), 0),
  }

  return (
    <div className="page">
      <header className="page-header">
        <h1 className="page-title">{t.nutritionTitle}</h1>
        <p className="page-subtitle">{t.nutritionSubtitle}</p>
        {profile.data?.bodyWeightKg != null && <WeightChip kg={profile.data.bodyWeightKg} />}
        {day.isSuccess && (
          <div className="day-actions">
            <button className="icon-btn" onClick={() => setMenuOpen((open) => !open)} aria-label={t.dayMenu} aria-expanded={menuOpen}>
              ⋮
            </button>
            {menuOpen && (
              <div className="menu">
                <button onClick={() => void copyPreviousDay()} disabled={copying}>
                  {copyLabel}
                </button>
              </div>
            )}
          </div>
        )}
      </header>

      <DaySwitcher date={date} today={today} onChange={setDate} />

      {day.isPending ? (
        <NutritionSkeleton />
      ) : day.isError ? (
        <ErrorState error={day.error} onRetry={() => day.refetch()} />
      ) : (
        <>
          <GoalRings calories={totals.calories} protein={totals.protein} goals={day.data.goals} onEdit={() => setEditingGoals(true)} />
          <MacroSplit protein={totals.protein} carbs={totals.carbs} fat={totals.fat} />

          {frequent.data && frequent.data.length > 0 && (
            <section className="quick-add">
              <h2 className="section-title">{t.quickAdd}</h2>
              <div className="chip-row">
                {frequent.data.map((food) => (
                  <QuickAddChip key={`${food.name}|${food.caloriesKcal}|${food.proteinG}`} food={food} onAdd={() => add({ name: food.name, caloriesKcal: food.caloriesKcal, proteinG: food.proteinG, carbsG: food.carbsG, fatG: food.fatG }, true)} />
                ))}
              </div>
            </section>
          )}

          {visibleEntries.length === 0 ? (
            <MessageState icon={<PlateIcon size={32} />} title={t.noFoodTitle} body={t.noFoodBody}>
              <button className="copy-day-cta" onClick={() => void copyPreviousDay()} disabled={copying} data-testid="copy-previous-day">
                <span className="copy-day-cta-tag num">{formatShortDate(shiftDay(date, -1))}</span>
                <strong>{copying ? <span className="spinner small" /> : copyLabel}</strong>
                <small>{t.copyDayHint}</small>
              </button>
            </MessageState>
          ) : (
            <div className="food-list">
              {visibleEntries.map((entry, index) => (
                <SwipeToDelete
                  key={entry.id}
                  label={entry.name}
                  confirm={() => confirm({ item: entry.name, message: t.kcal(formatKg(entry.caloriesKcal)) })}
                  onDelete={() => remove(entry)}
                >
                  <div className="food-row" style={{ animationDelay: `${Math.min(index, 8) * 30}ms` }}>
                    <span className="food-text">
                      <span className="food-name">{entry.name}</span>
                      <span className="food-macros num">
                        {t.proteinShort(formatKg(entry.proteinG))}
                        {entry.carbsG != null && ` · ${t.carbsShort(formatKg(entry.carbsG))}`}
                        {entry.fatG != null && ` · ${t.fatShort(formatKg(entry.fatG))}`}
                      </span>
                    </span>
                    <span className="food-kcal num">{t.kcal(formatKg(entry.caloriesKcal))}</span>
                  </div>
                </SwipeToDelete>
              ))}
            </div>
          )}

          <button className="fab" onClick={() => setAdding(true)} data-testid="fab-add-food">
            <span className="fab-plus">+</span> {t.addFood}
          </button>
        </>
      )}

      {adding && (
        <AddFoodSheet
          onClose={() => setAdding(false)}
          onSave={async (input) => {
            if (await add(input, false)) setAdding(false)
          }}
        />
      )}
      {editingGoals && day.data && (
        <GoalsSheet
          goals={day.data.goals}
          bodyWeightKg={profile.data?.bodyWeightKg ?? null}
          onClose={() => setEditingGoals(false)}
          onSaved={async () => {
            await refresh()
            setEditingGoals(false)
          }}
        />
      )}
    </div>
  )
}

/** Current weight as a small tag under the title; it opens the weight log on the Profile page. */
function WeightChip({ kg }: { kg: number }) {
  return (
    <Link to={`/profile#${WEIGHT_CARD_ID}`} className="weight-link-chip" aria-label={t.weightChipLabel(formatKg(kg))} data-testid="weight-link-chip">
      <span className="weight-link-chip-tag" aria-hidden>
        {t.weightTitle}
      </span>
      <strong className="num">{formatKg(kg)}</strong>
      <small aria-hidden>kg</small>
    </Link>
  )
}

function DaySwitcher({ date, today, onChange }: { date: string; today: string; onChange: (date: string) => void }) {
  const label = date === today ? t.today : date === shiftDay(today, -1) ? t.yesterday : formatShortDate(date)
  return (
    <div className="day-switcher">
      <button className="icon-btn" onClick={() => onChange(shiftDay(date, -1))} aria-label={t.previousDay}>
        ‹
      </button>
      <div className="day-switcher-label">
        <strong key={date}>{label}</strong>
        <small>{formatLongDate(date)}</small>
      </div>
      <button className="icon-btn" onClick={() => onChange(shiftDay(date, 1))} disabled={date >= today} aria-label={t.nextDay}>
        ›
      </button>
    </div>
  )
}

/**
 * Calories get the big ring; protein's smaller ring overlaps its lower-right edge, so the two read as
 * one badge with a clear main number instead of two equal circles side by side.
 */
function GoalRings({ calories, protein, goals, onEdit }: { calories: number; protein: number; goals: NutritionGoals; onEdit: () => void }) {
  const kcal = goalProgress(calories, goals.calorieGoalKcal)
  const prot = goalProgress(protein, goals.proteinGoalG)
  const hasGoals = goals.calorieGoalKcal != null || goals.proteinGoalG != null
  return (
    <button className="goal-rings" onClick={onEdit} aria-label={t.setGoals}>
      <Ring className="ring-calories" size={176} stroke={14} fraction={kcal.fraction} over={kcal.over}>
        <small>{t.caloriesLabel}</small>
        <strong>
          <CountUp value={calories} format={(n) => formatKg(n)} />
        </strong>
        <span>{goals.calorieGoalKcal != null ? t.ofGoal(t.kcal(formatKg(goals.calorieGoalKcal))) : t.noGoal}</span>
      </Ring>
      <Ring className="ring-protein" size={112} stroke={11} fraction={prot.fraction} over={prot.over}>
        <small>{t.proteinLabel}</small>
        <strong>
          <CountUp value={protein} format={(n) => t.grams(formatKg(Math.round(n)))} />
        </strong>
        {goals.proteinGoalG != null && <span>{t.ofGoal(t.grams(String(goals.proteinGoalG)))}</span>}
      </Ring>
      <span className="goal-rings-caption">
        {hasGoals ? <RemainingText calories={calories} protein={protein} goals={goals} /> : <span className="accent">{t.setGoals} ›</span>}
      </span>
    </button>
  )
}

/** One bar split by where the day's calories came from, with gram totals underneath. */
function MacroSplit({ protein, carbs, fat }: { protein: number; carbs: number; fat: number }) {
  const split = macroEnergySplit(protein, carbs, fat)
  const parts = [
    { key: 'protein', share: split.protein, label: t.proteinShort(formatKg(Math.round(protein))) },
    { key: 'carbs', share: split.carbs, label: t.carbsShort(formatKg(Math.round(carbs))) },
    { key: 'fat', share: split.fat, label: t.fatShort(formatKg(Math.round(fat))) },
  ]
  return (
    <div className="macro-split" aria-label={t.macroSplit}>
      <div className="macro-bar">
        {parts.map((p) => (
          <span key={p.key} className={`macro-${p.key}`} style={{ flexGrow: p.share }} />
        ))}
      </div>
      <div className="macro-legend num">
        {parts.map((p) => (
          <span key={p.key} className={`macro-${p.key}`}>
            {p.label}
            {p.share > 0 && <small> {Math.round(p.share * 100)}%</small>}
          </span>
        ))}
      </div>
    </div>
  )
}

function RemainingText({ calories, protein, goals }: { calories: number; protein: number; goals: NutritionGoals }) {
  const parts: string[] = []
  const describe = (left: number, unit: (n: string) => string) => (left >= 0 ? t.remaining(unit(formatKg(Math.round(left)))) : t.over(unit(formatKg(Math.round(-left)))))
  if (goals.calorieGoalKcal != null) parts.push(describe(goals.calorieGoalKcal - calories, t.kcal))
  if (goals.proteinGoalG != null) parts.push(describe(goals.proteinGoalG - protein, t.grams))
  return <>{parts.join(' · ')}</>
}

function Ring({ className, size, stroke, fraction, over, children }: { className: string; size: number; stroke: number; fraction: number; over: boolean; children: ReactNode }) {
  const radius = (size - stroke) / 2
  const circumference = 2 * Math.PI * radius
  return (
    <div className={over ? `ring ${className} over` : `ring ${className}`} style={{ width: size, height: size } as CSSProperties}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden>
        <circle className="ring-track" cx={size / 2} cy={size / 2} r={radius} strokeWidth={stroke} />
        <circle
          className="ring-progress"
          cx={size / 2}
          cy={size / 2}
          r={radius}
          strokeWidth={stroke}
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - fraction)}
        />
      </svg>
      <div className="ring-label">{children}</div>
    </div>
  )
}

function QuickAddChip({ food, onAdd }: { food: FrequentFood; onAdd: () => Promise<boolean> }) {
  const [busy, setBusy] = useState(false)
  return (
    <button
      className="quick-chip"
      disabled={busy}
      onClick={async () => {
        setBusy(true)
        await onAdd()
        setBusy(false)
      }}
    >
      <span className="quick-chip-name">{food.name}</span>
      <span className="quick-chip-meta num">
        {t.kcal(formatKg(food.caloriesKcal))}
        {food.proteinG > 0 && ` · ${t.grams(formatKg(food.proteinG))}`}
      </span>
    </button>
  )
}

function GoalsSheet({ goals, bodyWeightKg, onSaved, onClose }: { goals: NutritionGoals; bodyWeightKg: number | null; onSaved: () => Promise<void>; onClose: () => void }) {
  const toast = useToast()
  const [calories, setCalories] = useState(goals.calorieGoalKcal?.toString() ?? '')
  const [protein, setProtein] = useState(goals.proteinGoalG?.toString() ?? '')
  const [errors, setErrors] = useState<Set<GoalField>>(new Set())
  const [saving, setSaving] = useState(false)
  const suggestion = suggestedProteinGoal(bodyWeightKg)

  const save = async () => {
    const result = validateGoals(calories, protein)
    if (result.errors.size > 0) {
      setErrors(result.errors)
      return
    }
    setSaving(true)
    try {
      await api.updateNutritionGoals({ calorieGoalKcal: result.calorieGoalKcal, proteinGoalG: result.proteinGoalG })
      await onSaved()
    } catch (error) {
      toast({ message: errorMessage(error) })
      setSaving(false)
    }
  }

  return (
    <Sheet title={t.goalsTitle} onClose={onClose}>
      <div className="stack">
        <div className="row">
          <div className={errors.has('calories') ? 'field invalid' : 'field'}>
            <label htmlFor="goal-kcal">{t.calorieGoal}</label>
            <input id="goal-kcal" inputMode="numeric" value={calories} onChange={(e) => (setCalories(e.target.value.replace(/\D/g, '').slice(0, 5)), setErrors(new Set()))} />
            {errors.has('calories') && <span className="field-error">{t.errorCalorieGoal}</span>}
          </div>
          <div className={errors.has('protein') ? 'field invalid' : 'field'}>
            <label htmlFor="goal-protein">{t.proteinGoal}</label>
            <input id="goal-protein" inputMode="numeric" value={protein} onChange={(e) => (setProtein(e.target.value.replace(/\D/g, '').slice(0, 3)), setErrors(new Set()))} />
            {errors.has('protein') && <span className="field-error">{t.errorProteinGoal}</span>}
          </div>
        </div>
        {suggestion != null && String(suggestion) !== protein && (
          <button className="btn btn-text" style={{ alignSelf: 'flex-start', padding: 0 }} onClick={() => setProtein(String(suggestion))}>
            {t.suggestProtein(suggestion)}
          </button>
        )}
        <p className="muted" style={{ margin: 0, fontSize: 13 }}>
          {t.goalsHint}
        </p>
        <button className="btn btn-primary btn-block" onClick={save} disabled={saving}>
          {saving ? <span className="spinner small" /> : t.save}
        </button>
      </div>
    </Sheet>
  )
}

