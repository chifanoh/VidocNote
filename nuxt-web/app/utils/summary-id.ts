/**
 * 摘要的对外标识（后端发的 UUIDv7）：地址栏校验与日志短写。
 * 内部自增主键已经不出现在接口和链接里，所以数字形式的旧链接一律按无效处理。
 */
const UUID_V7 = /^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

/** 地址栏 / query 里的 id 是否合法（不合法就不必发请求，直接给「链接无效」） */
export function isSummaryId(raw: string | null | undefined): boolean {
  return typeof raw === 'string' && UUID_V7.test(raw)
}

/** 终端日志里只写前 8 位，整串 UUID 会把日志行撑满 */
export function shortId(id: string): string {
  return id.slice(0, 8)
}
