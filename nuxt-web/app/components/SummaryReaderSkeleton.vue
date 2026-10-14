<template>
  <div class="flex h-full flex-col gap-6 lg:flex-row" role="status" aria-label="加载中">
    <aside class="w-full shrink-0 lg:w-72" aria-hidden="true">
      <div class="rounded-lg border border-line bg-page p-5 shadow-card">
        <USkeleton class="aspect-video w-full rounded" />
        <USkeleton class="mt-4 h-5 w-11/12" />
        <div class="mt-4 space-y-2">
          <USkeleton class="h-4 w-2/3" />
          <USkeleton class="h-4 w-1/2" />
          <USkeleton class="h-4 w-1/3" />
        </div>
        <USkeleton class="mt-4 h-4 w-24" />
      </div>
    </aside>

    <div class="min-w-0 flex-1">
      <div class="rounded-lg border border-line bg-page p-6 shadow-card">
        <div class="mb-4 flex items-center gap-1 border-b border-line pb-4" aria-hidden="true">
          <USkeleton class="h-8 w-16 rounded-md" />
          <USkeleton class="h-8 w-16 rounded-md" />
          <USkeleton class="h-8 w-16 rounded-md" />
          <USkeleton class="ms-auto h-8 w-16 rounded-md" />
        </div>
        <div class="space-y-3" aria-hidden="true">
          <USkeleton class="h-5 w-1/3" />
          <USkeleton class="h-4 w-full" />
          <USkeleton class="h-4 w-full" />
          <USkeleton class="h-4 w-5/6" />
          <USkeleton class="mt-6 h-5 w-1/4" />
          <USkeleton class="h-4 w-full" />
          <USkeleton class="h-4 w-11/12" />
          <USkeleton class="h-4 w-3/4" />
          <USkeleton class="mt-6 h-5 w-1/3" />
          <USkeleton class="h-4 w-full" />
          <USkeleton class="h-4 w-full" />
          <USkeleton class="h-4 w-2/3" />
          <USkeleton class="mt-6 h-5 w-1/5" />
          <USkeleton class="h-4 w-11/12" />
          <USkeleton class="h-4 w-full" />
          <USkeleton class="h-4 w-4/5" />
        </div>
      </div>
    </div>

    <nav class="hidden w-56 shrink-0 xl:block" aria-hidden="true">
      <div class="rounded-lg border border-line bg-page p-4 shadow-card">
        <USkeleton class="h-6 w-20" />
        <div class="mt-4 space-y-2">
          <USkeleton class="h-4 w-full" />
          <USkeleton class="h-4 w-3/4" />
          <USkeleton class="h-4 w-full" />
          <USkeleton class="h-4 w-2/3" />
          <USkeleton class="h-4 w-full" />
          <USkeleton class="h-4 w-1/2" />
        </div>
      </div>
    </nav>
  </div>
</template>
