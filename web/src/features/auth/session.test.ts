import { afterEach, describe, expect, it, vi } from 'vitest'
import { endSession } from './session'

/**
 * The bug this file exists for: sign-out reported success no matter what the
 * server said, so the page reloaded, the session was still alive, and clicking
 * again was what actually worked.
 */
describe('endSession', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  function stubFetch(response: Response) {
    const fetch = vi.fn().mockResolvedValue(response)
    vi.stubGlobal('fetch', fetch)
    return fetch
  }

  it('reports failure when the server refuses', async () => {
    // A refused sign-out is the whole point. 403 was what the browser really
    // got, back when no CSRF token had been issued yet.
    stubFetch(new Response(null, { status: 403 }))

    await expect(endSession('a-token')).resolves.toBe(false)
  })

  it('reports success when the server ends the session', async () => {
    stubFetch(new Response(null, { status: 204 }))

    await expect(endSession('a-token')).resolves.toBe(true)
  })

  it('sends the CSRF token and the session cookie', async () => {
    const fetch = stubFetch(new Response(null, { status: 204 }))

    await endSession('a-token')

    const [, init] = fetch.mock.calls[0]
    expect(init.headers).toEqual({ 'X-XSRF-TOKEN': 'a-token' })
    // Without this the browser withholds the session cookie cross-origin and
    // the request logs nobody out, quietly.
    expect(init.credentials).toBe('include')
  })

  it('still asks when there is no token, rather than assuming failure', async () => {
    // The server is the authority on whether a token was needed. Guessing here
    // would mean a UI that refuses to sign out on its own initiative.
    const fetch = stubFetch(new Response(null, { status: 204 }))

    await expect(endSession(undefined)).resolves.toBe(true)
    expect(fetch.mock.calls[0][1].headers).toEqual({})
  })
})
