<script setup lang="ts">
import type { AppErrorInfo } from '~/utils/api-error'

/** 全站异常 */
const props = withDefaults(defineProps<{
  info: AppErrorInfo
  icon?: string
  compact?: boolean
}>(), { icon: 'i-lucide-file-x', compact: false })

const ui = computed(() => props.compact
  ? {
      root: 'min-h-0 py-10',
      statusMessage: 'mt-2 text-2xl font-bold tracking-normal text-balance text-ink sm:text-2xl',
      message: 'mt-2 text-sm text-ink-4 text-balance'
    }
  : undefined)
</script>

<template>
  <AppNotice v-if="!info.statusCode" :icon="icon" :text="info.statusMessage" />

  <UError
    v-else
    :icon="icon"
    :clear="{
      color: 'neutral',
      size: 'xl',
      icon: 'i-lucide-arrow-left',
      label: '返回首页',
      class: 'rounded-full'
    }"
    :error="{
      statusCode: info.statusCode,
      statusMessage: info.statusMessage,
      message: info.message
    }"
    :ui="ui"
  />
</template>
