<script setup lang="ts">
import { parseMarkdown } from '@nuxtjs/mdc/runtime'
import type { TocLink } from '~/types'

/**
 * 摘要正文渲染：把 LLM 产出的 Markdown 渲染成带样式的正文。
 * 解析结果里的 toc 通过 `parsed` 事件交给父级（阅读页右栏目录用）；
 * 展示层与复制层分离——复制走的始终是父级持有的 Markdown 原文。
 */
const props = defineProps<{ value: string | null | undefined }>()
const emit = defineEmits<{ parsed: [links: TocLink[]] }>()

// MDCRenderer 的 body 是解析产物，无需深层响应式
const body = shallowRef<unknown>(null)
let seq = 0

// 标题渲染成原生标签：MDCRenderer 默认把 h1–h6 映射到 Nuxt UI 的 Prose 组件，
// 而 ProseH2 会给标题再包一层 <a> 锚点（悬停冒出 #，正文样式又把 <a> 当链接染蓝加下划线）。
// 摘要正文只要纯标题；映射成原生 h1–h6 后 id 仍保留（阅读页右栏目录靠它跳转）。
const components = { h1: 'h1', h2: 'h2', h3: 'h3', h4: 'h4', h5: 'h5', h6: 'h6' }

watch(
  () => props.value,
  async (value) => {
    const my = ++seq
    const md = (value ?? '').trim()
    if (!md) {
      body.value = null
      emit('parsed', [])
      return
    }
    try {
      const parsed = await parseMarkdown(md)
      if (my !== seq) return // 期间内容已变，丢弃过期结果
      body.value = parsed.body
      emit('parsed', (parsed.toc?.links as TocLink[] | undefined) ?? [])
    } catch {
      if (my !== seq) return
      body.value = null
      emit('parsed', [])
    }
  },
  { immediate: true }
)
</script>

<template>
  <div class="summary-md">
    <MDCRenderer v-if="body" :body="body" :components="components" />
    <p v-else class="text-sm leading-6 text-ink-4">（无摘要内容）</p>
  </div>
</template>

<style scoped>
/* Markdown 正文样式：只用 main.css 的设计令牌，不写裸色值（DESIGN.md §9.1）。
   MDCRenderer 渲染的节点属于子组件，故统一用 :deep() 命中。 */
.summary-md :deep(h1),
.summary-md :deep(h2) {
  margin: 1.75rem 0 0.75rem;
  font-size: 1.125rem;
  font-weight: 600;
  line-height: 1.75rem;
  color: var(--color-ink-2);
}

.summary-md :deep(h3),
.summary-md :deep(h4),
.summary-md :deep(h5),
.summary-md :deep(h6) {
  margin: 1.25rem 0 0.5rem;
  font-size: 1rem;
  font-weight: 600;
  line-height: 1.5rem;
  color: var(--color-ink-2);
}

.summary-md :deep(p) {
  margin: 0.75rem 0;
  font-size: 0.875rem;
  line-height: 1.5rem;
  color: var(--color-ink-3);
}

.summary-md :deep(strong) {
  font-weight: 600;
  color: var(--color-ink);
}

.summary-md :deep(ul),
.summary-md :deep(ol) {
  margin: 0.75rem 0;
  padding-left: 1.25rem;
  font-size: 0.875rem;
  line-height: 1.5rem;
  color: var(--color-ink-3);
}

.summary-md :deep(ul) {
  list-style: disc;
}

.summary-md :deep(ol) {
  list-style: decimal;
}

.summary-md :deep(li) {
  margin: 0.375rem 0;
}

.summary-md :deep(li::marker) {
  color: var(--color-ink-5);
}

.summary-md :deep(code) {
  padding: 0.125rem 0.375rem;
  border-radius: 0.25rem;
  border: 1px solid var(--color-line);
  background: var(--color-page);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.8125rem;
  color: var(--color-ink-2);
}

.summary-md :deep(pre) {
  margin: 1rem 0;
  padding: 0.875rem 1rem;
  border-radius: 0.375rem;
  border: 1px solid var(--color-line);
  background: var(--color-page);
  overflow-x: auto;
}

.summary-md :deep(pre code) {
  padding: 0;
  background: transparent;
  font-size: 0.8125rem;
  line-height: 1.5rem;
}

.summary-md :deep(blockquote) {
  margin: 1rem 0;
  padding: 0.25rem 0 0.25rem 1rem;
  border-left: 3px solid var(--color-line-strong);
  color: var(--color-ink-4);
}

/* 链接：默认黑字 + 下划线，悬停转全站强调蓝 */
.summary-md :deep(a) {
  color: var(--color-ink-2);
  text-decoration: underline;
  text-underline-offset: 2px;
  word-break: break-all;
}

.summary-md :deep(a:hover) {
  color: var(--color-hover);
}

.summary-md :deep(hr) {
  margin: 1.5rem 0;
  border: 0;
  border-top: 1px solid var(--color-line);
}

.summary-md :deep(table) {
  width: 100%;
  margin: 1rem 0;
  border-collapse: collapse;
  font-size: 0.875rem;
}

.summary-md :deep(th),
.summary-md :deep(td) {
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--color-line);
  text-align: left;
}

.summary-md :deep(th) {
  background: var(--color-page);
  font-weight: 600;
  color: var(--color-ink-2);
}

.summary-md :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 0.375rem;
}

/* 首尾元素去掉多余外边距，嵌入卡片时上下留白交给容器 */
.summary-md :deep(:first-child) {
  margin-top: 0;
}

.summary-md :deep(:last-child) {
  margin-bottom: 0;
}
</style>
