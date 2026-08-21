const FOCUSABLE_SELECTOR = [
  'button:not([disabled])',
  '[href]',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

function focusableIn(container: HTMLElement): HTMLElement[] {
  return Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR))
    .filter((element) => !element.hasAttribute('disabled') && element.offsetParent !== null)
}

/** 将焦点交给窗口内第一个可操作控件，无控件时聚焦窗口本身。 */
export function focusFirstIn(container: HTMLElement | null): void {
  if (!container) return
  const target = focusableIn(container)[0] ?? container
  target.focus()
}

/** 将 Tab 焦点限制在当前阻塞窗口中。 */
export function trapFocus(event: KeyboardEvent, container: HTMLElement | null): void {
  if (event.key !== 'Tab' || !container) return
  const focusable = focusableIn(container)
  if (!focusable.length) {
    event.preventDefault()
    container.focus()
    return
  }
  const first = focusable[0]!
  const last = focusable[focusable.length - 1]!
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}
