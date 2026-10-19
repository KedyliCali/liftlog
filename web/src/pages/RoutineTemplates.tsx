import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useId, useMemo, useState } from 'react'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { Routine } from '../api/types'
import { ErrorState } from '../components/StateViews'
import { useToast } from '../components/contexts'
import { errorMessage } from '../lib/format'
import { t } from '../lib/i18n'
import { ROUTINE_TEMPLATES, resolveTemplate, templateName, templateRoutineInput, templateSummary, type RoutineTemplate } from '../lib/routineTemplates'
import { useStartRoutine } from './useStartRoutine'

/**
 * The template catalog. "list" is the full card list of the Routines sheet; "strip" is a row of small
 * tiles that scrolls sideways, for places where the list would push everything else down.
 */
export function TemplateCards({ variant, onSelect }: { variant: 'list' | 'strip'; onSelect: (template: RoutineTemplate) => void }) {
  if (variant === 'strip') {
    return (
      <div className="template-chips">
        {ROUTINE_TEMPLATES.map((template) => (
          <button key={template.id} className="template-chip" onClick={() => onSelect(template)} title={templateSummary(template)}>
            <span className="template-chip-front">
              <span className="template-chip-tag">{t.templateTag}</span>
              <strong>{templateName(template)}</strong>
              <small>{t.exercises(template.exercises.length)}</small>
            </span>
          </button>
        ))}
      </div>
    )
  }
  return (
    <div className="template-cards">
      {ROUTINE_TEMPLATES.map((template) => (
        <TemplateCard key={template.id} template={template} onSelect={onSelect} />
      ))}
    </div>
  )
}

/** A card lifted off a lime plate, with its focus tag straddling the top edge and the exercise count in the corner. */
function TemplateCard({ template, onSelect }: { template: RoutineTemplate; onSelect: (template: RoutineTemplate) => void }) {
  const summaryId = useId()
  return (
    <button className="template-card" onClick={() => onSelect(template)} aria-label={templateName(template)} aria-describedby={summaryId}>
      <span className="template-card-tags" aria-hidden>
        <span className="template-card-tag">{t.templateFocus[template.focus]}</span>
        {template.beginner && <span className="template-card-tag beginner">{t.beginnerTag}</span>}
      </span>
      <span className="template-card-front">
        <strong>{templateName(template)}</strong>
        <span className="template-card-summary" id={summaryId}>
          {templateSummary(template)}
        </span>
      </span>
      <span className="template-card-count num" aria-hidden>
        {template.exercises.length}
      </span>
    </button>
  )
}

/**
 * One template up close: its exercises (resolved against the user's own list), then "Add to my routines"
 * or "Add and start". startDate is the day "Add and start" logs the workout on; the phone's date by default.
 */
export function TemplatePreview({
  template,
  onBack,
  backLabel,
  onAdded,
  onStarted,
  startDate,
}: {
  template: RoutineTemplate
  onBack: () => void
  backLabel: string
  onAdded: (routine: Routine) => void
  onStarted?: () => void
  startDate?: string
}) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const catalog = useQuery({ queryKey: queryKeys.exercises, queryFn: api.exercises, staleTime: 5 * 60_000 })
  const resolved = useMemo(() => (catalog.data ? resolveTemplate(template, catalog.data) : null), [catalog.data, template])
  const { start } = useStartRoutine(onStarted)
  const [busy, setBusy] = useState<'adding' | 'starting' | null>(null)
  // If starting fails after the routine was saved, a second tap starts that routine instead of adding a copy
  const [created, setCreated] = useState<Routine | null>(null)

  const input = resolved ? templateRoutineInput(template, resolved) : null
  const missing = new Set(resolved?.missing ?? [])

  const add = async (andStart: boolean) => {
    if (busy || !input) return
    setBusy(andStart ? 'starting' : 'adding')
    try {
      const routine = created ?? (await api.createRoutine(input))
      setCreated(routine)
      await queryClient.invalidateQueries({ queryKey: queryKeys.routines })
      if (andStart) {
        await start(routine, startDate)
        setBusy(null)
      } else {
        toast({ message: t.templateAdded(routine.name) })
        onAdded(routine)
      }
    } catch (error) {
      toast({ message: errorMessage(error) })
      setBusy(null)
    }
  }

  return (
    <div className="stack template-preview">
      <button type="button" className="btn-text btn" style={{ alignSelf: 'flex-start', padding: 0 }} onClick={onBack}>
        ‹ {backLabel}
      </button>

      <div className="template-hero">
        <span className="template-hero-tag">{t.templateTag}</span>
        <h3 className="template-hero-name">{templateName(template)}</h3>
        <div className="chips">
          <span className="chip">{t.templateFocus[template.focus]}</span>
          {template.beginner && <span className="chip">{t.beginnerTag}</span>}
          <span className="chip">{t.exercises(template.exercises.length)}</span>
        </div>
      </div>

      {catalog.isPending ? (
        <div className="spinner" style={{ margin: '24px auto' }} />
      ) : catalog.isError ? (
        <ErrorState error={catalog.error} onRetry={() => catalog.refetch()} />
      ) : (
        <>
          <ol className="template-lines">
            {template.exercises.map((exercise, index) => (
              <li key={exercise.name} className={missing.has(exercise.name) ? 'template-line missing' : 'template-line'}>
                <span className="template-line-index num">{index + 1}</span>
                <span className="template-line-name">{exercise.name}</span>
                <span className="template-line-scheme num">
                  {exercise.sets}×{exercise.targetReps}
                </span>
              </li>
            ))}
          </ol>
          {input === null ? (
            <p className="field-error" role="alert">
              {t.templateNothingResolved}
            </p>
          ) : (
            missing.size > 0 && (
              <p className="template-missing" role="status">
                {t.templateMissing([...missing].join(', '))}
              </p>
            )
          )}
        </>
      )}

      <button className="btn btn-primary btn-block" onClick={() => add(false)} disabled={!input || busy !== null} data-testid="template-add">
        {busy === 'adding' ? <span className="spinner small" /> : t.addToRoutines}
      </button>
      <button className="btn btn-tonal btn-block" onClick={() => add(true)} disabled={!input || busy !== null} data-testid="template-add-start">
        {busy === 'starting' ? <span className="spinner small" /> : t.addAndStart}
      </button>
    </div>
  )
}
