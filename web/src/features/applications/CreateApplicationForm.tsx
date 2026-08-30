import { useState, type FormEvent } from 'react'
import type { CreateApplicationBody } from '../../api/client'
import { ErrorNotice } from '../../components/ErrorNotice'
import { useCreateApplication } from './hooks'

const EMPTY: CreateApplicationBody = {
  companyName: '',
  roleTitle: '',
  status: 'SAVED',
  location: '',
  jobUrl: '',
  source: '',
}

const inputClass =
  'w-full rounded-md border border-line bg-surface px-3 py-2 text-sm outline-none placeholder:text-slate-400 focus:border-brand focus:ring-2 focus:ring-brand/20'

const labelClass = 'block text-xs font-medium text-ink-soft mb-1'

export function CreateApplicationForm() {
  const [form, setForm] = useState<CreateApplicationBody>(EMPTY)
  const createApplication = useCreateApplication()

  function set<K extends keyof CreateApplicationBody>(
    key: K,
    value: CreateApplicationBody[K],
  ) {
    setForm((current) => ({ ...current, [key]: value }))
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()

    createApplication.mutate(
      {
        ...form,
        // Send undefined rather than "" so the server stores NULL instead of
        // an empty string. Two ways to say "no value" is one too many.
        location: form.location || undefined,
        jobUrl: form.jobUrl || undefined,
        source: form.source || undefined,
        salaryMin: form.salaryMin || undefined,
        salaryMax: form.salaryMax || undefined,
      },
      { onSuccess: () => setForm(EMPTY) },
    )
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="rounded-lg border border-line bg-surface p-5 shadow-sm"
    >
      <h2 className="text-sm font-semibold text-ink">Track a new application</h2>
      <p className="mt-1 text-xs text-ink-soft">
        The company is created automatically if it does not exist yet.
      </p>

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
            onChange={(e) => set('status', e.target.value as CreateApplicationBody['status'])}
          >
            {/* Only the statuses a brand-new record can legitimately start in. */}
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
            value={form.location ?? ''}
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
            value={form.jobUrl ?? ''}
            onChange={(e) => set('jobUrl', e.target.value)}
            placeholder="https://..."
          />
        </div>
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
    </form>
  )
}
