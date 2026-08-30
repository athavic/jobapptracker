import type { Application, ApplicationStatus } from '../../api/client'
import { formatRelative, formatSalary, titleCase } from '../../lib/format'
import { StatusBadge } from './StatusBadge'
import { useChangeStatus } from './hooks'

/**
 * The status control renders straight from `allowedNextStatuses`, which the API
 * computes from the same enum that enforces the rule server-side. The UI never
 * has its own copy of the lifecycle, so the two cannot drift.
 */
function StatusControl({ application }: { application: Application }) {
  const changeStatus = useChangeStatus()
  const moves = application.allowedNextStatuses

  if (moves.length === 0) {
    return <span className="text-xs text-ink-soft">No moves left</span>
  }

  return (
    <select
      aria-label={`Change status for ${application.roleTitle}`}
      disabled={changeStatus.isPending}
      value=""
      onChange={(e) =>
        changeStatus.mutate({
          id: application.id,
          status: e.target.value as ApplicationStatus,
        })
      }
      className="rounded-md border border-line bg-surface px-2 py-1 text-xs text-ink-soft outline-none focus:border-brand focus:ring-2 focus:ring-brand/20 disabled:opacity-50"
    >
      <option value="" disabled>
        Move to…
      </option>
      {moves.map((status) => (
        <option key={status} value={status}>
          {titleCase(status)}
        </option>
      ))}
    </select>
  )
}

/**
 * The role title doubles as the link to the posting, so the table does not need
 * a column for a URL that would never fit in one.
 *
 * jobUrl is optional, so the non-link branch renders a <span> rather than a
 * <div> - swapping between a block and an inline element would shift rows that
 * have a link against rows that do not.
 */
function RoleCell({ application }: { application: Application }) {
  return (
    <>
      {application.jobUrl ? (
        <a
          href={application.jobUrl}
          target="_blank"
          // Without noopener the posting gets a live handle back to this tab
          // through window.opener and can navigate it somewhere else.
          rel="noopener noreferrer"
          className="font-medium text-ink underline-offset-2 hover:text-brand hover:underline"
        >
          {application.roleTitle}
          {/* An SVG rather than the ↗ character: Windows renders U+2197 as a
              colour emoji by default, which ignores the surrounding text
              colour. Decorative, so hidden from screen readers... */}
          <svg
            aria-hidden="true"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.5"
            strokeLinecap="round"
            strokeLinejoin="round"
            className="ml-1 inline-block size-3 align-[-0.1em] opacity-60"
          >
            <path d="M7 17 17 7" />
            <path d="M9 7h8v8" />
          </svg>
          {/* ...so the same warning is given here instead. Opening a new tab
              unannounced is disorienting, and both audiences deserve the hint. */}
          <span className="sr-only"> (opens the job posting in a new tab)</span>
        </a>
      ) : (
        <span className="font-medium text-ink">{application.roleTitle}</span>
      )}

      {application.location && (
        <div className="text-xs text-ink-soft">{application.location}</div>
      )}
    </>
  )
}

export function ApplicationsTable({ applications }: { applications: Application[] }) {
  if (applications.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-line bg-surface px-6 py-12 text-center">
        <p className="text-sm font-medium text-ink">Nothing here yet</p>
        <p className="mt-1 text-sm text-ink-soft">
          Add an application above, or clear the filters.
        </p>
      </div>
    )
  }

  return (
    <div className="overflow-x-auto rounded-lg border border-line bg-surface">
      <table className="w-full min-w-[46rem] text-sm">
        <thead>
          <tr className="border-b border-line text-left text-xs uppercase tracking-wide text-ink-soft">
            <th className="px-4 py-3 font-medium">Role</th>
            <th className="px-4 py-3 font-medium">Company</th>
            <th className="px-4 py-3 font-medium">Status</th>
            <th className="px-4 py-3 font-medium">Salary</th>
            <th className="px-4 py-3 font-medium">Added</th>
            <th className="px-4 py-3 font-medium">
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {applications.map((application) => (
            <tr
              key={application.id}
              className="border-b border-line last:border-0 hover:bg-canvas"
            >
              <td className="px-4 py-3">
                <RoleCell application={application} />
              </td>

              <td className="px-4 py-3 text-ink-soft">{application.company.name}</td>

              <td className="px-4 py-3">
                <StatusBadge status={application.status} />
              </td>

              <td className="px-4 py-3 tabular-nums text-ink-soft">
                {formatSalary(
                  application.salaryMin,
                  application.salaryMax,
                  application.currency,
                )}
              </td>

              <td className="px-4 py-3 text-ink-soft">
                {formatRelative(application.createdAt)}
              </td>

              <td className="px-4 py-3 text-right">
                <StatusControl application={application} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
