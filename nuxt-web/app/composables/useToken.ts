/**
 * token 的唯一真源：useState 同 key 全应用共享，cookie 只负责持久化与 SSR 读取。
 * 不能各处各自 useCookie —— 那样每个调用方拿到的是实例化那一刻的独立快照，
 * 请求头会滞后于 cookie，且同步依赖浏览器的 Cookie Store API 事件。
 */
export function useToken() {
  const cookie = useCookie<string | null>('vidoc_token', {
    maxAge: 7 * 24 * 60 * 60,
    sameSite: 'lax',
    secure: import.meta.env.PROD
  })
  // 首次调用方负责初始化；SSR 取自请求头，客户端刷新取自 document.cookie
  const token = useState<string | null>('vidoc_token', () => cookie.value ?? null)
  if (import.meta.client) {
    // 静态产物（nuxt generate）把载荷里的初值烘死在构建期（构建时读不到 cookie，恒为 null），
    // 而 hydration 时载荷已含这个 key、不会再执行上面的 initializer —— 不显式纠正就是「刷新即登出」。
    // 开发模式每请求 SSR 能读到 cookie，所以这个 bug 只在线上出现。
    if (cookie.value && token.value !== cookie.value) {
      token.value = cookie.value
    }
    // sync：写完立刻落 cookie，否则用户紧接着硬刷新会丢掉这次写入（内存态已随刷新消失）
    watch(token, v => { cookie.value = v }, { flush: 'sync' })
  }
  return token
}
