import { afterEach, describe, expect, it, vi } from 'vitest'
import { formatRelative, formatSalary, titleCase } from './format'

/*
 * A note on what is NOT asserted here.
 *
 * formatDate, formatDateTime and the currency amounts inside formatSalary all
 * go through Intl with an undefined locale - deliberately, so each viewer sees
 * their own conventions. That means the exact output depends on the machine
 * running the test: "$120,000" here, "120.000 $" on a European CI runner. A
 * test asserting the exact string would pass locally and fail in CI for a
 * reason that has nothing to do with the code.
 *
 * So these assert the parts this module actually decides - the suffix, the
 * separator, the em dash for "nothing recorded", the branch taken - and leave
 * the number formatting to Intl, which is not ours to test.
 */

describe('titleCase', () => {
  it('renders a screaming enum value as a word', () => {
    expect(titleCase('APPLIED')).toBe('Applied')
  })

  it('leaves an already-cased word alone', () => {
    expect(titleCase('Applied')).toBe('Applied')
  })
})

describe('formatRelative', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  function at(now: string) {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(now))
  }

  it('says today rather than "0 days ago"', () => {
    at('2026-09-02T12:00:00Z')
    expect(formatRelative('2026-09-02T09:00:00Z')).toBe('today')
  })

  it('says yesterday rather than "1 days ago"', () => {
    at('2026-09-02T12:00:00Z')
    expect(formatRelative('2026-09-01T12:00:00Z')).toBe('yesterday')
  })

  it('counts days up to the month boundary', () => {
    at('2026-09-02T12:00:00Z')
    expect(formatRelative('2026-08-28T12:00:00Z')).toBe('5 days ago')
  })

  it('switches to months at 30 days, singular', () => {
    at('2026-09-02T12:00:00Z')
    expect(formatRelative('2026-08-03T12:00:00Z')).toBe('a month ago')
  })

  it('pluralises beyond one month', () => {
    at('2026-09-02T12:00:00Z')
    expect(formatRelative('2026-06-04T12:00:00Z')).toBe('3 months ago')
  })

  it('renders a missing timestamp as an em dash, not "Invalid Date"', () => {
    expect(formatRelative(undefined)).toBe('—')
  })
})

describe('formatSalary', () => {
  it('renders an em dash when nothing was recorded', () => {
    expect(formatSalary(undefined, undefined, undefined, undefined)).toBe('—')
  })

  it('renders a range with both ends', () => {
    const result = formatSalary(120000, 160000, 'USD', 'ANNUAL')

    expect(result).toContain('–')
    expect(result).toContain('/year')
  })

  it('collapses a range whose ends are equal to one amount', () => {
    // The table should say "$120,000/year", not "$120,000 – $120,000/year".
    expect(formatSalary(120000, 120000, 'USD', 'ANNUAL')).not.toContain('–')
  })

  it('renders a one-sided salary from whichever bound exists', () => {
    expect(formatSalary(undefined, 200000, 'USD', 'ANNUAL')).not.toBe('—')
    expect(formatSalary(90000, undefined, 'USD', 'ANNUAL')).not.toBe('—')
  })

  it('labels the period, since 27.50 and 120000 are not the same kind of number', () => {
    expect(formatSalary(27.5, undefined, 'USD', 'HOURLY')).toContain('/hour')
    expect(formatSalary(120000, undefined, 'USD', 'ANNUAL')).toContain('/year')
  })

  it('falls back to a valid currency rather than throwing on an absent one', () => {
    // Intl.NumberFormat throws a RangeError on an empty currency code, which
    // would take the whole table down over one row with no currency set.
    expect(() => formatSalary(120000, undefined, undefined, 'ANNUAL')).not.toThrow()
  })
})
