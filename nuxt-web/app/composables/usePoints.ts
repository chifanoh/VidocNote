import type { PointsLedgerItem, PointsSummary } from '~/types'

/** 积分 API 封装 */
export function usePoints() {
  const { apiFetch } = useApi()

  async function summary(): Promise<PointsSummary> {
    return apiFetch<PointsSummary>('/api/points')
  }

  async function ledger(size = 20): Promise<PointsLedgerItem[]> {
    return apiFetch<PointsLedgerItem[]>(`/api/points/ledger?size=${size}`)
  }

  return { summary, ledger }
}
