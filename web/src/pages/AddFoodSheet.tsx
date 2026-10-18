import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { CatalogFood, FoodInput } from '../api/types'
import { Sheet } from '../components/Sheet'
import { ErrorState } from '../components/StateViews'
import { amountInGrams, entryName, nutritionFor, searchFoods, stepServings, type Amount } from '../lib/foodCatalog'
import { formatKg } from '../lib/format'
import { locale, t } from '../lib/i18n'
import { validateFood, type FoodField, type FoodForm } from '../lib/nutrition'
import { sanitizeDecimal } from '../lib/validation'

type FoodDraft = Omit<FoodInput, 'entryDate'>

const isTurkish = locale.toLowerCase().startsWith('tr')
const nameOf = (food: CatalogFood) => (isTurkish ? food.nameTr : food.nameEn)
const servingOf = (food: CatalogFood) => (isTurkish ? food.servingTr : food.servingEn)

/** Search the built-in list first; typing values by hand is the fallback for anything not in it. */
export function AddFoodSheet({ onSave, onClose }: { onSave: (input: FoodDraft) => Promise<void>; onClose: () => void }) {
  const [step, setStep] = useState<{ kind: 'search' } | { kind: 'amount'; food: CatalogFood } | { kind: 'manual'; name: string }>({ kind: 'search' })
  const [query, setQuery] = useState('')

  return (
    <Sheet title={t.addFood} onClose={onClose}>
      {step.kind === 'search' && (
        <FoodSearch query={query} onQuery={setQuery} onPick={(food) => setStep({ kind: 'amount', food })} onManual={() => setStep({ kind: 'manual', name: query.trim() })} />
      )}
      {step.kind === 'amount' && <AmountStep food={step.food} onBack={() => setStep({ kind: 'search' })} onSave={onSave} />}
      {step.kind === 'manual' && <ManualFoodForm initialName={step.name} onBack={() => setStep({ kind: 'search' })} onSave={onSave} />}
    </Sheet>
  )
}

function FoodSearch({ query, onQuery, onPick, onManual }: { query: string; onQuery: (q: string) => void; onPick: (food: CatalogFood) => void; onManual: () => void }) {
  // The list only changes with a backend release
  const catalog = useQuery({ queryKey: queryKeys.foods, queryFn: api.foods, staleTime: 24 * 60 * 60_000 })
  const results = catalog.data ? (query.trim() ? searchFoods(catalog.data, query) : catalog.data) : []

  return (
    <div className="stack">
      <div className="field">
        <input autoFocus placeholder={t.searchFoods} value={query} onChange={(e) => onQuery(e.target.value)} aria-label={t.searchFoods} />
      </div>
      <button className="btn btn-text" style={{ alignSelf: 'flex-start', padding: 0 }} onClick={onManual}>
        ✎ {t.enterManually}
      </button>
      {catalog.isPending ? (
        <div className="spinner" style={{ margin: '24px auto' }} />
      ) : catalog.isError ? (
        <ErrorState error={catalog.error} onRetry={() => catalog.refetch()} />
      ) : (
        <div className="food-results">
          {results.length === 0 && <p className="muted">{t.noFoodMatches(query.trim())}</p>}
          {results.map((food, index) => {
            const startsCategory = !query.trim() && food.category !== results[index - 1]?.category
            const perServing = nutritionFor(food, { kind: 'servings', count: 1 })
            return (
              <div key={food.id}>
                {startsCategory && (
                  <h4 className="exercise-section-title" data-category={food.category}>
                    {t.foodCategories[food.category] ?? food.category}
                  </h4>
                )}
                <button className="food-result" onClick={() => onPick(food)}>
                  <span className="food-result-name">{nameOf(food)}</span>
                  <span className="food-result-meta num">
                    {servingOf(food)} · {t.kcal(formatKg(perServing.caloriesKcal))} · {t.grams(formatKg(perServing.proteinG))}
                  </span>
                </button>
              </div>
            )
          })}
        </div>
      )}
      <p className="muted catalog-note">{t.catalogApproximate}</p>
    </div>
  )
}

function AmountStep({ food, onBack, onSave }: { food: CatalogFood; onBack: () => void; onSave: (input: FoodDraft) => Promise<void> }) {
  const [mode, setMode] = useState<'servings' | 'grams'>('servings')
  const [servings, setServings] = useState(1)
  const [gramsText, setGramsText] = useState(String(food.servingG))
  const [saving, setSaving] = useState(false)

  const grams = Number(gramsText)
  const gramsValid = gramsText !== '' && grams > 0 && grams <= 5000
  const amount: Amount = mode === 'servings' ? { kind: 'servings', count: servings } : { kind: 'grams', grams: gramsValid ? grams : 0 }
  const values = nutritionFor(food, amount)
  const canSave = mode === 'servings' || gramsValid

  const save = async () => {
    setSaving(true)
    await onSave({ name: entryName(nameOf(food), servingOf(food), amount, formatKg), ...values })
    setSaving(false)
  }

  return (
    <div className="stack">
      <button className="btn btn-text" style={{ alignSelf: 'flex-start', padding: 0 }} onClick={onBack}>
        ‹ {t.back}
      </button>
      <div className="amount-food">
        <strong>{nameOf(food)}</strong>
        <span className="muted num">
          {t.per100g(formatKg(food.kcalPer100g), formatKg(food.proteinPer100g))}
        </span>
      </div>

      <div className="segmented" role="tablist">
        <button role="tab" aria-selected={mode === 'servings'} className={mode === 'servings' ? 'selected' : ''} onClick={() => setMode('servings')}>
          {t.byServing}
        </button>
        <button role="tab" aria-selected={mode === 'grams'} className={mode === 'grams' ? 'selected' : ''} onClick={() => setMode('grams')}>
          {t.byGrams}
        </button>
      </div>

      {mode === 'servings' ? (
        <div className="field">
          <span className="field-label">
            {servingOf(food)} = {formatKg(food.servingG)} g
          </span>
          <div className="stepper">
            <button type="button" onClick={() => setServings((n) => stepServings(n, -1))} disabled={servings <= 0.5} aria-label={t.fewerServings}>
              −
            </button>
            <span className="stepper-value num" key={servings}>
              {formatKg(servings)}
            </span>
            <button type="button" onClick={() => setServings((n) => stepServings(n, 1))} disabled={servings >= 10} aria-label={t.moreServings}>
              +
            </button>
          </div>
        </div>
      ) : (
        <div className={gramsValid ? 'field' : 'field invalid'}>
          <label htmlFor="food-grams">{t.amountGrams}</label>
          <input id="food-grams" inputMode="decimal" autoComplete="off" value={gramsText} onChange={(e) => setGramsText(sanitizeDecimal(e.target.value, 6))} />
          {!gramsValid && <span className="field-error">{t.errorGrams}</span>}
        </div>
      )}

      {/* Result tile with the gram total tucked under its corner, recalculated as the amount changes */}
      <div className="amount-preview">
        <div>
          <strong className="num" key={values.caloriesKcal}>
            {t.kcal(formatKg(values.caloriesKcal))}
          </strong>
          <span className="num">{t.proteinAmount(formatKg(values.proteinG))}</span>
        </div>
        <span className="amount-preview-grams num">{formatKg(Math.round(amountInGrams(food, amount)))} g</span>
      </div>

      <button className="btn btn-primary btn-block" onClick={save} disabled={saving || !canSave} data-testid="save-food">
        {saving ? <span className="spinner small" /> : t.save}
      </button>
    </div>
  )
}

function ManualFoodForm({ initialName, onBack, onSave }: { initialName: string; onBack: () => void; onSave: (input: FoodDraft) => Promise<void> }) {
  const [form, setForm] = useState<FoodForm>({ name: initialName, calories: '', protein: '' })
  const [errors, setErrors] = useState<Set<FoodField>>(new Set())
  const [saving, setSaving] = useState(false)

  const update = (patch: Partial<FoodForm>, field: FoodField) => {
    setForm((f) => ({ ...f, ...patch }))
    setErrors((e) => {
      const next = new Set(e)
      next.delete(field)
      return next
    })
  }

  const submit = async () => {
    const result = validateFood(form)
    if (!result.input) {
      setErrors(result.errors)
      return
    }
    setSaving(true)
    await onSave(result.input)
    setSaving(false)
  }

  return (
    <form
      className="stack"
      onSubmit={(event) => {
        event.preventDefault()
        void submit()
      }}
    >
      <button type="button" className="btn btn-text" style={{ alignSelf: 'flex-start', padding: 0 }} onClick={onBack}>
        ‹ {t.back}
      </button>
      <div className={errors.has('name') ? 'field invalid' : 'field'}>
        <label htmlFor="food-name">{t.foodName}</label>
        <input id="food-name" value={form.name} maxLength={100} placeholder={t.foodNamePlaceholder} autoComplete="off" onChange={(e) => update({ name: e.target.value }, 'name')} />
        {errors.has('name') && <span className="field-error">{t.errorFoodName}</span>}
      </div>
      <div className="row">
        <div className={errors.has('calories') ? 'field invalid' : 'field'}>
          <label htmlFor="food-kcal">{t.caloriesKcal}</label>
          <input id="food-kcal" inputMode="numeric" autoComplete="off" value={form.calories} onChange={(e) => update({ calories: e.target.value.replace(/\D/g, '').slice(0, 5) }, 'calories')} />
          {errors.has('calories') && <span className="field-error">{t.errorCalories}</span>}
        </div>
        <div className={errors.has('protein') ? 'field invalid' : 'field'}>
          <label htmlFor="food-protein">{t.proteinOptional}</label>
          <input id="food-protein" inputMode="decimal" autoComplete="off" value={form.protein} onChange={(e) => update({ protein: sanitizeDecimal(e.target.value, 6) }, 'protein')} />
          {errors.has('protein') && <span className="field-error">{t.errorProtein}</span>}
        </div>
      </div>
      <button type="submit" className="btn btn-primary btn-block" disabled={saving} data-testid="save-food">
        {saving ? <span className="spinner small" /> : t.save}
      </button>
    </form>
  )
}
