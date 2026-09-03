import { useState, type ReactNode } from 'react'
import { readCsrfToken, SIGN_IN_URL } from '../../api/client'
import { ErrorNotice } from '../../components/ErrorNotice'
import { endSession } from './session'
import { isSignedOut, useSession, type Session } from './useSession'

/**
 * Nothing renders until we know who is asking.
 *
 * Deliberately a gate rather than a redirect-on-401 interceptor. Every page in
 * this app needs a workspace, so rendering the board and then swapping it for a
 * sign-in screen once the first request fails would mean a visible flash of an
 * empty state belonging to nobody. Asking once, up front, costs one request.
 */
export function SignInGate({ children }: { children: ReactNode }) {
  const { data: session, isPending, error } = useSession()

  if (isPending) {
    // No spinner. The check usually resolves in a few milliseconds, and a
    // spinner that appears and vanishes reads as a glitch rather than progress.
    return null
  }

  if (isSignedOut(error)) {
    return <SignInScreen />
  }

  if (error || !session) {
    // A real failure - the API is down, or something broke - and saying so
    // beats offering a sign-in button that would not work either.
    return (
      <div className="mx-auto max-w-lg px-6 py-16">
        <ErrorNotice error={error} />
      </div>
    )
  }

  return (
    <>
      <SessionBar session={session} />
      {children}
    </>
  )
}

function SignInScreen() {
  return (
    <div className="mx-auto flex min-h-screen max-w-lg flex-col justify-center px-6">
      <div className="rounded-xl border border-line bg-surface p-8 shadow-sm">
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Job Tracker</h1>
        <p className="mt-2 text-sm text-ink-soft">
          Sign in to see your applications. Everything you track stays in your workspace.
        </p>

        {/*
          An anchor, not a button with an onClick. The browser has to navigate
          to Google and back for the session cookie to be set on the API origin;
          a fetch would be blocked by CORS and could not follow the redirect.
        */}
        <a
          href={SIGN_IN_URL}
          className="mt-6 inline-flex items-center gap-2 rounded-lg bg-ink px-4 py-2 text-sm font-medium text-surface transition hover:opacity-90"
        >
          Continue with Google
        </a>
      </div>
    </div>
  )
}

function SessionBar({ session }: { session: Session }) {
  const [failed, setFailed] = useState(false)

  async function signOut() {
    setFailed(false)

    if (!(await endSession(readCsrfToken()))) {
      // Reloading regardless is what hid the CSRF bug for so long: a refused
      // sign-out looked like a page that blinked and stayed put, so the
      // obvious response was to click again - which worked, because the
      // refusal itself was what handed out the missing token.
      setFailed(true)
      return
    }

    // A full page load rather than clearing the query cache, because signing
    // out has to leave nothing behind: every cached application, filter and
    // form draft belongs to the person who is leaving.
    window.location.reload()
  }

  return (
    <div className="border-b border-line bg-surface">
      <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-6 py-2">
        <span className="text-xs text-ink-soft">
          Signed in as {session.displayName || session.email}
        </span>
        <div className="flex items-center gap-3">
          {failed && (
            <span role="alert" className="text-xs text-danger-ink">
              Could not sign out. Try again.
            </span>
          )}
          <button
            type="button"
            onClick={signOut}
            className="text-xs text-ink-soft underline underline-offset-2 transition hover:text-ink"
          >
            Sign out
          </button>
        </div>
      </div>
    </div>
  )
}
