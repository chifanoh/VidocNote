interface TurnstileWidgetOptions {
  sitekey: string
  action?: string
  theme?: 'light' | 'dark' | 'auto'
  language?: string
  callback?: (token: string) => void
  'error-callback'?: () => void
  'expired-callback'?: () => void
}

interface TurnstileApi {
  render: (el: HTMLElement, options: TurnstileWidgetOptions) => string
  reset: (widgetId?: string) => void
  remove: (widgetId?: string) => void
}

declare global {
  interface Window {
    turnstile?: TurnstileApi
  }
}

const SCRIPT_SRC = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'
let scriptPromise: Promise<void> | null = null

function loadScript(): Promise<void> {
  if (window.turnstile) {
    return Promise.resolve()
  }
  scriptPromise ??= new Promise<void>((resolve, reject) => {
    const el = document.createElement('script')
    el.src = SCRIPT_SRC
    el.async = true
    el.defer = true
    el.onload = () => resolve()
    el.onerror = () => {
      scriptPromise = null
      reject(new Error('turnstile script failed'))
    }
    document.head.appendChild(el)
  })
  return scriptPromise
}

/**
 * Cloudflare Turnstile 人机验证。site key 为空时整个小部件不渲染，后端也不校验（本地开发）。
 * token 是一次性的：提交过一次就必须 reset 换新的，否则第二次提交必被服务端拒。
 */
export function useTurnstile() {
  const siteKey = (useRuntimeConfig().public.turnstileSiteKey as string) || ''
  const enabled = computed(() => !!siteKey)
  const token = ref('')
  const failed = ref(false)
  let widgetId: string | null = null

  async function mount(el: HTMLElement) {
    if (!siteKey || widgetId) {
      return
    }
    try {
      await loadScript()
      // 等脚本的这段时间里元素可能已经被卸载，别往脱离文档的节点里渲染
      if (!el.isConnected) {
        return
      }
      widgetId = window.turnstile!.render(el, {
        sitekey: siteKey,
        action: 'login',
        theme: 'light',
        language: 'zh-cn',
        callback: (value) => {
          token.value = value
          failed.value = false
        },
        'error-callback': () => {
          failed.value = true
        },
        'expired-callback': () => {
          token.value = ''
        }
      })
    } catch {
      failed.value = true
    }
  }

  function reset() {
    token.value = ''
    if (widgetId && window.turnstile) {
      window.turnstile.reset(widgetId)
    }
  }

  function unmount() {
    token.value = ''
    if (widgetId && window.turnstile) {
      window.turnstile.remove(widgetId)
    }
    widgetId = null
  }

  return { enabled, token, failed, mount, reset, unmount }
}
