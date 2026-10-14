<script setup lang="ts">
import { useClipboard } from '@vueuse/core'

// 覆盖 docus 的同名组件（app 目录优先于 layer）：
// docus 把「本页 Markdown」指向 `${origin}/raw${route.path}.md`，但 /raw 只在 vercel
// preset 下注册，本站是静态托管 → 死链。nuxt-ai-ready 已为每个页面产出同名 .md 孪生文件
// （如 /docs/guide/quickstart.md），直接指向它即可；MCP 入口也去掉（静态产物没有 /mcp）。
const route = useRoute()
const { copy, copied } = useClipboard()
const { t } = useDocusI18n()

const markdownLink = computed(() => `${window?.location?.origin}${route.path}.md`)

const items = computed(() => [
  [{
    label: t('docs.copy.link'),
    icon: 'i-lucide-link',
    onSelect() {
      copy(markdownLink.value)
    }
  }, {
    label: t('docs.copy.view'),
    icon: 'i-simple-icons:markdown',
    target: '_blank',
    to: markdownLink.value
  }, {
    label: t('docs.copy.gpt'),
    icon: 'i-simple-icons:openai',
    target: '_blank',
    to: `https://chatgpt.com/?hints=search&q=${encodeURIComponent(`Read ${markdownLink.value} so I can ask questions about it.`)}`
  }, {
    label: t('docs.copy.claude'),
    icon: 'i-simple-icons:anthropic',
    target: '_blank',
    to: `https://claude.ai/new?q=${encodeURIComponent(`Read ${markdownLink.value} so I can ask questions about it.`)}`
  }]
])

async function copyPage() {
  copy(await $fetch<string>(`${route.path}.md`))
}
</script>

<template>
  <UFieldGroup size="sm">
    <UButton
      :label="t('docs.copy.page')"
      :icon="copied ? 'i-lucide-check' : 'i-lucide-copy'"
      color="neutral"
      variant="soft"
      :ui="{
        leadingIcon: 'text-neutral size-3.5',
      }"
      @click="copyPage"
    />

    <UDropdownMenu
      size="sm"
      :items="items"
      :content="{
        align: 'end',
        side: 'bottom',
        sideOffset: 8,
      }"
    >
      <UButton
        icon="i-lucide-chevron-down"
        color="neutral"
        variant="soft"
        class="border-l border-muted"
      />
    </UDropdownMenu>
  </UFieldGroup>
</template>
