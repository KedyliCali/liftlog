import { fireEvent, screen } from '@testing-library/react'
import { onTestFinished } from 'vitest'

/** Drags a swipe-to-delete row to the left past the threshold and lets go, as a finger would. */
export async function swipeRowLeft(label: string) {
  // jsdom has no pointer capture, which the row asks for once a drag turns horizontal
  Element.prototype.setPointerCapture = () => {}
  onTestFinished(() => {
    delete (Element.prototype as Partial<Element>).setPointerCapture
  })
  const row = await screen.findByLabelText(label)
  fireEvent.pointerDown(row, { clientX: 300, clientY: 10 })
  fireEvent.pointerMove(row, { clientX: 100, clientY: 10 })
  fireEvent.pointerUp(row)
  return row
}
