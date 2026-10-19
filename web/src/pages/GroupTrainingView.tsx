import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { GroupTrainingMember } from '../api/types'
import { ShareTrainingCard } from '../components/ShareTraining'
import { SortableTable, type SortableColumn } from '../components/SortableTable'
import { WorkoutListSkeleton } from '../components/Skeletons'
import { ErrorState } from '../components/StateViews'
import { formatKg, formatShortDate } from '../lib/format'
import { t } from '../lib/i18n'
import { BIG_LIFTS, bestOf, columnLeaders, splitTraining, type TrainingRow } from '../lib/groupTraining'
import { todayIso } from '../lib/nutrition'

type Days = 7 | 30

const nameOf = (member: { displayName?: string | null }) => member.displayName ?? t.memberFallback
const dash = (value: number | null, format: (n: number) => string) => (value == null ? '–' : format(value))

/** The group compared on training: who opted in, side by side; who did not, by name only. */
export function GroupTrainingView() {
  const [days, setDays] = useState<Days>(7)
  // The phone's date, so "7 days" ends on the user's today even when the server's clock is already tomorrow
  const [today] = useState(todayIso)
  const query = useQuery({ queryKey: queryKeys.groupTraining(days, today), queryFn: () => api.groupTraining(days, today) })

  let content
  if (query.isPending) content = <WorkoutListSkeleton />
  else if (query.isError) content = <ErrorState error={query.error} onRetry={() => query.refetch()} />
  else if (query.data === null) content = <p className="muted training-empty">{t.trainingNoGroup}</p>
  else {
    const { rows, hidden } = splitTraining(query.data.members)
    const me = rows.find((row) => row.you)
    content = (
      <>
        {me && !me.shares && <ShareTrainingCard />}
        <p className="muted training-period num">{t.trainingPeriod(formatShortDate(query.data.from), formatShortDate(query.data.to))}</p>
        {rows.length > 0 && <TrainingTable rows={rows} />}
        {hidden.length > 0 && <NotSharingList members={hidden} />}
      </>
    )
  }

  return (
    <div className="training-view">
      <div className="board-toggle" role="group" aria-label={t.trainingDaysLabel}>
        {([7, 30] as const).map((option) => (
          <button key={option} type="button" aria-pressed={days === option} className={days === option ? 'selected' : ''} onClick={() => setDays(option)}>
            {t.trainingDays(option)}
          </button>
        ))}
      </div>
      {content}
    </div>
  )
}

/** Header taps sort; the top value of every column is lit lime. */
export function TrainingTable({ rows }: { rows: TrainingRow[] }) {
  const leaderOf = (value: (row: TrainingRow) => number | null) => {
    const leaders = columnLeaders(rows, value)
    return (row: TrainingRow) => leaders.has(row)
  }
  const numberColumn = (key: string, label: string, value: (row: TrainingRow) => number | null, format: (n: number) => string): SortableColumn<TrainingRow> => ({
    key,
    label,
    sortValue: value,
    render: (row) => dash(value(row), format),
    isLeader: leaderOf(value),
  })

  const columns: SortableColumn<TrainingRow>[] = [
    {
      key: 'member',
      label: t.memberColumn,
      sortValue: (row) => nameOf(row).toLocaleLowerCase(),
      firstDirection: 'asc',
      render: (row) => (
        <span className="table-player">
          <span className="table-player-name">
            {nameOf(row)}
            {row.you && <span className="table-you">{t.youTag}</span>}
          </span>
          {row.you && !row.shares && <small>{t.onlyYouSeeRow}</small>}
        </span>
      ),
    },
    numberColumn('workouts', t.workoutsColumn, (row) => row.workouts, String),
    numberColumn('volume', t.volumeColumn, (row) => row.totalVolumeKg, (n) => t.kg(formatKg(Math.round(n)))),
    numberColumn('streak', t.streakColumn, (row) => row.weekStreak, t.streakWeeksShort),
    ...BIG_LIFTS.map(({ lift, short }) => numberColumn(lift, short, (row) => bestOf(row, lift), (n) => formatKg(Math.round(n * 10) / 10))),
  ]
  return (
    <SortableTable
      label={t.trainingTableLabel}
      rows={rows}
      columns={columns}
      rowKey={(row) => String(row.userId)}
      initialSort={{ key: 'workouts', direction: 'desc' }}
      rowClassName={(row) => (row.you ? 'you' : undefined)}
    />
  )
}

/** Members who did not opt in: greyed names, no numbers (the server sends none). */
function NotSharingList({ members }: { members: GroupTrainingMember[] }) {
  return (
    <ul className="not-sharing-list">
      {members.map((member, index) => (
        <li key={index} className="not-sharing">
          <span>{nameOf(member)}</span>
          <small>{t.notSharing}</small>
        </li>
      ))}
    </ul>
  )
}
