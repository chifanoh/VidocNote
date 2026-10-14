import type { User } from '~/types'

interface AuthResponse {
  token: string
  user: User
}

/**
 * 登录态管理：token 由 useToken 共享（cookie 只做持久化），用户信息响应式共享。
 */
export function useAuth() {
  const token = useToken()
  const user = useState<User | null>('vidoc_user', () => null)
  const { apiFetch } = useApi()

  const isLoggedIn = computed(() => !!token.value)

  function setSession(t: string, u: User) {
    token.value = t
    user.value = u
  }

  function clearSession() {
    token.value = null
    user.value = null
  }

  // token 是不透明随机串，解不出用户信息，刷新后只能回后端要（见 plugins/auth.client.ts）
  async function fetchMe(): Promise<User> {
    const me = await apiFetch<User>('/api/auth/me')
    user.value = me
    return me
  }

  /** 登录即注册。人机验证的 token 走请求头，不进请求体 */
  async function login(email: string, password: string, turnstileToken?: string) {
    const data = await apiFetch<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: { email, password },
      headers: turnstileToken ? { 'X-Turnstile-Token': turnstileToken } : {}
    })
    setSession(data.token, data.user)
  }

  async function logout() {
    try {
      // 注销服务端会话，否则该 token 在有效期内仍然可用
      await apiFetch('/api/auth/logout', { method: 'POST' })
    } catch {
      // 后端不可达也要让本地登出成功
    }
    clearSession()
    await navigateTo('/')
  }

  return { token, user, isLoggedIn, setSession, clearSession, fetchMe, login, logout }
}
