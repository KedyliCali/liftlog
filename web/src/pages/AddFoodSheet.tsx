import { useQuery } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { CatalogFood, FoodInput } from '../api/types'
import { BarcodeScanner } from '../components/BarcodeScanner'
import { Sheet } from '../components/Sheet'
import { ErrorState } from '../components/StateViews'
import { amountInGrams, entryName, nutritionFor, searchFoods, sortByCategory, stepServings, type Amount } from '../lib/foodCatalog'
import { formatKg } from '../lib/format'
import { locale, t } from '../lib/i18n'
import { validateFood, type FoodField, type FoodForm } from '../lib/nutrition'
import { sanitizeDecimal } from '../lib/validation'
import { lookupBarcode, productAsCatalogFood, type ScannedProduct } from '../lib/openFoodFacts'
import { BarcodeIcon, PencilIcon } from '../components/Icons'

type FoodDraft = Omit<FoodInput, 'entryDate'>

const isTurkish = locale.toLowerCase().startsWith('tr')
const nameOf = (food: CatalogFood) => (isTurkish ? food.nameTr : food.nameEn)
const servingOf = (food: CatalogFood) => (isTurkish ? food.servingTr : food.servingEn)

type Step =
  | { kind: 'search' }
  | { kind: 'scan' }
  | { kind: 'lookup'; code: string }
  | { kind: 'lookupFailed'; code: string }
  | { kind: 'amount'; food: CatalogFood; product?: ScannedProduct }
  | { kind: 'manual'; name: string; notice?: string }

/**
 * Search the built-in list first; a packaged food can be scanned instead, and typing values by hand is
 * the fallback for anything else. A scanned product goes through the same amount step as a catalog food.
 */
export function AddFoodSheet({ onSave, onClose }: { onSave: (input: FoodDraft) => Promise<void>; onClose: () => void }) {
  const [step, setStep] = useState<Step>({ kind: 'search' })
  const [query, setQuery] = useState('')
  // Bumped on every lookup and on leaving it, so a slow answer cannot replace a step the user moved on from
  const lookupRun = useRef(0)

  const lookUp = async (code: string) => {
    const run = ++lookupRun.current
    setStep({ kind: 'lookup', code })
    try {
      const result = await lookupBarcode(code, { preferTurkish: isTurkish })
      if (run !== lookupRun.current) return
      if (result.status === 'found') setStep({ kind: 'amount', food: productAsCatalogFood(result.product), product: result.product })
      else setStep({ kind: 'manual', name: result.status === 'noNutrition' ? (result.name ?? '') : '', notice: t.productNotFound })
    } catch {
      if (run === lookupRun.current) setStep({ kind: 'lookupFailed', code })
    }
  }

  const backToSearch = () => {
    lookupRun.current++
    setStep({ kind: 'search' })
  }

  return (
    <Sheet title={step.kind === 'scan' ? t.scanBarcode : t.addFood} onClose={onClose}>
      {step.kind === 'search' && (
        <FoodSearch
          query={query}
          onQuery={setQuery}
          onPick={(food) => setStep({ kind: 'amount', food })}
          onManual={() => setStep({ kind: 'manual', name: query.trim() })}
          onScan={() => setStep({ kind: 'scan' })}
        />
      )}
      {step.kind === 'scan' && <BarcodeScanner onDetected={(code) => void lookUp(code)} onBack={backToSearch} />}
      {step.kind === 'lookup' && (
        <div className="lookup-state" role="status">
          <div className="spinner" />
          <span className="muted">{t.lookingUpProduct}</span>
          <span className="scanned-product-code num">{step.code}</span>
        </div>
      )}
      {step.kind === 'lookupFailed' && (
        <div className="lookup-state" role="alert">
          <strong>{t.lookupFailed}</strong>
          <button className="btn btn-tonal" onClick={() => void lookUp(step.code)}>
            {t.retry}
          </button>
          <button className="btn btn-text" onClick={() => setStep({ kind: 'manual', name: '' })}>
            <PencilIcon /> {t.enterManually}
          </button>
        </div>
      )}
      {step.kind === 'amount' && (
        <AmountStep key={step.food.id} food={step.food} product={step.product} onBack={step.product ? () => setStep({ kind: 'scan' }) : backToSearch} onSave={onSave} />
      )}
      {step.kind === 'manual' && <ManualFoodForm initialName={step.name} notice={step.notice} onBack={backToSearch} onSave={onSave} />}
    </Sheet>
  )
}

function FoodSearch({
  query,
  onQuery,
  onPick,
  onManual,
  onScan,
}: {
  query: string
  onQuery: (q: string) => void
  onPick: (food: CatalogFood) => void
  onManual: () => void
  onScan: () => void
}) {
  // The list only changes with a backend release
  const catalog = useQuery({ queryKey: queryKeys.foods, queryFn: api.foods, staleTime: 24 * 60 * 60_000 })
  const results = catalog.data ? (query.trim() ? searchFoods(catalog.data, query) : sortByCategory(catalog.data)) : []

  return (
    <div className="stack">
      <div className="field">
        <input autoFocus placeholder={t.searchFoods} value={query} onChange={(e) => onQuery(e.target.value)} aria-label={t.searchFoods} />
      </div>
      <button className="scan-entry" onClick={onScan} data-testid="scan-barcode">
        <span className="scan-entry-icon">
          <BarcodeIcon />
        </span>
        <span>
          <strong>{t.scanBarcode}</strong>
          <small>{t.packagedFood} · Open Food Facts</small>
        </span>
      </button>
      <button className="btn btn-text" style={{ alignSelf: 'flex-start', padding: 0 }} onClick={onManual}>
        <PencilIcon /> {t.enterManually}
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

function AmountStep({ food, product, onBack, onSave }: { food: CatalogFood; product?: ScannedProduct; onBack: () => void; onSave: (input: FoodDraft) => Promise<void> }) {
  // A pack without a printed serving is easier to weigh than to count in made-up 100 g "servings"
  const [mode, setMode] = useState<'servings' | 'grams'>(product && product.servingG == null ? 'grams' : 'servings')
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
      <div className={product ? 'amount-food scanned-product' : 'amount-food'}>
        {product && <span className="scanned-product-code num">{product.code}</span>}
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
        <div className="amount-preview-macros num">
          <span>{t.carbsShort(formatKg(values.carbsG))}</span>
          <span>{t.fatShort(formatKg(values.fatG))}</span>
        </div>
        <span className="amount-preview-grams num">{formatKg(Math.round(amountInGrams(food, amount)))} g</span>
      </div>

      <button className="btn btn-primary btn-block" onClick={save} disabled={saving || !canSave} data-testid="save-food">
        {saving ? <span className="spinner small" /> : t.save}
      </button>
    </div>
  )
}

function ManualFoodForm({ initialName, notice, onBack, onSave }: { initialName: string; notice?: string; onBack: () => void; onSave: (input: FoodDraft) => Promise<void> }) {
  const [form, setForm] = useState<FoodForm>({ name: initialName, calories: '', protein: '', carbs: '', fat: '' })
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
      {notice && (
        <p className="manual-notice" role="status">
          {notice}
        </p>
      )}
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
      <div className="row">
        <div className={errors.has('carbs') ? 'field invalid' : 'field'}>
          <label htmlFor="food-carbs">{t.carbsOptional}</label>
          <input id="food-carbs" inputMode="decimal" autoComplete="off" value={form.carbs} onChange={(e) => update({ carbs: sanitizeDecimal(e.target.value, 6) }, 'carbs')} />
          {errors.has('carbs') && <span className="field-error">{t.errorProtein}</span>}
        </div>
        <div className={errors.has('fat') ? 'field invalid' : 'field'}>
          <label htmlFor="food-fat">{t.fatOptional}</label>
          <input id="food-fat" inputMode="decimal" autoComplete="off" value={form.fat} onChange={(e) => update({ fat: sanitizeDecimal(e.target.value, 6) }, 'fat')} />
          {errors.has('fat') && <span className="field-error">{t.errorProtein}</span>}
        </div>
      </div>
      <button type="submit" className="btn btn-primary btn-block" disabled={saving} data-testid="save-food">
        {saving ? <span className="spinner small" /> : t.save}
      </button>
    </form>
  )
}
