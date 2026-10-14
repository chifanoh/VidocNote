import type { OrderItem, PayFormResponse } from '~/types'

/** 订单与支付 API 封装 */
export function usePayment() {
  const { apiFetch } = useApi()

  async function createOrder(planCode: string): Promise<OrderItem> {
    return apiFetch<OrderItem>('/api/payments/orders', { method: 'POST', body: { planCode } })
  }

  async function orders(size = 20): Promise<OrderItem[]> {
    return apiFetch<OrderItem[]>(`/api/payments/orders?size=${size}`)
  }

  async function pay(outTradeNo: string): Promise<PayFormResponse> {
    return apiFetch<PayFormResponse>(`/api/payments/orders/${outTradeNo}/pay`, { method: 'POST' })
  }

  async function confirm(outTradeNo: string): Promise<OrderItem> {
    return apiFetch<OrderItem>(`/api/payments/orders/${outTradeNo}/confirm`, { method: 'POST' })
  }

  async function subscription(): Promise<{ active: boolean }> {
    return apiFetch<{ active: boolean }>('/api/payments/subscription')
  }

  return { createOrder, orders, pay, confirm, subscription }
}
