<script setup lang="ts">
import type { PointsLedgerItem, PointsSummary } from '~/types'
import type { AppErrorInfo } from '~/utils/api-error'
import { describeApiError } from '~/utils/api-error'
import { formatPointsDelta, pointsReasonLabel } from '~/utils/points'

definePageMeta({ layout: 'dashboard', header: false, footer: false, middleware: 'auth' })

useSeoMeta({
  title: '用量信息 - Vidoc Note',
  robots: 'noindex, nofollow'
})

const LEDGER_SIZE = 10

const { summary: fetchSummary, ledger } = usePoints()
const { isLoggedIn } = useAuth()
const route = useRoute()

const points = ref<PointsSummary | null>(null)
const items = ref<PointsLedgerItem[]>([])
const loading = ref(true)
const error = ref<AppErrorInfo | null>(null)

function formatTime(iso: string): string {
  return new Date(iso).toLocaleString('zh-CN', { hour12: false })
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const balance = await fetchSummary()
    const rows = await ledger(LEDGER_SIZE)
    points.value = balance
    items.value = rows ?? []
  } catch (e: unknown) {
    error.value = describeApiError(e)
  } finally {
    loading.value = false
  }
}

watch(isLoggedIn, (loggedIn) => {
  if (loggedIn && error.value?.needLogin) void load()
})

onMounted(() => {
  void load()
})
</script>

<template>
  <div>
    <h1 class="flex items-center gap-2 text-3xl font-semibold tracking-normal text-ink">
      <UIcon name="i-material-symbols-monitoring" class="size-8 shrink-0" />
      用量信息
    </h1>

    <AppError v-if="error" :info="error" compact class="mt-6" />

    <div v-else-if="loading" class="mt-6 flex flex-col gap-4" role="status" aria-label="加载中">
      <div class="rounded-lg border border-line bg-page p-6 shadow-card" aria-hidden="true">
        <USkeleton class="h-4 w-20" />
        <div class="mt-3 flex items-center gap-2">
          <USkeleton class="size-8 shrink-0 rounded-full" />
          <USkeleton class="h-10 w-24" />
        </div>
        <USkeleton class="mt-3 h-3.5 w-48" />
      </div>
      <div class="rounded-lg border border-line bg-page p-5 shadow-card" aria-hidden="true">
        <USkeleton class="h-4 w-16" />
        <div class="mt-4 space-y-3">
          <USkeleton v-for="i in 4" :key="i" class="h-8 w-full" />
        </div>
      </div>
    </div>

    <template v-else-if="points">
      <div class="mt-6 rounded-lg border border-line bg-page p-6 shadow-card">
        <p class="text-md text-ink">可用积分</p>
        <p class="mt-1 flex items-center gap-2 text-ink">
          <span class="points-icon size-8 shrink-0" aria-hidden="true"></span>
          <span class="text-4xl font-semibold">{{ points.balance }}</span>
        </p>
        
      </div>

      <div class="mt-4 rounded-lg border border-line bg-page p-5 shadow-card">
        <div>
          <p class="text-md text-ink">最近流水</p>
          <p class="text-sm text-ink-5">只显示用户最近50条流水记录</p>
        </div>
        <ul v-if="items.length" class="mt-2">
          <li
            v-for="(row, i) in items"
            :key="i"
            class="flex items-center justify-between gap-3 border-t border-line py-3 first:border-t-0"
          >
            <span class="min-w-0">
              <span class="block truncate text-sm text-ink">{{ pointsReasonLabel(row.reason) }}</span>
              <span class="block text-xs text-ink-5">{{ formatTime(row.createdAt) }}</span>
            </span>
            <span
              class="shrink-0 text-sm tabular-nums"
              :class="row.delta > 0 ? 'text-ink' : 'text-ink-4'"
            >{{ formatPointsDelta(row.delta) }}</span>
          </li>
        </ul>

        <p v-else class="mt-3 text-sm text-ink-5">暂无积分记录</p>
      </div>
    </template>
  </div>
</template>

<style scoped>
.points-icon {
  display: inline-block;
  background-color: currentColor;
  -webkit-mask-image: url("/logo/cib--bitcoin.svg");
  mask-image: url("/logo/cib--bitcoin.svg");
  -webkit-mask-position: center;
  mask-position: center;
  -webkit-mask-repeat: no-repeat;
  mask-repeat: no-repeat;
  -webkit-mask-size: contain;
  mask-size: contain;
}
</style>
