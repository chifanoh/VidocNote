/** 秒数 → `m:ss` / `h:mm:ss`；空值或非法值返回空串 */
export function formatDuration(seconds: number | null | undefined): string {
  if (!seconds || seconds <= 0) return ''
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  const mm = String(m).padStart(2, '0')
  const ss = String(s).padStart(2, '0')
  return h > 0 ? `${h}:${mm}:${ss}` : `${m}:${ss}`
}

/** 秒数 → `X 小时 Y 分钟`；用于累计量展示（不逐秒），空值返回 `0 分钟` */
export function formatTotalDuration(seconds: number | null | undefined): string {
  if (!seconds || seconds <= 0) return '0 分钟'
  const totalMinutes = Math.round(seconds / 60)
  const h = Math.floor(totalMinutes / 60)
  const m = totalMinutes % 60
  return h > 0 ? `${h} 小时 ${m} 分钟` : `${m} 分钟`
}

/** 按视频链接的 host 判定平台名（Summary 无 platform 字段，阅读页左栏展示用） */
export function platformOf(videoUrl: string | null | undefined): string {
  if (!videoUrl) return '未知平台'
  let host = ''
  try {
    host = new URL(videoUrl).hostname.toLowerCase()
  } catch {
    return '未知平台'
  }
  const rules: Array<[string[], string]> = [
    [['bilibili.com', 'b23.tv', 'bilibili.tv'], 'Bilibili'],
    [['youtube.com', 'youtu.be'], 'YouTube'],
    [['douyin.com', 'iesdouyin.com'], '抖音'],
    [['kuaishou.com'], '快手'],
    [['weibo.com', 'weibo.cn'], '微博'],
    [['xiaohongshu.com', 'xhslink.com'], '小红书'],
    [['zhihu.com'], '知乎']
  ]
  for (const [domains, name] of rules) {
    if (domains.some(d => host === d || host.endsWith(`.${d}`))) return name
  }
  return host.replace(/^www\./, '') || '未知平台'
}
