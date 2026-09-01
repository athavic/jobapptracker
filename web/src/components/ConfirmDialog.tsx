import { useEffect, useRef, type ReactNode } from 'react'
import { ErrorNotice } from './ErrorNotice'

/**
 * A modal that asks before doing something that cannot be undone.
 *
 * Deliberately not `window.confirm`: that blocks the JS thread, cannot be
 * styled, and renders a bright system box in the middle of a dark page - the
 * clearest possible tell that a dark theme was skin-deep.
 *
 * The dialog stays mounted while the action runs so a failure has somewhere to
 * be shown. Closing on submit and hoping would leave a 404 invisible.
 */
export function ConfirmDialog({
  title,
  confirmLabel,
  tone = 'danger',
  isPending = false,
  error,
  onConfirm,
  onCancel,
  children,
}: {
  title: string
  confirmLabel: string
  tone?: 'danger' | 'brand'
  isPending?: boolean
  error?: unknown
  onConfirm: () => void
  onCancel: () => void
  children: ReactNode
}) {
  const dialogRef = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = dialogRef.current
    // <dialog> only becomes modal - focus trap, backdrop, inertness behind it -
    // when opened through showModal(), never by rendering the open attribute.
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  /**
   * Escape is handled here rather than left to the browser, for the same reason
   * it is in EditApplicationDialog: the native close event does not reach React,
   * so letting the browser close the dialog would hide the element while leaving
   * this component mounted - and the next open would render an already-closed
   * dialog that never appears.
   */
  function handleKeyDown(event: React.KeyboardEvent<HTMLDialogElement>) {
    if (event.key === 'Escape') {
      event.preventDefault()
      if (!isPending) onCancel()
    }
  }

  /** A click that lands on the dialog itself, rather than the panel, is the backdrop. */
  function handleBackdropClick(event: React.MouseEvent<HTMLDialogElement>) {
    if (event.target === dialogRef.current && !isPending) onCancel()
  }

  return (
    <dialog
      ref={dialogRef}
      onKeyDown={handleKeyDown}
      onClick={handleBackdropClick}
      aria-labelledby="confirm-dialog-title"
      className="m-auto w-full max-w-md rounded-lg border border-line bg-surface p-0 text-ink"
    >
      <div className="p-6">
        <h2 id="confirm-dialog-title" className="text-base font-semibold">
          {title}
        </h2>

        <div className="mt-2 text-sm text-ink-soft">{children}</div>

        {error != null && (
          <div className="mt-4">
            <ErrorNotice error={error} />
          </div>
        )}

        <div className="mt-6 flex justify-end gap-3">
          {/*
            Cancel takes focus, not the destructive button. <dialog> focuses the
            first focusable child by default, and a confirm dialog where Enter
            deletes the thing is a trap - the safe option should be the one
            already under your finger.
          */}
          <button
            type="button"
            autoFocus
            onClick={onCancel}
            disabled={isPending}
            className="rounded-md border border-line px-4 py-2 text-sm transition hover:bg-canvas disabled:cursor-not-allowed disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={isPending}
            className={`rounded-md px-4 py-2 text-sm font-medium text-on-solid transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50 ${
              tone === 'danger' ? 'bg-danger' : 'bg-brand'
            }`}
          >
            {isPending ? 'Working…' : confirmLabel}
          </button>
        </div>
      </div>
    </dialog>
  )
}
