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
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export const api = createClient<paths>({
  baseUrl: API_BASE_URL,
  // Send the session cookie. Without this the browser withholds it on every
  // cross-origin request - the dev server and the API are different origins -
  // and every call is anonymous no matter who signed in.
  credentials: 'include',
})

/**
 * Where the sign-in handshake starts.
 *
 * A plain link, never a fetch. The browser has to *navigate* to Google and back
 * for the cookie to be set on the API origin; a fetch would be blocked by CORS
 * at accounts.google.com and could not carry the redirect anyway.
 */
export const SIGN_IN_URL = `${API_BASE_URL}/oauth2/authorization/google`

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE'])

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((entry) => entry.startsWith(`${name}=`))
    ?.slice(name.length + 1)
}

/**
 * The token to echo on a write, or undefined if the server has not issued one.
 *
 * Exported for the one write that does not go through this client: /logout is
 * Spring Security's own endpoint, so it is absent from the OpenAPI document and
 * openapi-fetch has no route for it. A second copy of this lookup living over
 * there is how the two quietly drift apart.
 */
export function readCsrfToken(): string | undefined {
  return readCookie(CSRF_COOKIE)
}

export { CSRF_HEADER }

/**
 * Echoes the CSRF token back on every write.
 *
 * Spring writes the token into a cookie this script can read, and expects it
 * returned in a header. That asymmetry is the whole mechanism: a hostile page
 * can make the browser *send* our cookies, but it cannot read them, so it
 * cannot produce the header. Reads are exempt because they change nothing and
 * Spring does not ask.
 */
api.use({
  onRequest({ request }) {
    if (!SAFE_METHODS.has(request.method.toUpperCase())) {
      const token = readCookie(CSRF_COOKIE)
      if (token) request.headers.set(CSRF_HEADER, token)
    }
    return request
  },
})

export type Application = components['schemas']['ApplicationResponse']
export type ApplicationStatus = Application['status']
export type RemoteType = NonNullable<Application['remoteType']>
export type SalaryPeriod = NonNullable<Application['salaryPeriod']>
export type ApplicationEvent = components['schemas']['ApplicationEventResponse']
export type ApplicationEventType = ApplicationEvent['type']
export type Actor = ApplicationEvent['actor']
export type CreateApplicationBody = components['schemas']['CreateApplicationRequest']
export type UpdateApplicationBody = components['schemas']['UpdateApplicationRequest']

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
