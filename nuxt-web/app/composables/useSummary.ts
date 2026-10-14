import type { CursorPage, Summary, SummaryListItem } from '~/types'

/**
 * 摘要 API 封装：创建、按 id 轮询到终态、历史列表、单条、删除。
 */
export function useSummary() {
  const { apiFetch } = useApi()

  async function create(videoUrl: string): Promise<Summary> {
    return apiFetch<Summary>('/api/summaries', { method: 'POST', body: { videoUrl } })
  }

  async function get(id: string): Promise<Summary> {
    return apiFetch<Summary>(`/api/summaries/${id}`)
  }

  /** 历史列表（游标分页）：不传 cursor 取第一页，之后原样回传上一页的 nextCursor */
  async function list(cursor?: string | null, size = 20): Promise<CursorPage<SummaryListItem>> {
    const query = cursor ? `?cursor=${encodeURIComponent(cursor)}&size=${size}` : `?size=${size}`
    return apiFetch<CursorPage<SummaryListItem>>(`/api/summaries${query}`)
  }

  async function remove(id: string): Promise<void> {
    await apiFetch<void>(`/api/summaries/${id}`, { method: 'DELETE' })
  }

  function isTerminal(status: Summary['status']): boolean {
    return status === 'COMPLETED' || status === 'FAILED'
  }

  /**
   * 轮询单条记录直到终态（每 2.5s）。
   *
   * 失败的任务后端不留存（生成失败即删除记录，见 SummaryProcessor），所以「轮询中记录消失」
   * 就等于「这次生成失败了」：这里拿最后一次看到的记录合成一条 FAILED 返回，让调用方的失败
   * 分支照常走。原因不在记录里（只落服务端日志），故 errorMessage 置空。
   *
   * @param shouldStop 可选：返回 true 时立即停止轮询（返回 null），用于用户中止
   * @returns 终结时的记录；用户中止或非 404 的请求异常返回 null
   */
  async function pollUntilDone(id: string, onUpdate?: (s: Summary) => void, shouldStop?: () => boolean): Promise<Summary | null> {
    let last: Summary | null = null
    for (;;) {
      if (shouldStop?.()) {
        return null
      }
      try {
        const summary = await get(id)
        onUpdate?.(summary)
        last = summary
        if (isTerminal(summary.status)) {
          return summary
        }
      } catch (e: any) {
        const code = e?.statusCode ?? e?.response?.status
        if (code === 404 && last) {
          const failed: Summary = { ...last, status: 'FAILED', errorMessage: null, progressStep: null }
          onUpdate?.(failed)
          return failed
        }
        return null
      }
      await new Promise(resolve => setTimeout(resolve, 2500))
    }
  }

  return { create, get, list, remove, pollUntilDone, isTerminal }
}
