import { useState } from 'react'
import type { Application, ApplicationStatus } from '../../api/client'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { ErrorNotice } from '../../components/ErrorNotice'
import { RowMenu, RowMenuItem, RowMenuSeparator } from '../../components/RowMenu'
import { formatRelative, formatSalary, titleCase } from '../../lib/format'
import { ApplicationTimelineDialog } from './ApplicationTimeline'
import { EditApplicationDialog } from './EditApplicationDialog'
import { StatusBadge } from './StatusBadge'
import {
  useArchiveApplication,
  useChangeStatus,
  useDeleteApplication,
  useRestoreApplication,
} from './hooks'

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

      {/*
        Archived rows only appear when the filter asks for them, but once they
        do they have to be distinguishable - otherwise the only clue that a row
        is archived is that its menu says Restore rather than Archive.
      */}
      {application.archived && (
        <span className="ml-2 rounded-full bg-inert-soft px-2 py-0.5 align-[0.05em] text-[0.6875rem] font-medium text-inert-ink">
          Archived
        </span>
      )}

      {application.location && (
        <div className="text-xs text-ink-soft">{application.location}</div>
      )}
    </>
  )
}

export function ApplicationsTable({ applications }: { applications: Application[] }) {
  // The id rather than the application itself: after an edit the list refetches
  // and hands down fresh objects, and holding a copy here would leave the
  // dialog showing a stale status badge.
  const [editingId, setEditingId] = useState<number | null>(null)
  const editing = applications.find((application) => application.id === editingId) ?? null

  const [historyId, setHistoryId] = useState<number | null>(null)
  const history = applications.find((application) => application.id === historyId) ?? null

  const [deletingId, setDeletingId] = useState<number | null>(null)
  const deleting = applications.find((application) => application.id === deletingId) ?? null

  const archiveApplication = useArchiveApplication()
  const restoreApplication = useRestoreApplication()
  const deleteApplication = useDeleteApplication()

  /*
   * Archive and restore act straight from the menu, so they have no dialog to
   * put a failure in and report above the table instead. Delete has a dialog of
   * its own and reports there, next to the button that caused it.
   */
  const actionError = archiveApplication.error ?? restoreApplication.error

  function confirmDelete() {
    if (deletingId == null) return
    deleteApplication.mutate(deletingId, { onSuccess: () => setDeletingId(null) })
  }

  function cancelDelete() {
    setDeletingId(null)
    // Clears a failed attempt, so opening the dialog on another row does not
    // start with the previous row's error already on screen.
    deleteApplication.reset()
  }

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
    <>
      {actionError != null && (
        <div className="mb-4">
          <ErrorNotice error={actionError} />
        </div>
      )}

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
                    application.salaryPeriod,
                  )}
                </td>

                <td className="px-4 py-3 text-ink-soft">
                  {formatRelative(application.createdAt)}
                </td>

                {/*
                  Only the status control stays in the row. Everything else is a
                  once-per-application action - you archive a given row once,
                  ever - so none of it earns permanent width in a table that
                  already scrolls sideways on a laptop.
                */}
                <td className="px-4 py-3">
                  <div className="flex items-center justify-end gap-2">
                    <StatusControl application={application} />

                    <RowMenu label={`Actions for ${application.roleTitle}`}>
                      {(close) => (
                        <>
                          <RowMenuItem
                            onClick={() => {
                              setHistoryId(application.id)
                              close()
                            }}
                          >
                            History
                          </RowMenuItem>

                          <RowMenuItem
                            onClick={() => {
                              setEditingId(application.id)
                              close()
                            }}
                          >
                            Edit
                          </RowMenuItem>

                          {/*
                            Archive asks nothing before it acts: it is fully
                            reversible by the item that replaces it, and the row
                            leaving the default list is feedback enough. Friction
                            should match how hard something is to undo, not how
                            final it sounds.
                          */}
                          {application.archived ? (
                            <RowMenuItem
                              onClick={() => {
                                restoreApplication.mutate(application.id)
                                close()
                              }}
                            >
                              Restore
                            </RowMenuItem>
                          ) : (
                            <RowMenuItem
                              onClick={() => {
                                archiveApplication.mutate(application.id)
                                close()
                              }}
                            >
                              Archive
                            </RowMenuItem>
                          )}

                          <RowMenuSeparator />

                          <RowMenuItem
                            tone="danger"
                            onClick={() => {
                              setDeletingId(application.id)
                              close()
                            }}
                          >
                            Delete
                          </RowMenuItem>
                        </>
                      )}
                    </RowMenu>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {history && (
        <ApplicationTimelineDialog
          key={history.id}
          application={history}
          onClose={() => setHistoryId(null)}
        />
      )}

      {editing && (
        <EditApplicationDialog
          // Remounting on id change resets the form to the new row's values;
          // without it the dialog would keep the previous row's edits.
          key={editing.id}
          application={editing}
          onClose={() => setEditingId(null)}
        />
      )}

      {deleting && (
        <ConfirmDialog
          key={deleting.id}
          title="Delete this application?"
          confirmLabel="Delete"
          isPending={deleteApplication.isPending}
          error={deleteApplication.error}
          onConfirm={confirmDelete}
          onCancel={cancelDelete}
        >
          <p>
            <span className="font-medium text-ink">{deleting.roleTitle}</span> at{' '}
            <span className="font-medium text-ink">{deleting.company.name}</span> will be
            removed permanently, along with its history.
          </p>
          <p className="mt-2">
            Archive it instead if you only want it off the board — archived
            applications still count towards your stats.
          </p>
        </ConfirmDialog>
      )}
    </>
  )
}
