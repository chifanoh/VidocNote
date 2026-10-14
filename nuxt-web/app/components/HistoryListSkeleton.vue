<script setup lang="ts">
withDefaults(defineProps<{ count?: number }>(), { count: 4 })
</script>

<template>
  <div class="flex flex-col gap-4 lg:gap-y-4" role="status" aria-label="加载中">
    <div
      v-for="i in count"
      :key="i"
      aria-hidden="true"
      class="flex flex-row items-start gap-4 border border-line bg-page p-4 shadow-card sm:gap-5 sm:p-5 lg:grid lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)] lg:items-center lg:gap-x-6"
    >
      <USkeleton class="aspect-video w-1/3 shrink-0 rounded lg:w-full" />
      <div class="flex min-w-0 flex-1 flex-col gap-3 lg:self-stretch">
        <USkeleton class="h-5 w-16 rounded-full" />
        <USkeleton class="h-6 w-4/5" />
        <USkeleton class="h-4 w-1/2 lg:mt-auto" />
      </div>
    </div>
  </div>
</template>
