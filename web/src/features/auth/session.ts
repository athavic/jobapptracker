import { API_BASE_URL, CSRF_HEADER } from '../../api/client'

/**
 * Ends the session on the server, and reports whether it actually ended.
 *
 * <p>Split out of the component for two reasons. It is the only write in the app
 * that bypasses the typed client - /logout belongs to Spring Security, not to
 * our OpenAPI document - and it is the piece that was wrong: the caller used to
 * reload the page whether or not the server agreed, so a refused sign-out was
 * indistinguishable from a successful one.
 *
 * The token is a parameter rather than read in here, which is what lets this be
 * tested at all: the suite runs without a DOM, so a function that reaches for
 * document.cookie could only be exercised in a browser.
 */
export async function endSession(csrfToken: string | undefined): Promise<boolean> {
  const response = await fetch(`${API_BASE_URL}/logout`, {
    method: 'POST',
    credentials: 'include',
    headers: csrfToken ? { [CSRF_HEADER]: csrfToken } : {},
  })

  return response.ok
}
