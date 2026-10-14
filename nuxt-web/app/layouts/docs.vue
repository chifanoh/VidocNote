<script setup lang="ts">
import type { ContentNavigationItem, PageCollections } from '@nuxt/content'

// docus 的侧栏数据来自 app.vue 里 provide('navigation', ...)，
// 而本项目的 app/app.vue 覆盖了 docus 的 app.vue，所以这里为 /docs 补一次。
// 用 useAsyncData 让导航进 payload：SSG 下客户端 hydration 不会先空后填。
const { data: navigation } = await useAsyncData(
  'navigation_docs',
  () => queryCollectionNavigation('docs' as keyof PageCollections) as Promise<ContentNavigationItem[]>
)

provide('navigation', navigation)
</script>

<template>
  <!-- app/app.vue 已经提供 UMain，这里不再嵌套（避免出现两个 main 元素） -->
  <UContainer>
    <UPage>
      <template #left>
        <UPageAside>
          <DocsAsideLeftTop />
          <DocsAsideLeftBody />
        </UPageAside>
      </template>
      <slot />
    </UPage>
  </UContainer>
</template>
