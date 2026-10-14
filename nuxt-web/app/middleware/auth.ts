export default defineNuxtRouteMiddleware((to) => {
  const { isLoggedIn } = useAuth()
  if (!isLoggedIn.value) {
    // 跳回首页并携带 auth 标记，由 app.vue 自动弹出登录框；原目标页通过 redirect 参数在登录后跳回
    return navigateTo(`/?auth=login&redirect=${encodeURIComponent(to.fullPath)}`)
  }
})
