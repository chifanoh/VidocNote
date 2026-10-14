<script setup lang="ts">
/** 仪表盘子页的居中提示卡：图标 + 一句文案，异常态与空态共用同一外观 */
defineProps<{
  icon: string
  text: string
}>()
</script>

<template>
  <div class="rounded-lg border border-line bg-page py-16 text-center shadow-card">
    <UIcon :name="icon" class="mx-auto mb-3 size-10 text-ink-4" />
    <p class="text-ink-4">{{ text }}</p>
    <slot />
  </div>
</template>
