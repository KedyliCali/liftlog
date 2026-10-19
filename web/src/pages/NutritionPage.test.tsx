import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { FoodEntry } from '../api/types'
import { ToastProvider } from '../components/Toast'
import { formatKg } from '../lib/format'
import { t } from '../lib/i18n'
import { shiftDay, todayIso } from '../lib/nutrition'
import { fakeServer, jsonResponse } from '../test/fakeServer'
import { NutritionPage } from './NutritionPage'

const today = todayIso()
const yesterday = shiftDay(today, -1)
const goals = { calorieGoalKcal: null, proteinGoalG: null }

const oats: FoodEntry = { id: 1, entryDate: yesterday, name: 'Oats', caloriesKcal: 380, proteinG: 13.5, carbsG: 60, fatG: 6.5 }
const shake: FoodEntry = { id: 2, entryDate: yesterday, name: 'Protein shake', caloriesKcal: 120, proteinG: 24, carbsG: null, fatG: null }

/** A tiny in-memory nutrition backend: days hold entries, copy and delete change them like the real API. */
function nutritionServer(initial: FoodEntry[]) {
  let entries = [...initial]
  let nextId = 100
  const dayOf = (date: string) => {
    const list = entries.filter((e) => e.entryDate === date)
    return { date, entries: list, totalCaloriesKcal: list.reduce((s, e) => s + e.caloriesKcal, 0), totalProteinG: 0, totalCarbsG: 0, totalFatG: 0, goals }
  }
  const routes: Record<string, () => Response> = {
    'GET /api/v1/nutrition/frequent': () => jsonResponse(200, []),
    [`GET /api/v1/nutrition/days/${today}`]: () => jsonResponse(200, dayOf(today)),
    [`POST /api/v1/nutrition/days/${today}/copy-from/${yesterday}`]: () => {
      const copies = entries.filter((e) => e.entryDate === yesterday).map((e) => ({ ...e, id: nextId++, entryDate: today }))
      entries = [...entries, ...copies]
      return jsonResponse(201, copies)
    },
  }
  for (const id of [100, 101, 102]) {
    routes[`DELETE /api/v1/nutrition/entries/${id}`] = () => {
      entries = entries.filter((e) => e.id !== id)
      return jsonResponse(204, undefined)
    }
  }
  const calls = fakeServer(routes)
  return { calls, entriesOn: (date: string) => entries.filter((e) => e.entryDate === date) }
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        {/* The weight chip is a router link */}
        <MemoryRouter>
          <NutritionPage />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('copy yesterday', () => {
  it('copies the previous day into an empty day and undoes exactly the copies', async () => {
    const server = nutritionServer([oats, shake])
    const user = renderPage()

    await user.click(await screen.findByTestId('copy-previous-day'))

    expect(await screen.findByText(t.copiedEntries(2))).toBeInTheDocument()
    expect(await screen.findByText('Oats')).toBeInTheDocument()
    expect(screen.getByText('Protein shake')).toBeInTheDocument()
    expect(server.calls.filter((c) => c.method === 'POST')).toEqual([{ method: 'POST', path: `/api/v1/nutrition/days/${today}/copy-from/${yesterday}`, body: undefined }])

    await user.click(within(screen.getByRole('status')).getByRole('button', { name: t.undo }))
    await waitFor(() => expect(server.entriesOn(today)).toEqual([]))
    expect(server.calls.filter((c) => c.method === 'DELETE').map((c) => c.path)).toEqual(['/api/v1/nutrition/entries/100', '/api/v1/nutrition/entries/101'])
    // Yesterday is untouched and today is empty again
    expect(server.entriesOn(yesterday)).toHaveLength(2)
    expect(await screen.findByTestId('copy-previous-day')).toBeInTheDocument()
  })

  it('is also in the day menu when the day already has entries', async () => {
    const existing: FoodEntry = { ...oats, id: 50, entryDate: today, name: 'Banana', caloriesKcal: 90 }
    const server = nutritionServer([oats, existing])
    const user = renderPage()

    expect(await screen.findByText('Banana')).toBeInTheDocument()
    expect(screen.queryByTestId('copy-previous-day')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: t.dayMenu }))
    await user.click(screen.getByRole('button', { name: t.copyYesterday }))

    expect(await screen.findByText(t.copiedEntries(1))).toBeInTheDocument()
    expect(await screen.findByText('Oats')).toBeInTheDocument()
    expect(server.entriesOn(today).map((e) => e.name)).toEqual(['Banana', 'Oats'])
  })

  it('says so when the previous day is empty', async () => {
    nutritionServer([])
    const user = renderPage()
    await user.click(await screen.findByTestId('copy-previous-day'))
    expect(await screen.findByText(t.nothingToCopy)).toBeInTheDocument()
  })
})

describe('weight chip', () => {
  const day = { date: today, entries: [], totalCaloriesKcal: 0, totalProteinG: 0, totalCarbsG: 0, totalFatG: 0, goals }

  it('shows the current weight and links to the weight log', async () => {
    fakeServer({
      'GET /api/v1/nutrition/frequent': () => jsonResponse(200, []),
      [`GET /api/v1/nutrition/days/${today}`]: () => jsonResponse(200, day),
      'GET /api/v1/profile': () => jsonResponse(200, { id: 1, displayName: null, heightCm: 180, bodyWeightKg: 82.4, avatarVersion: null }),
    })
    renderPage()

    const chip = await screen.findByTestId('weight-link-chip')
    expect(chip).toHaveAttribute('href', '/profile#weight')
    expect(chip).toHaveAccessibleName(t.weightChipLabel(formatKg(82.4)))
  })

  it('is hidden without a weight', async () => {
    fakeServer({
      'GET /api/v1/nutrition/frequent': () => jsonResponse(200, []),
      [`GET /api/v1/nutrition/days/${today}`]: () => jsonResponse(200, day),
      'GET /api/v1/profile': () => jsonResponse(200, { id: 1, displayName: null, heightCm: null, bodyWeightKg: null, avatarVersion: null }),
    })
    renderPage()

    expect(await screen.findByTestId('copy-previous-day')).toBeInTheDocument()
    expect(screen.queryByTestId('weight-link-chip')).not.toBeInTheDocument()
  })
})
