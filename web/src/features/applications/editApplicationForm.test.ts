import { describe, expect, it } from 'vitest'
import type { Application } from '../../api/client'
import { isSingleAmount, toFormState, toRequestBody } from './editApplicationForm'

/**
 * An application with only the fields these functions read. The rest of
 * ApplicationResponse is required by the type but irrelevant here, so it is
 * filled in once and overridden per test.
 */
function application(overrides: Partial<Application> = {}): Application {
  return {
    id: 1,
    company: { id: 7, name: 'Stripe' },
    roleTitle: 'Backend Engineer',
    status: 'APPLIED',
    allowedNextStatuses: ['SCREEN'],
    priority: 3,
    archived: false,
    createdAt: '2026-08-30T10:00:00Z',
    updatedAt: '2026-08-30T10:00:00Z',
    ...overrides,
  }
}

/**
 * PATCH semantics, as the API implements them: a field the body omits is left
 * alone. Modelling that here is what makes the round trip below meaningful -
 * without it a test could "pass" by sending undefined for everything.
 */
function applyPatch(before: Application, body: ReturnType<typeof toRequestBody>) {
  return {
    salaryMin: body.salaryMin === undefined ? before.salaryMin : body.salaryMin,
    salaryMax: body.salaryMax === undefined ? before.salaryMax : body.salaryMax,
  }
}

describe('isSingleAmount', () => {
  it('is true when both bounds are recorded and equal', () => {
    expect(isSingleAmount(application({ salaryMin: 120000, salaryMax: 120000 }))).toBe(true)
  })

  it('is true when there is no salary at all, so the empty form has one box', () => {
    expect(isSingleAmount(application())).toBe(true)
  })

  it('is false for a genuine range', () => {
    expect(isSingleAmount(application({ salaryMin: 120000, salaryMax: 160000 }))).toBe(false)
  })

  /*
   * The two that regressed. A bound with no partner is a range with an open
   * end - "up to 200k" is not the same claim as "exactly 200k" - and calling
   * it a single amount is what let the round trip below rewrite the data.
   */
  it('is false when only a maximum is recorded', () => {
    expect(isSingleAmount(application({ salaryMax: 200000 }))).toBe(false)
  })

  it('is false when only a minimum is recorded', () => {
    expect(isSingleAmount(application({ salaryMin: 90000 }))).toBe(false)
  })
})

describe('opening the edit dialog and saving without changing anything', () => {
  /*
   * The regression test, and the reason this file exists.
   *
   * Every one of these shapes must survive the trip out to the form and back,
   * because a user who opens a row to fix a typo in the location and presses
   * Save has not said anything about the salary. Two of them did not survive:
   * a one-sided salary opened in FIXED mode, and FIXED mode writes its single
   * amount into both columns, so "up to 200k" was silently saved as "exactly
   * 200k" - no unusual input, no warning, no way to notice.
   */
  const shapes: ReadonlyArray<[string, Partial<Application>]> = [
    ['no salary recorded', {}],
    ['a fixed amount', { salaryMin: 120000, salaryMax: 120000 }],
    ['a range', { salaryMin: 120000, salaryMax: 160000 }],
    ['a maximum with no minimum', { salaryMax: 200000 }],
    ['a minimum with no maximum', { salaryMin: 90000 }],
  ]

  it.each(shapes)('leaves %s exactly as it was', (_name, salary) => {
    const before = application(salary)

    const after = applyPatch(before, toRequestBody(toFormState(before)))

    expect(after.salaryMin).toBe(before.salaryMin)
    expect(after.salaryMax).toBe(before.salaryMax)
  })
})

describe('toFormState', () => {
  it('turns every absent optional into an empty string, never undefined', () => {
    // Passing undefined to an input's value silently switches it from a
    // controlled to an uncontrolled input, and React then stops updating it.
    const form = toFormState(application())

    expect(form.location).toBe('')
    expect(form.jobUrl).toBe('')
    expect(form.currency).toBe('')
    expect(form.salaryAmount).toBe('')
  })

  it('defaults the pay period rather than leaving the select unset', () => {
    expect(toFormState(application()).salaryPeriod).toBe('ANNUAL')
  })
})

describe('toRequestBody', () => {
  const form = toFormState(application({ location: 'Remote', jobUrl: 'https://x.test' }))

  it('sends a blank string through, because that is how a field gets cleared', () => {
    // The API turns "" into null. Dropping the key instead would mean "leave
    // alone", and the field could never be emptied.
    const body = toRequestBody({ ...form, location: '' })

    expect(body.location).toBe('')
  })

  it('trims the company and role, so " Stripe " does not create a second company', () => {
    const body = toRequestBody({ ...form, companyName: '  Stripe  ', roleTitle: '  Dev  ' })

    expect(body.companyName).toBe('Stripe')
    expect(body.roleTitle).toBe('Dev')
  })

  it('omits the pay period when no salary was entered', () => {
    const body = toRequestBody({ ...form, salaryMode: 'FIXED', salaryAmount: '' })

    expect(body.salaryPeriod).toBeUndefined()
  })

  it('sends the pay period once there is an amount for it to describe', () => {
    const body = toRequestBody({ ...form, salaryMode: 'FIXED', salaryAmount: '120000' })

    expect(body.salaryPeriod).toBe('ANNUAL')
    expect(body.salaryMin).toBe(120000)
    expect(body.salaryMax).toBe(120000)
  })
})
