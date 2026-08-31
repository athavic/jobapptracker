import { useEffect, useState } from 'react'
import {
  applyTheme,
  resolveTheme,
  storeTheme,
  storedTheme,
  watchSystemTheme,
  type Theme,
} from '../lib/theme'

/**
 * Flips between light and dark, and persists the choice.
 *
 * State is initialised from `resolveTheme()` rather than a constant, because
 * the inline script in index.html has already applied a theme by the time React
 * mounts. Starting from 'light' here would make the button's first click a
 * no-op for anyone on a dark OS - the classic hydration-style mismatch, in an
 * app that does not even hydrate.
 */
export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>(resolveTheme)

  // Only follow the OS while the user has made no choice of their own. Once
  // they have, an OS change must not silently overrule them.
  useEffect(() => {
    return watchSystemTheme((next) => {
      if (storedTheme() !== null) return
      setTheme(next)
      applyTheme(next)
    })
  }, [])

  function toggle() {
    const next: Theme = theme === 'dark' ? 'light' : 'dark'
    setTheme(next)
    applyTheme(next)
    storeTheme(next)
  }

  const goingDark = theme === 'light'

  return (
    <button
      type="button"
      onClick={toggle}
      // The label names what the click will DO, not what the theme currently
      // is. A screen reader user gets no help from being told the colour of a
      // page they cannot see.
      aria-label={goingDark ? 'Switch to dark theme' : 'Switch to light theme'}
      title={goingDark ? 'Switch to dark theme' : 'Switch to light theme'}
      className="inline-flex size-9 shrink-0 items-center justify-center rounded-md border border-line bg-surface text-ink-soft transition hover:border-brand hover:text-brand focus:ring-2 focus:ring-brand/20 focus:outline-none"
    >
      {goingDark ? <MoonIcon /> : <SunIcon />}
    </button>
  )
}

/* aria-hidden on both: the button already carries the label. */

function SunIcon() {
  return (
    <svg
      className="size-4"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </svg>
  )
}

function MoonIcon() {
  return (
    <svg
      className="size-4"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
    </svg>
  )
}
