<script setup lang="ts">
// 首页 ?id= 直达时结果卡的占位：形状照 SummaryResult 逐块复制（缩略图 / 标题 / 元信息 /
// 正文若干行 / 底部工具条），否则加载完成瞬间会从「一个 160px 灰块」跳成整张卡
</script>

<template>
  <div class="overflow-hidden rounded-lg border border-line bg-page shadow-card" role="status" aria-label="加载中">
    <div class="flex items-start gap-4 p-5 sm:p-6" aria-hidden="true">
      <USkeleton class="h-16 w-28 shrink-0 rounded" />
      <div class="min-w-0 flex-1">
        <USkeleton class="h-5 w-3/4" />
        <USkeleton class="mt-2 h-4 w-1/3" />
      </div>
      <USkeleton class="size-6 shrink-0 rounded" />
    </div>

    <div class="px-5 pb-5 sm:px-6 sm:pb-6" aria-hidden="true">
      <div class="space-y-3">
        <USkeleton class="h-5 w-2/5" />
        <USkeleton class="h-4 w-full" />
        <USkeleton class="h-4 w-11/12" />
        <USkeleton class="h-4 w-4/5" />
        <USkeleton class="mt-6 h-5 w-1/3" />
        <USkeleton class="h-4 w-full" />
        <USkeleton class="h-4 w-5/6" />
      </div>

      <div class="mt-5 flex items-center gap-1 border-t border-line pt-4">
        <USkeleton class="h-8 w-16 rounded-md" />
        <USkeleton class="h-8 w-20 rounded-md" />
        <USkeleton class="ms-auto h-8 w-16 rounded-md" />
      </div>
    </div>
  </div>
</template>
