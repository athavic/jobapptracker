/** Display helpers. Kept out of components so they stay testable and consistent. */

const dateFormat = new Intl.DateTimeFormat(undefined, {
  year: 'numeric',
  month: 'short',
  day: 'numeric',
})

/**
 * The API sends UTC instants. Intl renders them in the viewer's own timezone,
 * which is the whole reason the backend stores TIMESTAMPTZ and never a local time.
 */
export function formatDate(iso: string | undefined): string {
  if (!iso) return '—'
  return dateFormat.format(new Date(iso))
}

export function formatRelative(iso: string | undefined): string {
  if (!iso) return '—'
  const days = Math.round((Date.now() - new Date(iso).getTime()) / 86_400_000)
  if (days === 0) return 'today'
  if (days === 1) return 'yesterday'
  if (days < 30) return `${days} days ago`
  const months = Math.round(days / 30)
  return months === 1 ? 'a month ago' : `${months} months ago`
}

export function formatSalary(
  min: number | undefined,
  max: number | undefined,
  currency: string | undefined,
  period: 'ANNUAL' | 'HOURLY' | undefined,
): string {
  if (min == null && max == null) return '—'
  const suffix = period === 'HOURLY' ? '/hour' : '/year'
  const money = (n: number) =>
    new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency: currency || 'USD',
      minimumFractionDigits: 0,
      maximumFractionDigits: period === 'HOURLY' ? 2 : 0,
    }).format(n)

  if (min != null && max != null && min !== max) {
    return `${money(min)} – ${money(max)}${suffix}`
  }
  return `${money((min ?? max) as number)}${suffix}`
}

export function titleCase(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase()
}
