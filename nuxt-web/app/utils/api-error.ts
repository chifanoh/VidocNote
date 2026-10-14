/**
 * 全站错误的唯一措辞来源：页面只负责渲染（AppError）
 */

/** 错误文案 */
export interface AppErrorInfo {
  statusCode?: number
  statusMessage: string
  /** 第二行说明；不写就只渲染错误码 + 标题 */
  message?: string
  needLogin: boolean
}

/** 401：未登录或登录已过期 */
const NEED_LOGIN: AppErrorInfo = {
  statusCode: 401,
  statusMessage: '请先登录后查看',
  needLogin: true
}

/** 403 / 404：已删除 */
const GONE: AppErrorInfo = {
  statusCode: 404,
  statusMessage: '记录不存在或已删除',
  needLogin: false
}

/** 5xx： 网络不通 */
function unavailable(statusCode?: number): AppErrorInfo {
  return { statusCode, statusMessage: '服务暂时不可用', needLogin: false }
}

/** 429：nginx 限流 */
const RATE_LIMITED: AppErrorInfo = {
  statusCode: 429,
  statusMessage: '操作过于频繁，请稍后再试',
  needLogin: false
}

/** 摘要非完成态：不暴露「失败 / 进行中」，只给中立空态 */
export const EMPTY_SUMMARY: AppErrorInfo = {
  statusMessage: '该摘要暂无可展示内容',
  needLogin: false
}

/** 地址里没有可用的摘要编号 */
export const INVALID_LINK: AppErrorInfo = {
  statusMessage: '链接无效',
  needLogin: false
}

/** 账单列表为空（不是错误） */
export const EMPTY_ORDERS: AppErrorInfo = {
  statusMessage: '暂无账单记录',
  needLogin: false
}

function statusOf(e: unknown): number {
  const err = e as { statusCode?: number; status?: number; response?: { status?: number } } | null
  return err?.statusCode ?? err?.status ?? err?.response?.status ?? 0
}

/** 接口错误 → 用户看到的文案（401 要登录、403/404 一律当不存在、其余当服务故障） */
export function describeApiError(e: unknown): AppErrorInfo {
  const code = statusOf(e)
  if (code === 401) return NEED_LOGIN
  if (code === 403 || code === 404) return GONE
  if (code === 429) return RATE_LIMITED
  return unavailable(code || undefined)
}

/** 页面级错误（路由 / 组件抛的未处理异常）→ 文案；这里的 404 是「页面不存在」，不是某条记录 */
export function describePageError(statusCode?: number): AppErrorInfo {
  if (statusCode === 401) return NEED_LOGIN
  if (statusCode === 403) {
    return { statusCode: 403, statusMessage: '没有访问权限', needLogin: false }
  }
  if (statusCode === 404) {
    return {
      statusCode: 404,
      statusMessage: '页面未找到',
      message: '灰常抱歉，您要查找的页面不存在~',
      needLogin: false
    }
  }
  return unavailable(statusCode)
}
