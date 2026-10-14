<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { OrderItem, OrderStatus } from '~/types'
import type { AppErrorInfo } from '~/utils/api-error'
import { describeApiError, EMPTY_ORDERS } from '~/utils/api-error'

definePageMeta({ layout: 'dashboard', header: false, footer: false, middleware: 'auth' })

useSeoMeta({
  title: '账单 - Vidoc Note',
  robots: 'noindex, nofollow'
})

const route = useRoute()
const toast = useToast()
const { orders, confirm } = usePayment()

const list = ref<OrderItem[]>([])
const loading = ref(true)
const error = ref<AppErrorInfo | null>(null)

const STATUS_TEXT: Record<OrderStatus, string> = {
  PENDING: '待支付',
  PAID: '已支付',
  CLOSED: '已关闭'
}

const columns: TableColumn<OrderItem>[] = [
  { accessorKey: 'outTradeNo', header: '订单编号', meta: { class: { td: 'text-ink' } } },
  { accessorKey: 'amount', header: '金额', meta: { class: { td: 'text-ink-3 tabular-nums' } } },
  { accessorKey: 'status', header: '状态' },
  { accessorKey: 'paidAt', header: '支付时间', meta: { class: { td: 'tabular-nums' } } }
]

// @nuxt/ui 默认用中性的 --ui-border / text-muted，这里换成项目令牌（暖灰边框、ink 灰阶）
const TABLE_UI = {
  th: 'text-ink',
  td: 'text-ink-4',
  tbody: 'divide-line',
  separator: 'bg-line'
}

function amountText(fen: number) {
  return `￥${(fen / 100).toFixed(2)}`
}

function timeText(iso: string) {
  return new Date(iso).toLocaleString('zh-CN', { hour12: false })
}

async function load() {
  loading.value = true
  error.value = null
  try {
    list.value = await orders()
  } catch (e) {
    error.value = describeApiError(e)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  // 从支付宝收银台同步跳回时带着 out_trade_no；通知可能比它晚到，这里主动确认一次
  const returned = route.query.out_trade_no
  if (typeof returned === 'string' && returned) {
    try {
      const order = await confirm(returned)
      if (order.status === 'PAID') {
        toast.add({
          title: '支付成功',
          description: '积分已到账',
          color: 'success',
          icon: 'i-material-symbols-check-circle',
          duration: 4000
        })
      }
    } catch {
      // 确认失败不阻断列表；异步通知到达后状态会自行更新
    }
  }
  await load()
})
</script>

<template>
  <div>
    <h1 class="flex items-center gap-2 text-3xl font-semibold tracking-normal text-ink">
      <UIcon name="i-material-symbols-receipt-long" class="size-8 shrink-0" />
      账单
    </h1>

    <div v-if="loading" class="mt-6 overflow-hidden rounded-lg border border-line bg-page shadow-card" role="status" aria-label="加载中">
      <div aria-hidden="true">
        <div class="flex items-center gap-4 border-b border-line px-4 py-3.5">
          <USkeleton class="h-5 w-32" />
          <USkeleton class="h-5 w-14" />
          <USkeleton class="h-5 w-12" />
          <USkeleton class="h-5 w-32" />
        </div>
        <div v-for="i in 3" :key="i" class="flex items-center gap-4 border-b border-line p-4 last:border-b-0">
          <USkeleton class="h-5 w-32" />
          <USkeleton class="h-5 w-14" />
          <USkeleton class="h-5 w-12" />
          <USkeleton class="h-5 w-32" />
        </div>
      </div>
    </div>

    <AppError v-else-if="error" :info="error" compact class="mt-6" />

    <AppError
      v-else-if="!list.length"
      :info="EMPTY_ORDERS"
      icon="i-material-symbols-receipt-long"
      compact
      class="mt-6"
    />

    <div v-else class="mt-6 overflow-hidden rounded-lg border border-line bg-page shadow-card">
      <UTable :data="list" :columns="columns" :ui="TABLE_UI">
        <template #amount-cell="{ row }">
          {{ amountText(row.original.amount) }}
        </template>
        <template #status-cell="{ row }">
          {{ STATUS_TEXT[row.original.status] }}
        </template>
        <template #paidAt-cell="{ row }">
          {{ row.original.paidAt ? timeText(row.original.paidAt) : '—' }}
        </template>
      </UTable>
    </div>
  </div>
</template>
