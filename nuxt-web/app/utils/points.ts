import type { PointsReason } from '~/types'

const REASON_LABELS: Record<PointsReason, string> = {
  CONSUME: '生成摘要',
  REFUND: '生成失败退回',
  PURCHASE: '购买积分'
}

export function pointsReasonLabel(reason: PointsReason): string {
  return REASON_LABELS[reason] ?? reason
}

/** 带符号的分值：发放 +30、消耗 -30 */
export function formatPointsDelta(delta: number): string {
  return `${delta > 0 ? '+' : ''}${delta}`
}
