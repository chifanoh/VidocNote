<script setup lang="ts">
import { LEGAL_UPDATED_AT } from '~/utils/legal'

/**
 * 法务页外壳（用户协议 / 隐私政策）
 */
defineProps<{ title: string, icon: string }>()
</script>

<template>
  <div class="mx-auto max-w-4xl py-12">
    <h1 class="flex items-center gap-2 text-3xl font-semibold tracking-normal text-ink">
      <UIcon :name="icon" class="size-8 shrink-0" />
      {{ title }}
    </h1>
    <p class="mt-2 text-sm text-ink-4">最后更新：{{ LEGAL_UPDATED_AT }}</p>

    <div class="legal-prose mt-8">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.legal-prose :deep(h2) {
  margin: 2rem 0 0.75rem;
  font-size: 1.125rem;
  font-weight: 600;
  line-height: 1.75rem;
  color: var(--color-ink-2);
}

.legal-prose :deep(p) {
  margin: 0.75rem 0;
  font-size: 0.875rem;
  line-height: 1.5rem;
  color: var(--color-ink-3);
}

.legal-prose :deep(strong) {
  font-weight: 600;
  color: var(--color-ink);
}

.legal-prose :deep(ul) {
  margin: 0.75rem 0;
  padding-left: 1.25rem;
  list-style: disc;
  font-size: 0.875rem;
  line-height: 1.5rem;
  color: var(--color-ink-3);
}

.legal-prose :deep(li) {
  margin: 0.375rem 0;
}

.legal-prose :deep(li::marker) {
  color: var(--color-ink-5);
}

.legal-prose :deep(code) {
  padding: 0.125rem 0.375rem;
  border-radius: 0.25rem;
  border: 1px solid var(--color-line);
  background: var(--color-page);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.8125rem;
  color: var(--color-ink-2);
}

.legal-prose :deep(a) {
  color: var(--color-ink-2);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.legal-prose :deep(a:hover) {
  color: var(--color-hover);
}

.legal-prose :deep(:first-child) {
  margin-top: 0;
}

.legal-prose :deep(:last-child) {
  margin-bottom: 0;
}
</style>
