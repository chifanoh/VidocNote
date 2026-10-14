const DEVTOOLS_SHORTCUTS = ['I', 'J', 'C', 'K']

/**
 * 禁用右键 + 拦截 F12 / DevTools 快捷键
 */
export default defineNuxtPlugin(() => {
  function onContextMenu(event: MouseEvent) {
    const el = event.target as Element | null
    if (el?.closest('input, textarea, [contenteditable]:not([contenteditable="false"])')) return
    event.preventDefault()
  }

  function onKeyDown(event: KeyboardEvent) {
    const combo = (event.ctrlKey || event.metaKey) && event.shiftKey && DEVTOOLS_SHORTCUTS.includes(event.key.toUpperCase())
    if (event.key === 'F12' || combo) event.preventDefault()
  }

  document.addEventListener('contextmenu', onContextMenu)
  document.addEventListener('keydown', onKeyDown, true)
})
