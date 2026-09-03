import { useQuery } from '@tanstack/react-query'
import { api, ApiError, unwrap } from '../../api/client'
import type { components } from '../../api/schema'

export type Session = components['schemas']['MeResponse']

export const sessionQueryKey = ['session'] as const

/**
 * Who is signed in, according to the server.
 *
 * The browser cannot answer this itself. The session cookie is HttpOnly, so
 * JavaScript cannot see it, and even if it could, a cookie being present is not
 * the same as it still being valid. Asking the API is the only honest test.
 */
export function useSession() {
  return useQuery({
    queryKey: sessionQueryKey,
    queryFn: () => unwrap(api.GET('/api/v1/me', {})),

    // 401 is an answer, not a failure. Retrying it would delay the sign-in
    // screen by several seconds to re-learn something the first response
    // already said clearly.
    retry: (failureCount, error) =>
      !(error instanceof ApiError && error.status === 401) && failureCount < 2,

    // Not refetched on window focus: it changes only at sign-in and sign-out,
    // both of which reload the page or invalidate this key explicitly.
    staleTime: Infinity,
  })
}

/** True when the query failed specifically because nobody is signed in. */
export function isSignedOut(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401
}
