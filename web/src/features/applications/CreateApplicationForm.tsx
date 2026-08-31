import { useState, type FormEvent } from 'react'
import type { CreateApplicationBody, SalaryPeriod } from '../../api/client'
import { ErrorNotice } from '../../components/ErrorNotice'
import { CURRENCY_OPTIONS } from '../../lib/currencies'
import { useCreateApplication } from './hooks'

type SalaryMode = 'NONE' | 'FIXED' | 'RANGE'

interface FormState {
  companyName: string
  roleTitle: string
  status: NonNullable<CreateApplicationBody['status']>
  location: string
  jobUrl: string
  source: string
  salaryMode: SalaryMode
  salaryAmount: string
  salaryMin: string
  salaryMax: string
  currency: string
  salaryPeriod: SalaryPeriod
}

const EMPTY: FormState = {
  companyName: '',
  roleTitle: '',
  status: 'SAVED',
  location: '',
  jobUrl: '',
  source: '',
  salaryMode: 'NONE',
  salaryAmount: '',
  salaryMin: '',
  salaryMax: '',
  currency: 'USD',
  salaryPeriod: 'ANNUAL',
}

const inputClass =
  'w-full rounded-md border border-line bg-surface px-3 py-2 text-sm outline-none placeholder:text-slate-400 focus:border-brand focus:ring-2 focus:ring-brand/20'

const labelClass = 'block text-xs font-medium text-ink-soft mb-1'

const toNumber = (value: string) => (value.trim() === '' ? undefined : Number(value))

export function CreateApplicationForm() {
  const [form, setForm] = useState<FormState>(EMPTY)
  const [isExpanded, setIsExpanded] = useState(true)
  const createApplication = useCreateApplication()
  const hasSalary = form.salaryMode !== 'NONE'
  const amountStep = form.salaryPeriod === 'HOURLY' ? '0.01' : '1'

  function set<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((current) => ({ ...current, [key]: value }))
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()

    const fixedAmount = toNumber(form.salaryAmount)
    const body: CreateApplicationBody = {
      companyName: form.companyName,
      roleTitle: form.roleTitle,
      status: form.status,
      location: form.location || undefined,
      jobUrl: form.jobUrl || undefined,
      source: form.source || undefined,
      salaryMin:
        !hasSalary
          ? undefined
          : form.salaryMode === 'FIXED'
            ? fixedAmount
            : toNumber(form.salaryMin),
      salaryMax:
        !hasSalary
          ? undefined
          : form.salaryMode === 'FIXED'
            ? fixedAmount
            : toNumber(form.salaryMax),
      currency: hasSalary ? form.currency : undefined,
      salaryPeriod: hasSalary ? form.salaryPeriod : undefined,
    }

    createApplication.mutate(body, { onSuccess: () => setForm(EMPTY) })
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="rounded-lg border border-line bg-surface p-5 shadow-sm"
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-sm font-semibold text-ink">Track a new application</h2>
          {isExpanded && (
            <p className="mt-1 text-xs text-ink-soft">
              The company is created automatically if it does not exist yet.
            </p>
          )}
        </div>

        <button
          type="button"
          onClick={() => setIsExpanded((current) => !current)}
          aria-expanded={isExpanded}
          aria-controls="create-application-fields"
          aria-label={isExpanded ? 'Hide add application form' : 'Show add application form'}
          className="shrink-0 rounded-md border border-line p-2 text-ink-soft transition hover:border-brand hover:text-brand focus:outline-none focus:ring-2 focus:ring-brand/20"
        >
          <svg
            aria-hidden="true"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            className={`size-4 transition-transform ${isExpanded ? '' : 'rotate-180'}`}
          >
            <path d="m18 15-6-6-6 6" />
          </svg>
        </button>
      </div>

      {isExpanded && (
        <div id="create-application-fields">
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
        <div>
          <label className={labelClass} htmlFor="companyName">
            Company
          </label>
          <input
            id="companyName"
            className={inputClass}
            value={form.companyName}
            onChange={(e) => set('companyName', e.target.value)}
            placeholder="Stripe"
            required
          />
        </div>

        <div>
          <label className={labelClass} htmlFor="roleTitle">
            Role
          </label>
          <input
            id="roleTitle"
            className={inputClass}
            value={form.roleTitle}
            onChange={(e) => set('roleTitle', e.target.value)}
            placeholder="Backend Engineer"
            required
          />
        </div>

        <div>
          <label className={labelClass} htmlFor="status">
            Status
          </label>
          <select
            id="status"
            className={inputClass}
            value={form.status}
            onChange={(e) => set('status', e.target.value as FormState['status'])}
          >
            <option value="DISCOVERED">Discovered</option>
            <option value="SAVED">Saved</option>
            <option value="APPLIED">Applied</option>
          </select>
        </div>

        <div>
          <label className={labelClass} htmlFor="location">
            Location
          </label>
          <input
            id="location"
            className={inputClass}
            value={form.location}
            onChange={(e) => set('location', e.target.value)}
            placeholder="Remote"
          />
        </div>

        <div className="sm:col-span-2">
          <label className={labelClass} htmlFor="jobUrl">
            Job posting URL
          </label>
          <input
            id="jobUrl"
            type="url"
            className={inputClass}
            value={form.jobUrl}
            onChange={(e) => set('jobUrl', e.target.value)}
            placeholder="https://..."
          />
        </div>

        <fieldset className="grid gap-4 border-t border-line pt-4 sm:col-span-2 sm:grid-cols-2">
          <legend className="px-1 text-xs font-medium text-ink-soft">Compensation</legend>

          <div>
            <label className={labelClass} htmlFor="salary-mode">
              Salary format
            </label>
            <select
              id="salary-mode"
              className={inputClass}
              value={form.salaryMode}
              onChange={(e) => set('salaryMode', e.target.value as SalaryMode)}
            >
              <option value="NONE">Not listed</option>
              <option value="FIXED">Fixed amount</option>
              <option value="RANGE">Salary range</option>
            </select>
          </div>

          {hasSalary && (
            <div>
              <label className={labelClass} htmlFor="salary-period">
                Pay period
              </label>
              <select
                id="salary-period"
                className={inputClass}
                value={form.salaryPeriod}
                onChange={(e) => set('salaryPeriod', e.target.value as SalaryPeriod)}
              >
                <option value="ANNUAL">Annual</option>
                <option value="HOURLY">Hourly</option>
              </select>
            </div>
          )}

          {form.salaryMode === 'FIXED' && (
            <div>
              <label className={labelClass} htmlFor="salary-amount">
                Amount
              </label>
              <input
                id="salary-amount"
                type="number"
                min="0"
                step={amountStep}
                className={inputClass}
                value={form.salaryAmount}
                onChange={(e) => set('salaryAmount', e.target.value)}
                placeholder={form.salaryPeriod === 'HOURLY' ? '27.50' : '120000'}
                required
              />
            </div>
          )}

          {form.salaryMode === 'RANGE' && (
            <>
              <div>
                <label className={labelClass} htmlFor="salary-min">
                  Minimum
                </label>
                <input
                  id="salary-min"
                  type="number"
                  min="0"
                  step={amountStep}
                  className={inputClass}
                  value={form.salaryMin}
                  onChange={(e) => set('salaryMin', e.target.value)}
                  placeholder={form.salaryPeriod === 'HOURLY' ? '25.00' : '120000'}
                  required
                />
              </div>
              <div>
                <label className={labelClass} htmlFor="salary-max">
                  Maximum
                </label>
                <input
                  id="salary-max"
                  type="number"
                  min="0"
                  step={amountStep}
                  className={inputClass}
                  value={form.salaryMax}
                  onChange={(e) => set('salaryMax', e.target.value)}
                  placeholder={form.salaryPeriod === 'HOURLY' ? '35.00' : '160000'}
                  required
                />
              </div>
            </>
          )}

          {hasSalary && (
            <div>
              <label className={labelClass} htmlFor="salary-currency">
                Currency
              </label>
              <select
                id="salary-currency"
                className={inputClass}
                value={form.currency}
                onChange={(e) => set('currency', e.target.value)}
              >
                {CURRENCY_OPTIONS.map(([code, name]) => (
                  <option key={code} value={code}>
                    {code} — {name}
                  </option>
                ))}
              </select>
            </div>
          )}
        </fieldset>
          </div>

          {createApplication.isError && (
            <div className="mt-4">
              <ErrorNotice error={createApplication.error} />
            </div>
          )}

          <div className="mt-4 flex items-center gap-3">
            <button
              type="submit"
              disabled={createApplication.isPending}
              className="rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {createApplication.isPending ? 'Saving…' : 'Add application'}
            </button>

            {createApplication.isSuccess && (
              <span className="text-xs text-emerald-700">Added.</span>
            )}
          </div>
        </div>
      )}
    </form>
  )
}
