import { useEffect, useRef } from 'react'
import type { Actor, Application, ApplicationEvent } from '../../api/client'
import { ErrorNotice } from '../../components/ErrorNotice'
import { formatDateTime, formatRelative } from '../../lib/format'
import { StatusBadge } from './StatusBadge'
import { useApplicationEvents } from './hooks'

/**
 * Who gets the credit, in the words a person would use.
 *
 * A job names itself; a person is just "You", because until phase 5 there is
 * only one. SYSTEM covers events the application wrote about itself - today
 * that means the rows V4 backfilled, which genuinely were not anybody's doing.
 */
function actorLabel(actor: Actor, detail: string | undefined): string {
  if (actor === 'AUTOMATION') return detail ?? 'Automation'
  if (actor === 'SYSTEM') return 'System'
  return 'You'
}

/** Automation is the one worth spotting at a glance - that is the whole point. */
const actorStyles: Record<Actor, string> = {
  HUMAN: 'bg-neutral-soft text-neutral-ink ring-neutral-line',
  AUTOMATION: 'bg-brand-soft text-brand-ink ring-brand-line',
  SYSTEM: 'bg-inert-soft text-inert-ink ring-inert-line',
}

/**
 * The headline for one event.
 *
 * Note the narrowing: `toStatus` is optional in the generated types because the
 * API says it is absent on ARCHIVED, so TypeScript will not let this render a
 * badge without checking. That is the database's CHECK constraint reaching all
 * the way into the component - the same rule, enforced three times over, and
 * not once written down twice.
 */
function EventSummary({ event }: { event: ApplicationEvent }) {
  if (event.type === 'STATUS_CHANGED' && event.fromStatus && event.toStatus) {
    return (
      <span className="inline-flex flex-wrap items-center gap-1.5">
        <StatusBadge status={event.fromStatus} />
        <svg
          aria-hidden="true"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="size-3 text-ink-soft"
        >
          <path d="M5 12h14" />
          <path d="m13 6 6 6-6 6" />
        </svg>
        <span className="sr-only">changed to</span>
        <StatusBadge status={event.toStatus} />
      </span>
    )
  }

  if (event.type === 'CREATED' && event.toStatus) {
    return (
      <span className="inline-flex flex-wrap items-center gap-1.5">
        <span className="text-ink">Added as</span>
        <StatusBadge status={event.toStatus} />
      </span>
    )
  }

  return <span className="text-ink">Archived</span>
}

function TimelineEntry({ event, isLast }: { event: ApplicationEvent; isLast: boolean }) {
  return (
    <li className="relative flex gap-3 pb-5 last:pb-0">
      {/* The rail is drawn per-entry and skipped on the last one, so it stops at
          the final dot instead of trailing into empty space below it. */}
      {!isLast && (
        <span aria-hidden="true" className="absolute left-[3px] top-3 h-full w-px bg-line" />
      )}
      <span
        aria-hidden="true"
        className="relative mt-1.5 size-[7px] shrink-0 rounded-full bg-ink-soft"
      />

      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
          <EventSummary event={event} />
          {/* The exact time is the tooltip and the accessible name; the relative
              one is what you actually read. */}
          <time
            dateTime={event.occurredAt}
            title={formatDateTime(event.occurredAt)}
            className="shrink-0 text-xs text-ink-soft"
          >
            {formatRelative(event.occurredAt)}
          </time>
        </div>

        <div className="mt-1.5 flex flex-wrap items-center gap-2">
          <span
            className={`inline-flex items-center rounded px-1.5 py-0.5 text-[11px] font-medium ring-1 ring-inset ${actorStyles[event.actor]}`}
          >
            {actorLabel(event.actor, event.actorDetail)}
          </span>
          {event.note && <span className="text-xs text-ink-soft">{event.note}</span>}
        </div>
      </div>
    </li>
  )
}

/**
 * An application's history, newest first.
 *
 * Read-only on purpose. Everything here is a consequence of something you did
 * elsewhere in the app, and an editable history would answer "what do we
 * currently claim happened" - which is the question the table already answers.
 */
export function ApplicationTimelineDialog({
  application,
  onClose,
}: {
  application: Application
  onClose: () => void
}) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const { data: events, isPending, isError, error } = useApplicationEvents(application.id)

  useEffect(() => {
    const dialog = dialogRef.current
    // showModal(), not the open attribute - see the longer note in
    // EditApplicationDialog for why the difference matters.
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  function handleKeyDown(event: React.KeyboardEvent<HTMLDialogElement>) {
    if (event.key === 'Escape') {
      event.preventDefault()
      onClose()
    }
  }

  function handleBackdropClick(event: React.MouseEvent<HTMLDialogElement>) {
    if (event.target === dialogRef.current) onClose()
  }

  return (
    <dialog
      ref={dialogRef}
      onKeyDown={handleKeyDown}
      onClick={handleBackdropClick}
      aria-labelledby="timeline-heading"
      className="m-auto w-full max-w-lg rounded-lg border border-line bg-surface p-0 text-ink"
    >
      <div className="p-6">
        <h2 id="timeline-heading" className="text-base font-semibold">
          History
        </h2>
        <p className="mt-1 text-xs text-ink-soft">
          {application.roleTitle} at {application.company.name}
        </p>

        <div className="mt-5">
          {isPending && <p className="py-8 text-center text-sm text-ink-soft">Loading…</p>}

          {isError && <ErrorNotice error={error} />}

          {events && events.length === 0 && (
            <p className="py-8 text-center text-sm text-ink-soft">
              Nothing recorded yet.
            </p>
          )}

          {events && events.length > 0 && (
            <ol className="max-h-[24rem] overflow-y-auto pr-1">
              {events.map((event, index) => (
                <TimelineEntry
                  key={event.id}
                  event={event}
                  isLast={index === events.length - 1}
                />
              ))}
            </ol>
          )}
        </div>

        <div className="mt-6 flex justify-end">
          <button
            type="button"
            onClick={onClose}
            className="rounded-md border border-line px-4 py-2 text-sm text-ink-soft transition hover:border-brand hover:text-brand"
          >
            Close
          </button>
        </div>
      </div>
    </dialog>
  )
}
