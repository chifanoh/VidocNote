/**
 * 刷新页面后 cookie 里只剩 token，用它回后端恢复用户信息
 * 不阻塞 hydration， 后端临时不可达仅 401 才登出。
 */
export default defineNuxtPlugin(() => {
  const { token, user, fetchMe, clearSession } = useAuth()
  if (!token.value || user.value) return

  fetchMe().catch((e: any) => {
    const code = e?.statusCode ?? e?.response?.status
    if (code === 401) clearSession()
  })
})
