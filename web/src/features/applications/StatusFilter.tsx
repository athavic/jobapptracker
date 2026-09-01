import { useEffect, useRef, useState } from 'react'
import type { ApplicationStatus } from '../../api/client'
import { titleCase } from '../../lib/format'

/**
 * Every status, in lifecycle order.
 *
 * This order is load-bearing beyond looking tidy: the selection is always
 * rebuilt by filtering this array, never by appending in click order. So
 * ticking Applied then Screen produces the same array as Screen then Applied,
 * which keeps the TanStack Query key stable - the key hashes the filters
 * object, and two arrays with the same members in a different order are two
 * different keys, and therefore two cache entries and two identical requests.
 */
const ALL_STATUSES: ApplicationStatus[] = [
  'DISCOVERED',
  'SAVED',
  'APPLIED',
  'SCREEN',
  'INTERVIEW',
  'OFFER',
  'ACCEPTED',
  'REJECTED',
  'GHOSTED',
  'WITHDRAWN',
]

function summarise(selected: ApplicationStatus[]): string {
  if (selected.length === 0) return 'All'
  if (selected.length === 1) return titleCase(selected[0])
  if (selected.length === ALL_STATUSES.length) return 'All'
  return `${selected.length} selected`
}

export function StatusFilter({
  selected,
  onChange,
}: {
  selected: ApplicationStatus[]
  onChange: (next: ApplicationStatus[]) => void
}) {
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  // A click anywhere outside closes the popover. Bound on the document rather
  // than a full-screen overlay element, so the rest of the filter bar stays
  // clickable - closing this and focusing the company box should be one click,
  // not two.
  useEffect(() => {
    if (!isOpen) return

    function handlePointerDown(event: PointerEvent) {
      if (!containerRef.current?.contains(event.target as Node)) {
        setIsOpen(false)
      }
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') setIsOpen(false)
    }

    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [isOpen])

  function toggle(status: ApplicationStatus) {
    const next = selected.includes(status)
      ? selected.filter((s) => s !== status)
      : ALL_STATUSES.filter((s) => s === status || selected.includes(s))

    onChange(next)
  }

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={() => setIsOpen((open) => !open)}
        aria-expanded={isOpen}
        aria-haspopup="true"
        className="flex w-44 items-center justify-between gap-2 rounded-md border border-line bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-brand focus:ring-2 focus:ring-brand/20"
      >
        <span className="truncate">{summarise(selected)}</span>
        <svg
          aria-hidden="true"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          className={`size-3.5 shrink-0 text-ink-soft transition-transform ${isOpen ? 'rotate-180' : ''}`}
        >
          <path d="m6 9 6 6 6-6" />
        </svg>
      </button>

      {isOpen && (
        <div
          role="group"
          aria-label="Filter by status"
          className="absolute left-0 top-full z-10 mt-1 w-56 rounded-md border border-line bg-surface p-1.5 shadow-lg"
        >
          <div className="max-h-72 overflow-y-auto">
            {ALL_STATUSES.map((status) => (
              <label
                key={status}
                className="flex cursor-pointer items-center gap-2.5 rounded px-2 py-1.5 text-sm text-ink hover:bg-canvas"
              >
                <input
                  type="checkbox"
                  checked={selected.includes(status)}
                  onChange={() => toggle(status)}
                  className="size-4 rounded border-line text-brand focus:ring-brand/20"
                />
                {titleCase(status)}
              </label>
            ))}
          </div>

          {/* No "select all": ticking every box and ticking none produce the
              same result, and offering two routes to one state invites the
              question of why they look different. Clearing is the real action. */}
          <div className="mt-1.5 border-t border-line pt-1.5">
            <button
              type="button"
              onClick={() => onChange([])}
              disabled={selected.length === 0}
              className="w-full rounded px-2 py-1.5 text-left text-xs text-ink-soft transition hover:bg-canvas hover:text-brand disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-ink-soft"
            >
              Clear — show all statuses
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
