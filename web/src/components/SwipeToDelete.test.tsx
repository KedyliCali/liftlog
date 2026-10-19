import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { t } from '../lib/i18n'
import { swipeRowLeft } from '../test/swipe'
import { ConfirmProvider } from './Confirm'
import { useConfirm } from './contexts'
import { SwipeToDelete } from './SwipeToDelete'

function Row({ onDelete }: { onDelete: () => void }) {
  const confirm = useConfirm()
  return (
    <SwipeToDelete label="Bench Press #3" confirm={() => confirm({ item: 'Bench Press #3' })} onDelete={onDelete}>
      <span>row</span>
    </SwipeToDelete>
  )
}

function renderRow() {
  const onDelete = vi.fn()
  const user = userEvent.setup()
  render(
    <ConfirmProvider>
      <Row onDelete={onDelete} />
    </ConfirmProvider>,
  )
  return { onDelete, user }
}

afterEach(() => cleanup())

describe('SwipeToDelete', () => {
  it('asks first, and a cancel slides the row back without deleting', async () => {
    const { onDelete, user } = renderRow()
    const row = await swipeRowLeft('Bench Press #3')

    expect(screen.getByRole('alertdialog')).toHaveTextContent('Bench Press #3')
    expect(row.style.transform).not.toBe('translateX(0px)')
    await user.click(screen.getByRole('button', { name: t.cancel }))

    await waitFor(() => expect(row.style.transform).toBe('translateX(0px)'))
    // Longer than the slide-out delay, so a late delete would have shown up
    await new Promise((resolve) => setTimeout(resolve, 250))
    expect(onDelete).not.toHaveBeenCalled()
  })

  it('deletes after the confirm', async () => {
    const { onDelete, user } = renderRow()
    await swipeRowLeft('Bench Press #3')
    await user.click(screen.getByRole('button', { name: t.delete }))
    await waitFor(() => expect(onDelete).toHaveBeenCalledOnce())
  })
})
