import type { PricingPlanProps } from '@nuxt/ui'

/**
 * code 与后端 Plan 枚举一一对应，kind 决定它是哪种商品；没有 code 的档位不可下单。
 * 这里是纯展示，价格与额度的唯一真源是服务端 `entity/Plan`，改价必须两边一起改。
 */
export type VidocPlan = PricingPlanProps & {
  code?: string
  kind?: 'subscription' | 'topup'
}

/** 订阅：限时积分，按期到账、到期作废，生效期内不可再买（首页 Pricing 区块与仪表盘共用） */
export const subscribePlans: VidocPlan[] = [
  {
    title: '入门月付',
    description: '1000积分/月',
    price: '￥9.9',
    code: 'monthly_9',
    kind: 'subscription',
    features: [
      '每月 1000 积分',
      '比单买充值更划算'
    ],
    button: {
      label: '订阅'
    }
  },
  {
    title: '月付',
    description: '2000积分/月',
    price: '￥19.9',
    code: 'monthly',
    kind: 'subscription',
    features: [
      '每月 2000 积分',
      '比单买充值更划算'
    ],
    button: {
      label: '订阅'
    }
  },
  {
    title: '年费',
    description: '3000积分/月',
    price: '￥189',
    code: 'yearly',
    kind: 'subscription',
    features: [
      '每月 3000 积分',
      '比单买充值更划算'
    ],
    button: {
      label: '订阅'
    }
  }
]

/** 充值：永久积分，一次到账、不过期、可重复购买。单独买比订阅贵，档位越大越划算 */
export const topupPlans: VidocPlan[] = [
  {
    title: '500 积分',
    description: '永久有效',
    price: '￥10',
    code: 'topup_10',
    kind: 'topup',
    features: [
      '积分永不过期',
      '可重复购买，额度累加'
    ],
    button: {
      label: '充值'
    }
  },
  {
    title: '3000 积分',
    description: '永久有效',
    price: '￥50',
    code: 'topup_50',
    kind: 'topup',
    features: [
      '积分永不过期',
      '可重复购买，额度累加'
    ],
    button: {
      label: '充值'
    }
  },
  {
    title: '7000 积分',
    description: '永久有效',
    price: '￥100',
    code: 'topup_100',
    kind: 'topup',
    features: [
      '积分永不过期',
      '可重复购买，额度累加'
    ],
    button: {
      label: '充值'
    }
  }
]
