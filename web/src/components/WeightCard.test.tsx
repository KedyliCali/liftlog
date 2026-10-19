import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { queryKeys } from '../api/queryKeys'
import type { WeightEntry, WeightHistory } from '../api/types'
import { formatKg } from '../lib/format'
import { t } from '../lib/i18n'
import { shiftDay, todayIso } from '../lib/nutrition'
import { formatWeight, formatWeightChange } from '../lib/weight'
import { fakeServer, jsonResponse } from '../test/fakeServer'
import { ToastProvider } from './Toast'
import { WeightCard } from './WeightCard'

const today = todayIso()

/** In-memory weight API: PUT upserts by date and GET answers from what is stored, like the server. */
function weightServer(initial: WeightEntry[], trend: Partial<WeightHistory> = {}) {
  let entries = [...initial]
  const history = (): WeightHistory => {
    const sorted = [...entries].sort((a, b) => a.date.localeCompare(b.date))
    return { entries: sorted, latest: sorted.at(-1) ?? null, change7d: null, change30d: null, average7d: null, ...trend }
  }
  const calls = fakeServer({
    'GET /api/v1/weight': () => jsonResponse(200, history()),
    [`PUT /api/v1/weight/${today}`]: (body) => {
      const entry = { date: today, weightKg: (body as { weightKg: number }).weightKg }
      entries = [...entries.filter((e) => e.date !== today), entry]
      return jsonResponse(200, entry)
    },
    [`DELETE /api/v1/weight/${today}`]: () => {
      entries = entries.filter((e) => e.date !== today)
      return jsonResponse(204, undefined)
    },
  })
  return { calls, entries: () => entries }
}

function renderCard(profileWeightKg: number | null = null) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <WeightCard profileWeightKg={profileWeightKg} />
      </ToastProvider>
    </QueryClientProvider>,
  )
  return { user: userEvent.setup(), client }
}

const field = () => screen.getByRole('textbox', { name: t.weightInput })
const stepButton = (delta: number) => screen.getByRole('button', { name: t.weightStep(formatWeightChange(delta).replace(' kg', '')) })

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('WeightCard', () => {
  it('shows the latest weight with its change chips and draws the chart', async () => {
    weightServer(
      [
        { date: shiftDay(today, -7), weightKg: 81.2 },
        { date: today, weightKg: 80.4 },
      ],
      { change7d: -0.8, change30d: -2.5, average7d: 80.84 },
    )
    renderCard()

    expect(await screen.findByTestId('weight-latest')).toHaveTextContent(`${t.weightToday}${formatKg(80.4)}kg`)
    expect(screen.getByTestId('weight-change-7')).toHaveTextContent(t.weightChange(7, formatWeightChange(-0.8)))
    expect(screen.getByTestId('weight-change-7')).toHaveClass('down')
    expect(screen.getByTestId('weight-change-30')).toHaveTextContent(t.weightChange(30, formatWeightChange(-2.5)))
    expect(screen.getByText(t.weightAverage(formatWeight(80.84)))).toBeInTheDocument()
    expect(screen.getByTestId('weight-chart')).toBeInTheDocument()
    // Today already has an entry, so saving replaces it
    expect(screen.getByTestId('save-weight')).toHaveTextContent(t.weightUpdate)
  })

  it("steps from today's weight and saves it for today", async () => {
    const server = weightServer([{ date: today, weightKg: 80.4 }])
    const { user, client } = renderCard()
    client.setQueryData(queryKeys.profile, { id: 1, displayName: null, heightCm: null, bodyWeightKg: 80.4, avatarVersion: null })

    await waitFor(() => expect(field()).toHaveValue('80.4'))
    await user.click(stepButton(0.1))
    await user.click(stepButton(0.5))
    await user.click(stepButton(-0.1))
    expect(field()).toHaveValue('80.9')

    await user.click(screen.getByTestId('save-weight'))

    expect(await screen.findByText(t.weightSaved)).toBeInTheDocument()
    expect(server.calls.filter((c) => c.method === 'PUT')).toEqual([{ method: 'PUT', path: `/api/v1/weight/${today}`, body: { weightKg: 80.9 } }])
    // The profile is refreshed too: the server copies the newest weight onto it
    expect(client.getQueryState(queryKeys.profile)?.isInvalidated).toBe(true)
    await waitFor(() => expect(screen.getByTestId('weight-latest')).toHaveTextContent(formatKg(80.9)))
  })

  it('starts from the profile weight and explains the empty chart', async () => {
    const server = weightServer([])
    const { user } = renderCard(82)

    expect(await screen.findByText(t.weightEmpty)).toBeInTheDocument()
    expect(field()).toHaveValue('82')
    expect(screen.getByTestId('save-weight')).toHaveTextContent(t.weightSave)
    expect(screen.queryByTestId('weight-latest')).not.toBeInTheDocument()

    await user.clear(field())
    await user.type(field(), '79,35')
    await user.click(screen.getByTestId('save-weight'))
    await waitFor(() => expect(server.entries()).toEqual([{ date: today, weightKg: 79.35 }]))
  })

  it('rejects an out-of-range weight without calling the server', async () => {
    const server = weightServer([])
    const { user } = renderCard()

    expect(await screen.findByText(t.weightEmpty)).toBeInTheDocument()
    await user.type(field(), '401')
    await user.click(screen.getByTestId('save-weight'))

    expect(screen.getByText(t.weightInvalid)).toBeInTheDocument()
    expect(server.calls.some((c) => c.method === 'PUT')).toBe(false)
  })

  it("deletes today's entry", async () => {
    const server = weightServer([
      { date: shiftDay(today, -1), weightKg: 81 },
      { date: today, weightKg: 80.4 },
    ])
    const { user } = renderCard()

    await user.click(await screen.findByRole('button', { name: t.weightDeleteToday }))

    expect(await screen.findByText(t.weightDeleted)).toBeInTheDocument()
    expect(server.calls.filter((c) => c.method === 'DELETE').map((c) => c.path)).toEqual([`/api/v1/weight/${today}`])
    await waitFor(() => expect(screen.queryByRole('button', { name: t.weightDeleteToday })).not.toBeInTheDocument())
  })

  it('asks for the chosen range ending on the phone date', async () => {
    weightServer([])
    const { user } = renderCard()

    await user.click(await screen.findByRole('tab', { name: t.weightRange(90) }))

    await waitFor(() => {
      const urls = vi.mocked(fetch).mock.calls.map(([url]) => String(url))
      expect(urls.some((url) => url.endsWith(`/api/v1/weight?days=90&today=${today}`))).toBe(true)
    })
    expect(screen.getByRole('tab', { name: t.weightRange(90) })).toHaveAttribute('aria-selected', 'true')
  })
})
