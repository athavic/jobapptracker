import { useState } from 'react'
import { ErrorNotice } from '../../components/ErrorNotice'
import { ThemeToggle } from '../../components/ThemeToggle'
import { ApplicationsTable } from './ApplicationsTable'
import { CreateApplicationForm } from './CreateApplicationForm'
import { StatusFilter } from './StatusFilter'
import { useApplications, type ApplicationFilters } from './hooks'

export function ApplicationsPage() {
  const [filters, setFilters] = useState<ApplicationFilters>({ page: 0, size: 20 })
  const { data, isPending, isError, error, isFetching } = useApplications(filters)

  // Changing a filter has to reset the page, or you land on page 3 of a
  // one-page result set and see nothing.
  function updateFilter(patch: Partial<ApplicationFilters>) {
    setFilters((current) => ({ ...current, ...patch, page: 0 }))
  }

  return (
    <div className="mx-auto max-w-5xl px-6 py-10">
      <header className="mb-8 flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-ink">Applications</h1>
          <p className="mt-1 text-sm text-ink-soft">
            Everything you have applied for, and everything you are still thinking about.
          </p>
        </div>
        <ThemeToggle />
      </header>

      <div className="mb-8">
        <CreateApplicationForm />
      </div>

      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div>
          <span className="mb-1 block text-xs font-medium text-ink-soft">Status</span>
          <StatusFilter
            selected={filters.statuses ?? []}
            onChange={(statuses) => updateFilter({ statuses })}
          />
        </div>

        <div className="grow sm:grow-0">
          <label
            className="mb-1 block text-xs font-medium text-ink-soft"
            htmlFor="filter-company"
          >
            Company
          </label>
          <input
            id="filter-company"
            value={filters.company ?? ''}
            onChange={(e) => updateFilter({ company: e.target.value })}
            placeholder="Search…"
            className="w-full rounded-md border border-line bg-surface px-3 py-2 text-sm outline-none placeholder:text-muted focus:border-brand focus:ring-2 focus:ring-brand/20 sm:w-56"
          />
        </div>

        <label className="flex items-center gap-2 py-2 text-sm text-ink-soft">
          <input
            type="checkbox"
            checked={filters.includeArchived ?? false}
            onChange={(e) => updateFilter({ includeArchived: e.target.checked })}
            className="size-4 rounded border-line text-brand focus:ring-brand/20"
          />
          Include archived
        </label>

        {isFetching && !isPending && (
          <span className="py-2 text-xs text-ink-soft">Updating…</span>
        )}
      </div>

      {isPending && <p className="py-12 text-center text-sm text-ink-soft">Loading…</p>}

      {isError && <ErrorNotice error={error} />}

      {data && (
        <>
          <ApplicationsTable applications={data.content} />

          <div className="mt-4 flex items-center justify-between text-sm text-ink-soft">
            <span>
              {data.totalElements} application{data.totalElements === 1 ? '' : 's'}
              {data.totalPages > 1 && ` · page ${data.page + 1} of ${data.totalPages}`}
            </span>

            {data.totalPages > 1 && (
              <div className="flex gap-2">
                <button
                  type="button"
                  disabled={data.first}
                  onClick={() => setFilters((f) => ({ ...f, page: (f.page ?? 0) - 1 }))}
                  className="rounded-md border border-line px-3 py-1.5 transition hover:bg-surface disabled:cursor-not-allowed disabled:opacity-40"
                >
                  Previous
                </button>
                <button
                  type="button"
                  disabled={data.last}
                  onClick={() => setFilters((f) => ({ ...f, page: (f.page ?? 0) + 1 }))}
                  className="rounded-md border border-line px-3 py-1.5 transition hover:bg-surface disabled:cursor-not-allowed disabled:opacity-40"
                >
                  Next
                </button>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  )
}
