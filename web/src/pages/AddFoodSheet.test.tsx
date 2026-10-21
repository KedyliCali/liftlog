import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { t } from '../lib/i18n'
import { fakeServer, jsonResponse } from '../test/fakeServer'
import { AddFoodSheet } from './AddFoodSheet'

type Callback = (result: { getText: () => string } | undefined, error: unknown, controls: unknown) => void
const camera = vi.hoisted(() => ({ deliver: null as null | Callback }))
vi.mock('@zxing/browser', () => ({
  BrowserMultiFormatOneDReader: class {
    async decodeFromConstraints(_constraints: unknown, _video: unknown, callback: Callback) {
      camera.deliver = callback
      return { stop: () => {} }
    }
  },
}))

const CODE = '8690504011234'
const OFF_PATH = `/api/v2/product/${CODE}.json`

function renderSheet() {
  const onSave = vi.fn<(input: object) => Promise<void>>(async () => {})
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <AddFoodSheet onSave={onSave} onClose={() => {}} />
    </QueryClientProvider>,
  )
  return { onSave, user: userEvent.setup() }
}

async function scan(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByTestId('scan-barcode'))
  await screen.findByText(t.scanHint)
  act(() => camera.deliver?.({ getText: () => CODE }, undefined, { stop: () => {} }))
}

beforeEach(() => {
  localStorage.clear()
  camera.deliver = null
  Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: vi.fn() } })
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('barcode in the add food sheet', () => {
  it('logs a scanned product through the amount step', async () => {
    fakeServer({
      'GET /api/v1/foods': () => jsonResponse(200, []),
      [`GET ${OFF_PATH}`]: () =>
        jsonResponse(200, {
          product: { product_name: 'Pötibör', brands: 'Ülker', serving_size: '30 g', serving_quantity: 30, nutriments: { 'energy-kcal_100g': 440, proteins_100g: 7.5, carbohydrates_100g: 72, fat_100g: 13 } },
        }),
    })
    const { onSave, user } = renderSheet()
    await scan(user)

    expect(await screen.findByText('Ülker Pötibör')).toBeInTheDocument()
    expect(screen.getByText(CODE)).toBeInTheDocument()
    await user.click(screen.getByTestId('save-food'))
    // One 30 g serving of a 440 kcal/100 g biscuit
    expect(onSave).toHaveBeenCalledWith({ name: 'Ülker Pötibör (30 g)', caloriesKcal: 132, proteinG: 2.3, carbsG: 21.6, fatG: 3.9, grams: 30 })
    // Cached for the next scan
    expect(localStorage.getItem('liftlog.barcodeProducts')).toContain(CODE)
  })

  it('switches to manual entry with the name when the product has no nutrition data', async () => {
    fakeServer({
      'GET /api/v1/foods': () => jsonResponse(200, []),
      [`GET ${OFF_PATH}`]: () => jsonResponse(200, { product: { product_name: 'Ayran', nutriments: {} } }),
    })
    const { user } = renderSheet()
    await scan(user)

    expect(await screen.findByText(t.productNotFound)).toBeInTheDocument()
    expect(screen.getByLabelText(t.foodName)).toHaveValue('Ayran')
  })

  it('switches to empty manual entry when the code is unknown', async () => {
    fakeServer({ 'GET /api/v1/foods': () => jsonResponse(200, []) })
    const { user } = renderSheet()
    await scan(user)

    expect(await screen.findByText(t.productNotFound)).toBeInTheDocument()
    expect(screen.getByLabelText(t.foodName)).toHaveValue('')
  })

  it('offers a retry when the database cannot be reached', async () => {
    let online = false
    fakeServer({
      'GET /api/v1/foods': () => jsonResponse(200, []),
      [`GET ${OFF_PATH}`]: () => (online ? jsonResponse(200, { product: { product_name: 'Su', nutriments: { 'energy-kcal_100g': 0 } } }) : jsonResponse(503, {})),
    })
    const { user } = renderSheet()
    await scan(user)

    expect(await screen.findByText(t.lookupFailed)).toBeInTheDocument()
    online = true
    await user.click(screen.getByRole('button', { name: t.retry }))
    expect(await screen.findByText('Su')).toBeInTheDocument()
    // No printed serving: weigh it in grams
    expect(screen.getByRole('tab', { name: t.byGrams })).toHaveAttribute('aria-selected', 'true')
  })
})

describe('portion in grams', () => {
  const oats = {
    id: 7, category: 'breakfast', nameTr: 'Oats', nameEn: 'Oats', kcalPer100g: 380, proteinPer100g: 13, carbsPer100g: 66, fatPer100g: 7,
    servingG: 40, servingTr: 'bowl', servingEn: 'bowl',
  }

  it('sends the grams of a catalog food, counting servings as grams too', async () => {
    fakeServer({ 'GET /api/v1/foods': () => jsonResponse(200, [oats]) })
    const { onSave, user } = renderSheet()

    await user.click(await screen.findByRole('button', { name: /^Oats/ }))
    await user.click(screen.getByRole('button', { name: t.moreServings }))
    await user.click(screen.getByTestId('save-food'))
    // One and a half 40 g bowls
    expect(onSave).toHaveBeenLastCalledWith(expect.objectContaining({ caloriesKcal: 228, grams: 60 }))
  })

  it('sends the weighed amount in gram mode', async () => {
    fakeServer({ 'GET /api/v1/foods': () => jsonResponse(200, [oats]) })
    const { onSave, user } = renderSheet()

    await user.click(await screen.findByRole('button', { name: /^Oats/ }))
    await user.click(screen.getByRole('tab', { name: t.byGrams }))
    await user.clear(screen.getByLabelText(t.amountGrams))
    await user.type(screen.getByLabelText(t.amountGrams), '150')
    await user.click(screen.getByTestId('save-food'))
    expect(onSave).toHaveBeenLastCalledWith(expect.objectContaining({ name: 'Oats (150 g)', caloriesKcal: 570, grams: 150 }))
  })

  it('leaves grams out of a hand-typed entry', async () => {
    fakeServer({ 'GET /api/v1/foods': () => jsonResponse(200, []) })
    const { onSave, user } = renderSheet()

    await user.click(await screen.findByRole('button', { name: t.enterManually }))
    await user.type(screen.getByLabelText(t.foodName), 'Homemade soup')
    await user.type(screen.getByLabelText(t.caloriesKcal), '250')
    await user.click(screen.getByTestId('save-food'))
    expect(onSave).toHaveBeenCalledTimes(1)
    expect(onSave.mock.calls[0][0]).not.toHaveProperty('grams')
  })
})

describe('category chips', () => {
  const row = (id: number, category: string, name: string) => ({
    id, category, nameTr: name, nameEn: name, kcalPer100g: 250, proteinPer100g: 12, carbsPer100g: 20, fatPer100g: 13, servingG: 100, servingTr: '1 adet', servingEn: '1 piece',
  })
  const foods = [
    row(1, 'dish', 'Mercimek corbasi'),
    row(2, 'fastfood', "Big Mac (McDonald's)"),
    row(3, 'fastfood', 'Whopper (Burger King)'),
    row(4, 'supplement', 'Whey izolat'),
  ]
  const shownFoods = () => [...document.querySelectorAll('.food-result-name')].map((el) => el.textContent)

  it('narrows the list and the search to one category, in a sideways strip the tab swipe leaves alone', async () => {
    fakeServer({ 'GET /api/v1/foods': () => jsonResponse(200, foods) })
    const { user } = renderSheet()
    await screen.findByText('Whey izolat')

    const strip = screen.getByRole('group', { name: t.foodCategoryFilter })
    expect(strip).toHaveClass('chip-row')
    // "All" first, then the categories that have foods, in browsing order
    expect([...strip.querySelectorAll('button')].map((b) => b.textContent)).toEqual([
      t.allFoods,
      t.foodCategoryChips.dish,
      t.foodCategoryChips.fastfood,
      t.foodCategoryChips.supplement,
    ])

    await user.click(screen.getByRole('button', { name: t.foodCategoryChips.fastfood }))
    expect(screen.getByRole('button', { name: t.foodCategoryChips.fastfood })).toHaveAttribute('aria-pressed', 'true')
    expect(shownFoods()).toEqual(["Big Mac (McDonald's)", 'Whopper (Burger King)'])

    // Typing searches inside the chosen category only
    await user.type(screen.getByLabelText(t.searchFoods), 'mcdonalds')
    expect(shownFoods()).toEqual(["Big Mac (McDonald's)"])
    await user.clear(screen.getByLabelText(t.searchFoods))
    await user.type(screen.getByLabelText(t.searchFoods), 'whey')
    expect(shownFoods()).toEqual([])

    // Tapping the chosen chip again, or "All", shows everything
    await user.click(screen.getByRole('button', { name: t.foodCategoryChips.fastfood }))
    expect(shownFoods()).toEqual(['Whey izolat'])
    await user.clear(screen.getByLabelText(t.searchFoods))
    await user.click(screen.getByRole('button', { name: t.foodCategoryChips.supplement }))
    await user.click(screen.getByRole('button', { name: t.allFoods }))
    expect(shownFoods()).toHaveLength(4)
  })

  it('keeps the chosen category after coming back from the amount step', async () => {
    fakeServer({ 'GET /api/v1/foods': () => jsonResponse(200, foods) })
    const { user } = renderSheet()
    await user.click(await screen.findByRole('button', { name: t.foodCategoryChips.supplement }))
    await user.click(screen.getByRole('button', { name: /^Whey izolat/ }))
    await user.click(screen.getByRole('button', { name: new RegExp(t.back) }))
    expect(screen.getByRole('button', { name: t.foodCategoryChips.supplement })).toHaveAttribute('aria-pressed', 'true')
    expect(shownFoods()).toEqual(['Whey izolat'])
  })
})
