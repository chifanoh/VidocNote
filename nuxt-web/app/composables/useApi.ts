/**
 * 统一 API 请求入口：dev 走 /api 代理，生产走 NUXT_PUBLIC_API_BASE 指向的后端域名。
 * 自动附带登录 token 请求头。
 */
export function useApi() {
  const config = useRuntimeConfig()
  // 取共享 token，避免与 useAuth 形成循环依赖
  const token = useToken()

  const baseUrl = computed(() => {
    const base = config.public.apiBase as string
    return base ? base.replace(/\/$/, '') : ''
  })

  async function apiFetch<T>(path: string, options: { method?: string; body?: unknown; headers?: Record<string, string> } = {}): Promise<T> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...(token.value ? { Authorization: `Bearer ${token.value}` } : {}),
      ...(options.headers ?? {})
    }
    return $fetch<T>(`${baseUrl.value}${path}`, {
      method: options.method ?? 'GET',
      body: options.body,
      headers
    })
  }

  return { apiFetch }
}

