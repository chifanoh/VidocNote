import type { AgentChatResponse } from '~/types'

export function useAgentChat() {
  const { apiFetch } = useApi()

  async function ask(summaryId: string, question: string): Promise<AgentChatResponse> {
    return apiFetch<AgentChatResponse>(`/api/summaries/${summaryId}/agent/chat`, {
      method: 'POST',
      body: { question }
    })
  }

  return { ask }
}
