import { ApiError } from '../api/client'

/**
 * Surfaces what the API actually said.
 *
 * The backend returns RFC 9457 problem details, so an illegal status transition
 * arrives with a message naming the moves that ARE legal. Showing that beats
 * "something went wrong".
 */
export function ErrorNotice({ error }: { error: unknown }) {
  if (!error) return null

  const isApiError = error instanceof ApiError
  const title = isApiError ? (error.problem.title ?? 'Request failed') : 'Request failed'
  const detail = error instanceof Error ? error.message : String(error)
  const fieldErrors = isApiError ? error.fieldErrors : {}

  return (
    <div
      role="alert"
      className="rounded-md border border-danger-line bg-danger-soft px-4 py-3 text-sm text-danger-ink"
    >
      <p className="font-medium">{title}</p>
      <p className="mt-0.5 text-danger-ink">{detail}</p>

      {Object.keys(fieldErrors).length > 0 && (
        <ul className="mt-2 list-inside list-disc space-y-0.5 text-danger-ink">
          {Object.entries(fieldErrors).map(([field, message]) => (
            <li key={field}>
              <span className="font-mono text-xs">{field}</span> — {message}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
