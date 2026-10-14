import type { Summary, TerminalStep } from '~/types'

/** 流水线步骤：key 与后端 progressStep 的取值一一对应，展示文案只在这里维护 */
export const SUMMARY_STEPS = [
  { key: 'FETCH_METADATA', label: '解析视频信息' },
  { key: 'FETCH_TRANSCRIPT', label: '获取并解析字幕' },
  { key: 'SUMMARIZE', label: '生成Markdown' }
] as const

/** 未开始的任务只有 PENDING/PROCESSING，此时第一步算进行中 */
export function buildSummarySteps(status: Summary['status'], stepKey: string | null): TerminalStep[] {
  const found = SUMMARY_STEPS.findIndex(step => step.key === stepKey)
  const running = status === 'PENDING' || status === 'PROCESSING'
  const current = found >= 0 ? found : (status === 'FAILED' || running ? 0 : -1)

  return SUMMARY_STEPS.map((step, i) => {
    if (status === 'COMPLETED') {
      return { key: step.key, label: step.label, state: 'done' as const }
    }
    if (i < current) {
      return { key: step.key, label: step.label, state: 'done' as const }
    }
    if (i === current) {
      return { key: step.key, label: step.label, state: status === 'FAILED' ? 'failed' as const : 'active' as const }
    }
    return { key: step.key, label: step.label, state: 'pending' as const }
  })
}
