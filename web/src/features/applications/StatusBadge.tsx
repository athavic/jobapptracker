import type { ApplicationStatus } from '../../api/client'
import { titleCase } from '../../lib/format'

/**
 * Status carries meaning, so it gets colour as well as text - active states in
 * the brand hue, dead ends in red, not-yet-applied in grey.
 */
const styles: Record<ApplicationStatus, string> = {
  DISCOVERED: 'bg-slate-100 text-slate-600 ring-slate-200',
  SAVED: 'bg-slate-100 text-slate-600 ring-slate-200',
  APPLIED: 'bg-brand-soft text-teal-800 ring-teal-200',
  SCREEN: 'bg-brand-soft text-teal-800 ring-teal-200',
  INTERVIEW: 'bg-amber-50 text-amber-800 ring-amber-200',
  OFFER: 'bg-emerald-50 text-emerald-800 ring-emerald-200',
  ACCEPTED: 'bg-emerald-600 text-white ring-emerald-600',
  REJECTED: 'bg-danger-soft text-red-800 ring-red-200',
  GHOSTED: 'bg-danger-soft text-red-800 ring-red-200',
  WITHDRAWN: 'bg-slate-200 text-slate-700 ring-slate-300',
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
