import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type CSSProperties, type ReactNode } from 'react'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { FoodEntry, FoodInput, FrequentFood, NutritionGoals } from '../api/types'
import { useToast } from '../components/contexts'
import { CountUp } from '../components/CountUp'
import { Sheet } from '../components/Sheet'
import { NutritionSkeleton } from '../components/Skeletons'
import { ErrorState, MessageState } from '../components/StateViews'
import { SwipeToDelete } from '../components/SwipeToDelete'
import { AddFoodSheet } from './AddFoodSheet'
import { vibrate } from '../lib/feedback'
import { errorMessage, formatKg, formatLongDate, formatShortDate } from '../lib/format'
import { t } from '../lib/i18n'
import { goalProgress, shiftDay, suggestedProteinGoal, todayIso, validateGoals, type GoalField } from '../lib/nutrition'

export function NutritionPage() {
  const queryClient = useQueryClient()
  const toast = useToast()
  const today = todayIso()
  const [date, setDate] = useState(today)
  const [adding, setAdding] = useState(false)
  const [editingGoals, setEditingGoals] = useState(false)
  const [pendingDelete, setPendingDelete] = useState<Set<number>>(new Set())

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
  }

  return (
    <div className="page">
      <header className="page-header">
        <h1 className="page-title">{t.nutritionTitle}</h1>
        <p className="page-subtitle">{t.nutritionSubtitle}</p>
      </header>

      <DaySwitcher date={date} today={today} onChange={setDate} />

      {day.isPending ? (
        <NutritionSkeleton />
      ) : day.isError ? (
        <ErrorState error={day.error} onRetry={() => day.refetch()} />
      ) : (
        <>
          <GoalRings calories={totals.calories} protein={totals.protein} goals={day.data.goals} onEdit={() => setEditingGoals(true)} />

          {frequent.data && frequent.data.length > 0 && (
            <section className="quick-add">
              <h2 className="section-title">{t.quickAdd}</h2>
              <div className="chip-row">
                {frequent.data.map((food) => (
                  <QuickAddChip key={`${food.name}|${food.caloriesKcal}|${food.proteinG}`} food={food} onAdd={() => add(food, true)} />
                ))}
              </div>
            </section>
          )}

          {visibleEntries.length === 0 ? (
            <MessageState icon="🍽" title={t.noFoodTitle} body={t.noFoodBody} />
          ) : (
            <div className="food-list">
              {visibleEntries.map((entry, index) => (
                <SwipeToDelete key={entry.id} label={entry.name} onDelete={() => remove(entry)}>
                  <div className="food-row" style={{ animationDelay: `${Math.min(index, 8) * 30}ms` }}>
                    <span className="food-name">{entry.name}</span>
                    <span className="food-protein num">{t.grams(formatKg(entry.proteinG))}</span>
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

