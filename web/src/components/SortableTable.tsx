import { useState, type ReactNode } from 'react'
import { t } from '../lib/i18n'
import { nextSort, sortRows, type SortDirection, type SortState } from '../lib/lolStats'

export interface SortableColumn<T> {
  key: string
  label: string
  /** What the column sorts by; null or undefined sorts last and usually renders as a dash */
  sortValue: (row: T) => number | string | null | undefined
  render: (row: T) => ReactNode
  /** The direction of the first tap: high to low for numbers, A to Z for names */
  firstDirection?: SortDirection
  /** Marks the cell holding the column's top value */
  isLeader?: (row: T) => boolean
}

/**
 * A table whose headers sort it: a tap sorts by that column, a second tap reverses it. The first column stays
 * put while the rest scrolls sideways inside the card, so a wide table fits a phone without the page scrolling.
 */
export function SortableTable<T>({ rows, columns, rowKey, initialSort, rowClassName, label }: {
  rows: readonly T[]
  columns: SortableColumn<T>[]
  rowKey: (row: T) => string
  initialSort: SortState
  rowClassName?: (row: T) => string | undefined
  /** Read out as the table's name */
  label: string
}) {
  const [sort, setSort] = useState(initialSort)
  const sortColumn = columns.find((column) => column.key === sort.key) ?? columns[0]
  const sorted = sortRows(rows, sortColumn.sortValue, sort.direction)

  return (
    <div className="data-table-card">
      <div className="data-table-scroll">
        <table className="data-table" aria-label={label}>
          <thead>
            <tr>
              {columns.map((column) => {
                const active = column.key === sort.key
                return (
                  <th key={column.key} scope="col" aria-sort={active ? (sort.direction === 'asc' ? 'ascending' : 'descending') : 'none'} className={active ? 'sorted' : undefined}>
                    <button type="button" title={t.sortBy(column.label)} onClick={() => setSort((current) => nextSort(current, column.key, column.firstDirection ?? 'desc'))}>
                      {column.label}
                      <span className="sort-arrow" aria-hidden>
                        {active ? (sort.direction === 'asc' ? '▲' : '▼') : ''}
                      </span>
                    </button>
                  </th>
                )
              })}
            </tr>
          </thead>
          <tbody>
            {sorted.map((row) => (
              <tr key={rowKey(row)} className={rowClassName?.(row)}>
                {columns.map((column, index) =>
                  index === 0 ? (
                    <th key={column.key} scope="row">
                      {column.render(row)}
                    </th>
                  ) : (
                    <td key={column.key} className={column.isLeader?.(row) ? 'num leader' : 'num'}>
                      {column.render(row)}
                    </td>
                  ),
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
