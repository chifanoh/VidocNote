<script setup lang="ts">
import type { TocLink } from '~/types'

/** 文章目录（递归）：id 与正文标题 id 一致，点击交给父级定位 */
defineProps<{ links: TocLink[]; activeId?: string | null }>()
const emit = defineEmits<{ select: [id: string] }>()
</script>

<template>
  <ul class="space-y-0.5">
    <li v-for="link in links" :key="link.id">
      <button
        type="button"
        class="block w-full rounded px-2 py-1 text-left text-sm leading-6 transition-colors hover:text-hover"
        :class="[
          link.depth >= 3 ? 'pl-6 text-[13px]' : '',
          activeId === link.id ? 'font-medium text-hover' : 'text-ink-4'
        ]"
        @click="emit('select', link.id)"
      >
        {{ link.text }}
      </button>
      <SummaryToc
        v-if="link.children?.length"
        :links="link.children"
        :active-id="activeId"
        @select="emit('select', $event)"
      />
    </li>
  </ul>
</template>
