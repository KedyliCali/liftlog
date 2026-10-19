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
  /** null keeps the rows in the given order until a header is tapped */
  initialSort: SortState | null
  rowClassName?: (row: T) => string | undefined
  /** Read out as the table's name */
  label: string
}) {
  const [sort, setSort] = useState<SortState | null>(initialSort)
  // A column can disappear (the match page's detail columns): its sort then falls back to the given order
  const sortColumn = sort ? columns.find((column) => column.key === sort.key) : undefined
  const sorted = sort && sortColumn ? sortRows(rows, sortColumn.sortValue, sort.direction) : rows

  return (
    <div className="data-table-card">
      <div className="data-table-scroll">
        <table className="data-table" aria-label={label}>
          <thead>
            <tr>
              {columns.map((column) => {
                const direction = sort && column.key === sort.key ? sort.direction : null
                return (
                  <th key={column.key} scope="col" aria-sort={direction ? (direction === 'asc' ? 'ascending' : 'descending') : 'none'} className={direction ? 'sorted' : undefined}>
                    <button
                      type="button"
                      title={t.sortBy(column.label)}
                      onClick={() => setSort((current) => (current ? nextSort(current, column.key, column.firstDirection ?? 'desc') : { key: column.key, direction: column.firstDirection ?? 'desc' }))}
                    >
                      {column.label}
                      <span className="sort-arrow" aria-hidden>
                        {direction ? (direction === 'asc' ? '▲' : '▼') : ''}
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
