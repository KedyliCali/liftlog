import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { t } from '../lib/i18n'
import { ConfirmProvider } from './Confirm'
import { useConfirm, type ConfirmOptions } from './contexts'
import { Sheet } from './Sheet'

/** A button that asks and writes the answer on screen, so each test can read what the promise resolved to. */
function Asker({ options }: { options?: ConfirmOptions }) {
  const confirm = useConfirm()
  const [answer, setAnswer] = useState('none')
  return (
    <>
      <button onClick={async () => setAnswer(String(await confirm(options)))}>ask</button>
      <output>{answer}</output>
    </>
  )
}

function renderAsker(options?: ConfirmOptions) {
  const user = userEvent.setup()
  render(
    <ConfirmProvider>
      <Asker options={options} />
    </ConfirmProvider>,
  )
  return user
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('useConfirm', () => {
  it('shows the title, the item and the consequence, and resolves true on Delete', async () => {
    const user = renderAsker({ item: 'Push A', message: 'Past workouts stay.' })
    await user.click(screen.getByText('ask'))

    const dialog = screen.getByRole('alertdialog', { name: t.confirmDeleteTitle })
    expect(dialog).toHaveTextContent('Push A')
    expect(dialog).toHaveTextContent('Past workouts stay.')
    await user.click(screen.getByRole('button', { name: t.delete }))

    expect(screen.getByText('true')).toBeInTheDocument()
    expect(screen.queryByRole('alertdialog')).toBeNull()
  })

  it('resolves false on Cancel', async () => {
    const user = renderAsker()
    await user.click(screen.getByText('ask'))
    await user.click(screen.getByRole('button', { name: t.cancel }))
    expect(screen.getByText('false')).toBeInTheDocument()
    expect(screen.queryByRole('alertdialog')).toBeNull()
  })

  it('resolves false on Escape and on a tap on the dimmed backdrop', async () => {
    const user = renderAsker()
    await user.click(screen.getByText('ask'))
    await user.keyboard('{Escape}')
    expect(screen.getByText('false')).toBeInTheDocument()

    await user.click(screen.getByText('ask'))
    fireEvent.click(screen.getByRole('alertdialog').parentElement!)
    expect(screen.queryByRole('alertdialog')).toBeNull()
    expect(screen.getByText('false')).toBeInTheDocument()
  })

  it('puts focus on Cancel, so a stray Enter does not delete', async () => {
    const user = renderAsker()
    await user.click(screen.getByText('ask'))
    expect(screen.getByRole('button', { name: t.cancel })).toHaveFocus()
    await user.keyboard('{Enter}')
    expect(screen.getByText('false')).toBeInTheDocument()
  })

  it('uses the given labels for a confirm that removes nothing', async () => {
    const user = renderAsker({ title: 'New code?', confirmLabel: 'Make it', tone: 'neutral' })
    await user.click(screen.getByText('ask'))
    expect(screen.getByRole('alertdialog', { name: 'New code?' })).toHaveClass('confirm-neutral')
    await user.click(screen.getByRole('button', { name: 'Make it' }))
    expect(screen.getByText('true')).toBeInTheDocument()
  })

  it('closes only itself on Escape when asked from inside another sheet', async () => {
    const onSheetClose = vi.fn()
    const user = userEvent.setup()
    render(
      <ConfirmProvider>
        <Sheet title="Editor" onClose={onSheetClose}>
          <Asker />
        </Sheet>
      </ConfirmProvider>,
    )
    await user.click(screen.getByText('ask'))
    await user.keyboard('{Escape}')
    expect(screen.getByText('false')).toBeInTheDocument()
    expect(onSheetClose).not.toHaveBeenCalled()
  })

  it('answers false to an older question when a new one replaces it', async () => {
    let first: Promise<boolean> | undefined
    function Twice() {
      const confirm = useConfirm()
      return <button onClick={() => (first ??= confirm())}>first</button>
    }
    const user = userEvent.setup()
    render(
      <ConfirmProvider>
        <Twice />
        <Asker />
      </ConfirmProvider>,
    )
    await user.click(screen.getByText('first'))
    // The sheet is modal for people; a second ask can only come from code, so trigger it directly
    await act(async () => screen.getByText('ask').click())
    await expect(first).resolves.toBe(false)
    expect(screen.getAllByRole('alertdialog')).toHaveLength(1)
  })

  it('throws a clear error without the provider', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    expect(() => render(<Asker />)).toThrow(/ConfirmProvider/)
  })
})
