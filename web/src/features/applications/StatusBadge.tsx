import type { ApplicationStatus } from '../../api/client'
import { titleCase } from '../../lib/format'

/**
 * Status carries meaning, so it gets colour as well as text - active states in
 * the brand hue, dead ends in red, not-yet-applied in grey.
 */
const styles: Record<ApplicationStatus, string> = {
  DISCOVERED: 'bg-neutral-soft text-neutral-ink ring-neutral-line',
  SAVED: 'bg-neutral-soft text-neutral-ink ring-neutral-line',
  APPLIED: 'bg-brand-soft text-brand-ink ring-brand-line',
  SCREEN: 'bg-brand-soft text-brand-ink ring-brand-line',
  INTERVIEW: 'bg-warn-soft text-warn-ink ring-warn-line',
  OFFER: 'bg-positive-soft text-positive-ink ring-positive-line',
  ACCEPTED: 'bg-positive text-on-solid ring-positive',
  REJECTED: 'bg-danger-soft text-danger-ink ring-danger-line',
  GHOSTED: 'bg-danger-soft text-danger-ink ring-danger-line',
  WITHDRAWN: 'bg-inert-soft text-inert-ink ring-inert-line',
}

export function StatusBadge({ status }: { status: ApplicationStatus }) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${styles[status]}`}
    >
      {titleCase(status)}
    </span>
  )
}
