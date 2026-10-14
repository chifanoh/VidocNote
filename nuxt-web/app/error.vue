<script setup lang="ts">
import type { NuxtError } from '#app'
import { describePageError } from '~/utils/api-error'

/**
 * 全局兜底错误页：路由 / 组件抛出的未处理异常都渲染到这里（Nuxt 约定文件）。
 * 认不出的地址（docus 的 catch-all 抛 404）在客户端换成真实路由 /404；5xx 保持原 URL 不动，刷新即重试。
 * 它在所有布局之外，故自己包一层 UApp；返回按钮由 AppError 内置（clearError → /）。
 */
const props = defineProps<{ error: NuxtError }>()

onMounted(() => {
  if (props.error.statusCode === 404 && useRoute().path !== '/404') clearError({ redirect: '/404' })
})

const info = computed(() => describePageError(props.error.statusCode))
</script>

<template>
  <UApp>
    <AppError :info="info" />
  </UApp>
</template>