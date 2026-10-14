<template>
  <section class="Home_homeSection">
    <div class="Home_homeSectionHeader">
      <h2>常见问题</h2>
    </div>
    <UAccordion :items="items" :ui="{ label: 'text-ink', body: 'text-ink-4' }"/>
    <!-- 折叠面板是 button + hidden，HTML→Markdown（index.md / llms-full.txt）时问答会被整段丢掉。
         这里补一份视觉隐藏的等价文本：sr-only 不被 mdream 过滤，aria-hidden 又避免读屏重复播报 -->
    <div class="sr-only" aria-hidden="true">
      <template v-for="item in items" :key="item.label">
        <h3>{{ item.label }}</h3>
        <p>{{ item.content }}</p>
      </template>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { AccordionItem } from '@nuxt/ui'
import { faqItems } from '~/utils/faq'

// 与首页 FAQPage 结构化数据同源，见 utils/faq.ts
const items = ref<AccordionItem[]>(faqItems.map(item => ({
  label: item.question,
  content: item.answer
})))
</script>