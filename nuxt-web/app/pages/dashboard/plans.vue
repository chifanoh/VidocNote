<script setup lang="ts">
import type { VidocPlan } from '~/utils/plans'
import { subscribePlans, topupPlans } from '~/utils/plans'

definePageMeta({ layout: 'dashboard', header: false, footer: false, middleware: 'auth' })

useSeoMeta({
  title: '订阅 - Vidoc Note',
  robots: 'noindex, nofollow'
})

const toast = useToast()
const { createOrder, pay, subscription } = usePayment()

const tabs = [
  { label: '订阅', value: 'subscription', slot: 'subscription' },
  { label: '充值', value: 'topup', slot: 'topup' }
]

const buying = ref<string | null>(null)

function withButtons(plans: VidocPlan[]) {
  return plans.map(plan => ({
    ...plan,
    button: {
      ...plan.button,
      loading: buying.value === plan.code,
      disabled: buying.value !== null || !plan.code,
      onClick: () => { void buy(plan) }
    }
  }))
}

const subscribeCards = computed(() => withButtons(subscribePlans))
const topupCards = computed(() => withButtons(topupPlans))

async function subscribed() {
  try {
    return (await subscription()).active
  } catch {
    return false
  }
}

async function buy(plan: VidocPlan) {
  const code = plan.code
  if (!code || buying.value) {
    return
  }
  buying.value = code
  let tab: Window | null = null
  try {
    // 充值卖永久积分，与订阅互不影响，任何时刻都能买，因此不查订阅状态
    if (plan.kind === 'subscription' && await subscribed()) {
      toast.add({
        title: '当前处于订阅期内',
        color: 'warning',
        icon: 'i-material-symbols-info-outline',
        duration: 4000
      })
      return
    }
    // 这一次 await 之后才开窗仍落在浏览器的用户激活窗口内；被拦时 submitPayForm 退回当前页提交
    tab = window.open('', '_blank')
    const order = await createOrder(code)
    const form = await pay(order.outTradeNo)
    submitPayForm(tab, form.formHtml)
  } catch (e: any) {
    tab?.close()
    toast.add({
      title: '下单失败',
      description: e?.data?.message ?? '请稍后重试',
      color: 'error',
      icon: 'i-material-symbols-error-outline',
      duration: 4000
    })
  } finally {
    buying.value = null
  }
}

/** 弹窗被拦时退回当前页提交，不额外引入依赖 */
function submitPayForm(tab: Window | null, formHtml: string) {
  if (tab) {
    tab.document.write(formHtml)
    tab.document.close()
    return
  }
  const holder = document.createElement('div')
  holder.innerHTML = formHtml
  document.body.appendChild(holder)
  holder.querySelector('form')?.submit()
}
</script>

<template>
  <div>
    <h1 class="flex items-center gap-2 text-3xl font-semibold tracking-normal text-ink">
      <UIcon name="i-material-symbols-workspace-premium" class="size-8 shrink-0" />
      订阅
    </h1>
    <p class="mt-4 text-xs text-ink-5">积分到账后不支持退款,请按实际需求购买。如遇系统问题导致积分未到账，请于意见反馈提交处理。</p>

    <UTabs
      :items="tabs"
      default-value="subscription"
      variant="link"
      color="primary"
      :ui="{ list: 'border-line' }"
      class="mt-4"
    >
      <template #subscription>
        <UPricingPlans :plans="subscribeCards" class="mt-6" />
      </template>
      <template #topup>
        <UPricingPlans :plans="topupCards" class="mt-6" />
      </template>
    </UTabs>
  </div>
</template>
