import { useEffect, useRef, useState, type FormEvent } from 'react'
import type {
  Application,
  ApplicationStatus,
  SalaryPeriod,
  UpdateApplicationBody,
} from '../../api/client'
import { ErrorNotice } from '../../components/ErrorNotice'
import { CURRENCY_OPTIONS } from '../../lib/currencies'
import { titleCase } from '../../lib/format'
import { StatusBadge } from './StatusBadge'
import { useChangeStatus, useUpdateApplication } from './hooks'

const inputClass =
  'w-full rounded-md border border-line bg-surface px-3 py-2 text-sm outline-none placeholder:text-muted focus:border-brand focus:ring-2 focus:ring-brand/20'

const labelClass = 'block text-xs font-medium text-ink-soft mb-1'

type SalaryMode = 'FIXED' | 'RANGE'

/** The editable fields, as strings, because that is what inputs deal in. */
interface FormState {
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
 */
function isSingleAmount(application: Application): boolean {
  const { salaryMin: min, salaryMax: max } = application

  if (min == null && max == null) return true
  return min != null && max != null && min === max
}

function toFormState(application: Application): FormState {
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

function toRequestBody(form: FormState): UpdateApplicationBody {
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

export function EditApplicationDialog({
  application,
  onClose,
}: {
  application: Application
  onClose: () => void
}) {
  const [form, setForm] = useState<FormState>(() => toFormState(application))
  const dialogRef = useRef<HTMLDialogElement>(null)

  const updateApplication = useUpdateApplication()
  const changeStatus = useChangeStatus()
  const currencyIsListed = CURRENCY_OPTIONS.some(([code]) => code === form.currency)
  const amountStep = form.salaryPeriod === 'HOURLY' ? '0.01' : '1'

  useEffect(() => {
    const dialog = dialogRef.current
    // <dialog> only becomes modal - focus trap, backdrop, inertness behind it -
    // when opened through showModal(), never by rendering the open attribute.
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  /**
   * Escape is handled here rather than left to the browser.
   *
   * The native close route is not usable from React: <dialog onClose> is not
   * wired through React's synthetic events, and the underlying close event does
   * not reach an addEventListener either. Letting the browser close the dialog
   * on its own would hide it while leaving this component mounted, so the next
   * Edit click would re-render an already-closed dialog and nothing would open.
   *
   * preventDefault stops the native close, and onClose unmounts us instead -
   * removing the element is what actually ends the modal.
   */
  function handleKeyDown(event: React.KeyboardEvent<HTMLDialogElement>) {
    if (event.key === 'Escape') {
      event.preventDefault()
      onClose()
    }
  }

  /** A click that lands on the dialog itself, rather than the form, is the backdrop. */
  function handleBackdropClick(event: React.MouseEvent<HTMLDialogElement>) {
    if (event.target === dialogRef.current) onClose()
  }

  function set<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((current) => ({ ...current, [key]: value }))
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    updateApplication.mutate(
      { id: application.id, body: toRequestBody(form) },
      { onSuccess: onClose },
    )
  }

  // The backdrop scrim is styled in index.css, not here: it is one of the few
  // colours that has to darken in both themes, so it cannot come from a single
  // token. `bg-ink/20` would be a white haze in dark mode.
  return (
    <dialog
      ref={dialogRef}
      onKeyDown={handleKeyDown}
      onClick={handleBackdropClick}
      className="m-auto w-full max-w-xl rounded-lg border border-line bg-surface p-0 text-ink"
    >
      <form onSubmit={handleSubmit} className="p-6">
        <h2 className="text-base font-semibold">Edit application</h2>
        <p className="mt-1 text-xs text-ink-soft">
          Changing the company to a name that does not exist yet will create it.
        </p>

        <div className="mt-5 grid gap-4 sm:grid-cols-2">
          <div>
            <label className={labelClass} htmlFor="edit-company">
              Company
            </label>
            <input
              id="edit-company"
              className={inputClass}
              value={form.companyName}
              onChange={(e) => set('companyName', e.target.value)}
              required
            />
          </div>

          <div>
            <label className={labelClass} htmlFor="edit-role">
              Role
            </label>
            <input
              id="edit-role"
              className={inputClass}
              value={form.roleTitle}
              onChange={(e) => set('roleTitle', e.target.value)}
              required
            />
          </div>

          <div>
            <label className={labelClass} htmlFor="edit-salary-mode">
              Salary format
            </label>
            <select
              id="edit-salary-mode"
              className={inputClass}
              value={form.salaryMode}
              onChange={(e) => set('salaryMode', e.target.value as SalaryMode)}
            >
              <option value="FIXED">Fixed amount</option>
              <option value="RANGE">Salary range</option>
            </select>
          </div>

          <div>
            <label className={labelClass} htmlFor="edit-salary-period">
              Pay period
            </label>
            <select
              id="edit-salary-period"
              className={inputClass}
              value={form.salaryPeriod}
              onChange={(e) => set('salaryPeriod', e.target.value as SalaryPeriod)}
            >
              <option value="ANNUAL">Annual</option>
              <option value="HOURLY">Hourly</option>
            </select>
          </div>

          {form.salaryMode === 'FIXED' ? (
            <div>
              <label className={labelClass} htmlFor="edit-salary-amount">
                Amount
              </label>
              <input
                id="edit-salary-amount"
                type="number"
                min="0"
                step={amountStep}
                className={inputClass}
                value={form.salaryAmount}
                onChange={(e) => set('salaryAmount', e.target.value)}
                placeholder={form.salaryPeriod === 'HOURLY' ? '27.50' : '120000'}
              />
            </div>
          ) : (
            <>
              <div>
                <label className={labelClass} htmlFor="edit-salary-min">
                  Minimum
                </label>
                <input
                  id="edit-salary-min"
                  type="number"
                  min="0"
                  step={amountStep}
                  className={inputClass}
                  value={form.salaryMin}
                  onChange={(e) => set('salaryMin', e.target.value)}
                  placeholder={form.salaryPeriod === 'HOURLY' ? '25.00' : '120000'}
                />
              </div>
              <div>
                <label className={labelClass} htmlFor="edit-salary-max">
                  Maximum
                </label>
                <input
                  id="edit-salary-max"
                  type="number"
                  min="0"
                  step={amountStep}
                  className={inputClass}
                  value={form.salaryMax}
                  onChange={(e) => set('salaryMax', e.target.value)}
                  placeholder={form.salaryPeriod === 'HOURLY' ? '35.00' : '160000'}
                />
              </div>
            </>
          )}

          <div>
            <label className={labelClass} htmlFor="edit-currency">
              Currency
            </label>
            <select
              id="edit-currency"
              className={inputClass}
              value={form.currency}
              onChange={(e) => set('currency', e.target.value)}
            >
              <option value="" disabled>
                Select currency
              </option>
              {!currencyIsListed && form.currency && (
                <option value={form.currency}>{form.currency} — Existing value</option>
              )}
              {CURRENCY_OPTIONS.map(([code, name]) => (
                <option key={code} value={code}>
                  {code} — {name}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className={labelClass} htmlFor="edit-location">
              Location
            </label>
            <input
              id="edit-location"
              className={inputClass}
              value={form.location}
              onChange={(e) => set('location', e.target.value)}
              placeholder="Remote"
            />
          </div>

          <div className="sm:col-span-2">
            <label className={labelClass} htmlFor="edit-url">
              Job posting URL
            </label>
            <input
              id="edit-url"
              type="url"
              className={inputClass}
              value={form.jobUrl}
              onChange={(e) => set('jobUrl', e.target.value)}
              placeholder="https://..."
            />
          </div>
        </div>

        {updateApplication.isError && (
          <div className="mt-4">
            <ErrorNotice error={updateApplication.error} />
          </div>
        )}

        {/*
          Status sits outside the Save button on purpose. It moves through its
          own endpoint so transitions stay validated, which means folding it
          into Save would make one button issue two writes - and leave a
          half-applied edit when the second one is rejected. Firing it
          immediately keeps each write independently succeeding or failing.
        */}
        <div className="mt-6 border-t border-line pt-5">
          <div className="flex flex-wrap items-center gap-3">
            <span className={labelClass + ' mb-0'}>Status</span>
            <StatusBadge status={application.status} />

            {application.allowedNextStatuses.length > 0 && (
              <select
                aria-label="Move to a different status"
                disabled={changeStatus.isPending}
                value=""
                onChange={(e) =>
                  changeStatus.mutate({
                    id: application.id,
                    status: e.target.value as ApplicationStatus,
                  })
                }
                className="rounded-md border border-line bg-surface px-2 py-1 text-xs text-ink-soft outline-none focus:border-brand focus:ring-2 focus:ring-brand/20 disabled:opacity-50"
              >
                <option value="" disabled>
                  Move to…
                </option>
                {application.allowedNextStatuses.map((status) => (
                  <option key={status} value={status}>
                    {titleCase(status)}
                  </option>
                ))}
              </select>
            )}
          </div>

          <p className="mt-2 text-xs text-ink-soft">
            Status changes save on their own, separately from the button below.
          </p>

          {changeStatus.isError && (
            <div className="mt-3">
              <ErrorNotice error={changeStatus.error} />
            </div>
          )}
        </div>

        <div className="mt-6 flex justify-end gap-3">
          <button
            type="button"
            onClick={onClose}
            className="rounded-md border border-line px-4 py-2 text-sm transition hover:bg-canvas"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={updateApplication.isPending}
            className="rounded-md bg-brand px-4 py-2 text-sm font-medium text-on-solid transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {updateApplication.isPending ? 'Saving…' : 'Save changes'}
          </button>
        </div>
      </form>
    </dialog>
  )
}
