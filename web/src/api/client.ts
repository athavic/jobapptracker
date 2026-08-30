import createClient from 'openapi-fetch'
import type { components, paths } from './schema'

/**
 * One typed client for the whole app.
 *
 * `paths` comes from schema.d.ts, which is generated from the API's own OpenAPI
 * document. That means the URL strings, query params and request bodies below
 * are checked against the real Java controller at compile time - rename a field
 * in a DTO, run `npm run generate:api`, and tsc names every file that breaks.
 */
export const api = createClient<paths>({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
})

export type Application = components['schemas']['ApplicationResponse']
export type ApplicationStatus = Application['status']
export type RemoteType = NonNullable<Application['remoteType']>
export type CreateApplicationBody = components['schemas']['CreateApplicationRequest']

/** The error shape GlobalExceptionHandler returns for every failure. */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  /** Present only on validation failures, keyed by field name. */
  fieldErrors?: Record<string, string>
}

/**
 * A failed request, carrying the problem detail the API sent.
 *
 * Without this the UI can only say "something went wrong". With it, an illegal
 * status transition can show the API's own explanation of which moves are legal.
 */
export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }

  /** Field-level messages from @Valid, if this was a validation failure. */
  get fieldErrors(): Record<string, string> {
    return this.problem.fieldErrors ?? {}
  }
}

/**
 * openapi-fetch resolves to `{ data, error, response }` rather than throwing.
 * TanStack Query needs a thrown error to mark a query failed, so this bridges
 * the two - and keeps the response type openapi-fetch inferred, so callers
 * still get the real DTO type rather than `unknown`.
 */
export async function unwrap<T>(
  call: Promise<{ data?: T; error?: unknown; response: Response }>,
): Promise<T> {
  const result = await call

  if (!result.response.ok) {
    const problem = (result.error ?? {}) as ProblemDetail
    throw new ApiError(result.response.status, problem)
  }

  return result.data as T
}
