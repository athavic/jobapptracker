export type Theme = 'light' | 'dark'

/**
 * Also hardcoded in the pre-paint script in index.html, which cannot import
 * this module and still run before the first paint. Change both together.
 */
const STORAGE_KEY = 'jobtracker-theme'

const DARK_QUERY = '(prefers-color-scheme: dark)'

/**
 * The theme the user explicitly picked, or null if they never have.
 *
 * Null is a real state, not a missing value: it means "follow the OS", which is
 * why the toggle writes storage only when it is used. Defaulting the stored
 * value to 'light' would silently opt everyone out of their system preference.
 */
export function storedTheme(): Theme | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return value === 'light' || value === 'dark' ? value : null
  } catch {
    // Storage can throw outright in private browsing, not just return null.
    return null
  }
}

export function systemTheme(): Theme {
  return window.matchMedia(DARK_QUERY).matches ? 'dark' : 'light'
}

/** What is actually on screen right now: the stored choice, else the OS. */
export function resolveTheme(): Theme {
  return storedTheme() ?? systemTheme()
}

export function applyTheme(theme: Theme): void {
  document.documentElement.dataset.theme = theme
}

export function storeTheme(theme: Theme): void {
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    // Losing the preference across reloads is survivable; crashing is not.
  }
}

/**
 * Calls back when the OS theme changes. Returns an unsubscribe function.
 *
 * Worth wiring up even though it fires rarely: most systems flip to dark on a
 * schedule, so an app left open in a tab overnight would otherwise sit in the
 * wrong theme until it was reloaded.
 */
export function watchSystemTheme(onChange: (theme: Theme) => void): () => void {
  const query = window.matchMedia(DARK_QUERY)
  const handler = (event: MediaQueryListEvent) => onChange(event.matches ? 'dark' : 'light')
  query.addEventListener('change', handler)
  return () => query.removeEventListener('change', handler)
}
