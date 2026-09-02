/**
 * The edit dialog's pure logic: API shape in, form shape out, and back again.
 *
 * Extracted from EditApplicationDialog for two reasons. The first is testing -
 * these are the decisions worth asserting on, and reaching them through a
 * rendered component would mean simulating a <dialog> the test environment
 * does not really implement. The second is that they are not React: they take
 * a value and return a value, and nothing here needs to know a component
 * exists. `lib/format.ts` is organised the same way.
 */
import type { Application, SalaryPeriod, UpdateApplicationBody } from '../../api/client'

export type SalaryMode = 'FIXED' | 'RANGE'

/** The editable fields, as strings, because that is what inputs deal in. */
export interface FormState {
  companyName: string
  roleTitle: string
  location: string
  jobUrl: string
  salaryMode: SalaryMode
  salaryAmount: string
  salaryMin: string
  salaryMax: string
  currency: string
  salaryPeriod: SalaryPeriod
}

/**
 * FIXED means one number, and that is a claim about the data, not a fallback.
 *
 * Only two shapes are genuinely one number: both bounds recorded and equal, or
 * no salary recorded at all - where FIXED is simply the friendlier empty form,
 * one box rather than two. Everything else is a range, including the one-sided
 * kind: "up to 200k" with no minimum is a range with an open end.
 *
 * Treating one-sided as FIXED is what made this worth writing down. FIXED mode
 * writes its single amount into BOTH `salaryMin` and `salaryMax` on save, so
 * opening a one-sided application and pressing Save - changing nothing else -
 * silently converted "up to 200k" into "exactly 200k". It needed no unusual
 * input and gave no sign it had happened.
 *
 * @returns `true` if both salary bounds are absent or equal, `false` otherwise.
 */
export function isSingleAmount(application: Application): boolean {
  const { salaryMin: min, salaryMax: max } = application

  if (min == null && max == null) return true
  return min != null && max != null && min === max
}

/**
 * Converts application data into the string-based state used by the edit form.
 *
 * @param application - The application to convert
 * @returns The initialized form state
 */
export function toFormState(application: Application): FormState {
  // Every field is a string here. Passing undefined to an input's value turns a
  // controlled input into an uncontrolled one, and React then stops updating it.
  return {
    companyName: application.company.name,
    roleTitle: application.roleTitle,
    location: application.location ?? '',
    jobUrl: application.jobUrl ?? '',
    salaryMode: isSingleAmount(application) ? 'FIXED' : 'RANGE',
    salaryAmount: (application.salaryMin ?? application.salaryMax)?.toString() ?? '',
    salaryMin: application.salaryMin?.toString() ?? '',
    salaryMax: application.salaryMax?.toString() ?? '',
    currency: application.currency ?? '',
    salaryPeriod: application.salaryPeriod ?? 'ANNUAL',
  }
}

export function toRequestBody(form: FormState): UpdateApplicationBody {
  const number = (value: string) => (value.trim() === '' ? undefined : Number(value))
  const fixedAmount = number(form.salaryAmount)
  const hasSalary =
    form.salaryMode === 'FIXED'
      ? fixedAmount != null
      : number(form.salaryMin) != null || number(form.salaryMax) != null

  return {
    companyName: form.companyName.trim(),
    roleTitle: form.roleTitle.trim(),
    // Blank is sent through rather than dropped: the API turns "" into null,
    // which is how a field gets cleared.
    location: form.location,
    jobUrl: form.jobUrl,
    salaryMin: form.salaryMode === 'FIXED' ? fixedAmount : number(form.salaryMin),
    salaryMax: form.salaryMode === 'FIXED' ? fixedAmount : number(form.salaryMax),
    currency: form.currency.trim() === '' ? undefined : form.currency.trim(),
    salaryPeriod: hasSalary ? form.salaryPeriod : undefined,
  }
}
