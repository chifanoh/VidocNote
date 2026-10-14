<script setup lang="ts">
const route = useRoute()
const { openModal, open, redirect } = useAuthModal()

// 中间件将未登录访问受保护页重定向到 /?auth=login&redirect=...，这里自动弹出登录框；
// 打开后清掉 URL 里的 auth/redirect 标记（redirect 已存入弹窗状态），避免刷新/返回时重复弹出。
watch(
  () => route.query.auth,
  (auth) => {
    if (auth === 'login' || auth === 'register') {
      const target = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
        ? route.query.redirect
        : null
      openModal(target)
      if (import.meta.client) {
        const query = { ...route.query }
        delete query.auth
        delete query.redirect
        navigateTo({ query }, { replace: true })
      }
    }
  },
  { immediate: true }
)

// 弹窗被关闭（未成功登录）时清掉待跳转目标，避免下次手动登录被带到旧目标页
watch(open, (isOpen) => {
  if (!isOpen) {
    redirect.value = null
  }
})
</script>

<template>
  <UApp :toaster="{ progress: false }">
    <!-- 首访横幅 -->
    <SignupBanner v-if="route.meta.header !== false" />
    <Header v-if="route.meta.header !== false" />
    <UMain>
      <NuxtLayout>
        <NuxtPage />
      </NuxtLayout>
    </UMain>
    <Footer v-if="route.meta.footer !== false" />
    <AuthModal />
  </UApp>
</template>
