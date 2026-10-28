export interface User {
  id: number
  email: string
  createdAt: string
}

export type SummaryStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface Summary {
  /** 对外标识：UUIDv7 字符串，内部自增主键不出现在接口里 */
  id: string
  videoUrl: string
  title: string | null
  author: string | null
  thumbnailUrl: string | null
  duration: number | null
  status: SummaryStatus
  /** 处理中的步骤 key（见 utils/summary-steps.ts），终态/旧数据为 null */
  progressStep: string | null
  summaryText: string | null
  errorMessage: string | null
  truncated: boolean
  createdAt: string
}

export interface AgentChatResponse {
  answer: string
}

/** 历史列表项：列表接口只回卡片要用的字段，不含 summaryText / errorMessage 等大字段 */
export interface SummaryListItem {
  id: string
  videoUrl: string
  title: string | null
  author: string | null
  thumbnailUrl: string | null
  duration: number | null
  createdAt: string
}

/** 游标分页响应：nextCursor 缺省表示没有下一页（后端只输出非 null 字段） */
export interface CursorPage<T> {
  items: T[]
  nextCursor?: string | null
  hasMore: boolean
}

/** 进度终端的步骤状态：待执行 / 进行中（转圈）/ 已完成（对勾）/ 失败（叉） */
export type TerminalStepState = 'pending' | 'active' | 'done' | 'failed'

export interface TerminalStep {
  key: string
  label: string
  state: TerminalStepState
}

/** Markdown 目录项：@nuxtjs/mdc 的 parseMarkdown 产出，id 与正文标题 id 一致 */
export interface TocLink {
  id: string
  text: string
  depth: number
  children?: TocLink[]
}

/** 积分事由 key（后端只发 key，中文文案见 utils/points.ts） */
export type PointsReason = 'CONSUME' | 'REFUND' | 'PURCHASE'

export interface PointsSummary {
  balance: number
}

export interface PointsLedgerItem {
  createdAt: string
  reason: PointsReason
  /** 正数为发放、负数为消耗 */
  delta: number
  /** 消耗 / 退回关联的摘要对外标识，可能为空 */
  summaryId: string | null
}

export type OrderStatus = 'PENDING' | 'PAID' | 'CLOSED'

export interface OrderItem {
  /** 商户订单号，同时是对外订单标识 */
  outTradeNo: string
  planCode: string
  planTitle: string
  /** 金额以分计 */
  amount: number
  status: OrderStatus
  createdAt: string
  paidAt: string | null
}

export interface PayFormResponse {
  outTradeNo: string
  /** 支付宝返回的自动提交表单 HTML */
  formHtml: string
}
