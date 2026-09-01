import { useEffect, useRef, useState, type ReactNode } from 'react'

/**
 * A `⋯` button and the menu it opens.
 *
 * Everything a row can do apart from changing its status lives in here. Those
 * actions all happen once per application - you archive a row once, ever - so
 * none of them earn permanent width in a table that already scrolls sideways.
 *
 * The menu is a `popover`, which matters for one specific reason: the table is
 * wrapped in `overflow-x-auto`, and an absolutely positioned child of a scroll
 * container is CLIPPED by it. A hand-rolled dropdown would be sliced off at the
 * table's edge. A popover is promoted to the browser's top layer, above the
 * whole document, so no ancestor's overflow can touch it - and `popover="auto"`
 * brings light dismiss and Escape along for free, which are the two things
 * hand-rolled menus usually get wrong.
 *
 * The top layer solves the *stacking*, not the *placing*: CSS anchor
 * positioning is not portable yet, so the coordinates are measured below.
 *
 * `children` is a function so an item can close the menu after it acts.
 */
export function RowMenu({
  label,
  children,
}: {
  label: string
  children: (close: () => void) => ReactNode
}) {
  const triggerRef = useRef<HTMLButtonElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)
  const [isOpen, setIsOpen] = useState(false)

  function close() {
    menuRef.current?.hidePopover()
  }

  function open() {
    const menu = menuRef.current
    const trigger = triggerRef.current
    if (!menu || !trigger) return

    // Shown first, then positioned: the menu has no size to measure until it is
    // in the top layer. Both happen in one task, so the browser paints once,
    // afterwards - there is no frame where the menu is visible in the wrong
    // place.
    menu.showPopover()

    const anchor = trigger.getBoundingClientRect()
    const { width, height } = menu.getBoundingClientRect()
    const GAP = 4

    // Flip above the trigger when there is not room below, so the last row of
    // the table does not open a menu that runs off the bottom of the window.
    const below = anchor.bottom + GAP
    const top = below + height > window.innerHeight ? anchor.top - height - GAP : below

    // Right-aligned to the trigger, but never off the left edge on a narrow
    // window.
    const left = Math.max(8, anchor.right - width)

    menu.style.top = `${top}px`
    menu.style.left = `${left}px`
  }

  /**
   * Three ways to close, none of which the popover handles for us.
   *
   * Scroll and resize: the coordinates above are a snapshot, so scrolling moves
   * the row and leaves the menu behind pointing at nothing. Capture phase,
   * because the scroll that matters is the table's own horizontal one and
   * scroll events on an inner element do not bubble to window.
   *
   * Escape is the surprising one. `popover="auto"` is specified to close on
   * Escape and mostly does - but driving this menu in a real browser it closed
   * on some presses and ignored others, with the keydown arriving at the
   * document untouched and no `toggle` event following. Rather than ship a
   * dismiss that works four times in five, Escape is handled here. Outside
   * clicks are left to the browser: that half of light dismiss was reliable
   * every time, and re-implementing it by hand is how menus end up closing when
   * you click their own scrollbar.
   */
  useEffect(() => {
    if (!isOpen) return

    const dismiss = () => close()
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        close()
      }
    }

    window.addEventListener('scroll', dismiss, true)
    window.addEventListener('resize', dismiss)
    document.addEventListener('keydown', onKeyDown, true)

    return () => {
      window.removeEventListener('scroll', dismiss, true)
      window.removeEventListener('resize', dismiss)
      document.removeEventListener('keydown', onKeyDown, true)
    }
  }, [isOpen])

  /**
   * Keeps `isOpen` honest when the browser closes the menu on its own - an
   * outside click - rather than only when this component asks it to. Without
   * it `aria-expanded` would go on claiming the menu is open, and the next
   * click on the trigger would try to close something already closed.
   *
   * addEventListener rather than an `onToggle` prop, which is the part worth
   * remembering: React 19 renders the `popover` attribute happily, but the
   * `toggle` event a popover fires never reached the JSX handler here. The
   * symptom was quiet - the menu closed, `aria-expanded` stayed "true", and
   * nothing in the console said why.
   */
  useEffect(() => {
    const menu = menuRef.current
    if (!menu) return

    const onToggle = (event: Event) => {
      const open = (event as ToggleEvent).newState === 'open'
      setIsOpen(open)

      /*
       * Send focus back to the `⋯` button, or a keyboard user pressing Escape
       * is dumped at the top of the document.
       *
       * It has to happen here rather than next to the close that caused it:
       * hiding a popover moves focus to <body> as part of its own teardown, so
       * anything that focuses the trigger earlier is immediately undone. By the
       * time this event fires, focus has settled and can be read.
       *
       * Only when focus has nowhere else to be. An item that opens a dialog has
       * already handed focus to the dialog, and stealing it back would trap the
       * user behind a modal they cannot reach.
       */
      if (!open && document.activeElement === document.body) {
        triggerRef.current?.focus()
      }
    }

    menu.addEventListener('toggle', onToggle)
    return () => menu.removeEventListener('toggle', onToggle)
  }, [])

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        onClick={() => (isOpen ? close() : open())}
        aria-haspopup="menu"
        aria-expanded={isOpen}
        aria-label={label}
        className="rounded-md border border-line px-2 py-1 text-ink-soft transition hover:border-brand hover:text-brand focus:outline-none focus:ring-2 focus:ring-brand/20"
      >
        <svg
          aria-hidden="true"
          viewBox="0 0 24 24"
          fill="currentColor"
          className="size-4"
        >
          <circle cx="5" cy="12" r="1.75" />
          <circle cx="12" cy="12" r="1.75" />
          <circle cx="19" cy="12" r="1.75" />
        </svg>
      </button>

      {/*
        `[inset:auto]` is load-bearing. The user-agent stylesheet gives every
        popover `inset: 0`, so setting only top and left above would leave right
        and bottom pinned to the viewport and stretch the menu across the page.
      */}
      <div
        ref={menuRef}
        popover="auto"
        role="menu"
        className="fixed m-0 w-44 [inset:auto] rounded-lg border border-line bg-surface p-1 text-ink shadow-lg"
      >
        {children(close)}
      </div>
    </>
  )
}

/** One row in a {@link RowMenu}. `tone` is what marks a destructive action. */
export function RowMenuItem({
  onClick,
  tone = 'default',
  children,
}: {
  onClick: () => void
  tone?: 'default' | 'danger'
  children: ReactNode
}) {
  return (
    <button
      type="button"
      role="menuitem"
      onClick={onClick}
      className={`block w-full rounded-md px-3 py-2 text-left text-sm transition focus:outline-none ${
        tone === 'danger'
          ? 'text-danger-ink hover:bg-danger-soft focus:bg-danger-soft'
          : 'text-ink hover:bg-canvas focus:bg-canvas'
      }`}
    >
      {children}
    </button>
  )
}

/** A hairline between groups of items. */
export function RowMenuSeparator() {
  return <div role="separator" className="my-1 border-t border-line" />
}
