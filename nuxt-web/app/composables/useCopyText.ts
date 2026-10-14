/**
 * 复制文本到剪贴板：优先 Clipboard API，失败降级到隐藏 textarea + execCommand。
 * 复制的是传入的**原始文本**——摘要场景即带语法标记的 Markdown 原文，与展示层的渲染无关。
 */
export function useCopyText() {
  const toast = useToast()

  async function copy(text: string, successTitle = '已复制摘要'): Promise<boolean> {
    if (!text) {
      toast.add({ title: '没有可复制的内容', color: 'error', icon: 'i-material-symbols-error-outline', duration: 4000 })
      return false
    }
    const notifySuccess = () =>
      toast.add({ title: successTitle, color: 'success', icon: 'i-material-symbols-check-circle', duration: 3000 })
    try {
      await navigator.clipboard.writeText(text)
      notifySuccess()
      return true
    } catch {
      // 降级：非安全上下文 / 旧浏览器
      try {
        const ta = document.createElement('textarea')
        ta.value = text
        ta.setAttribute('readonly', '')
        ta.className = 'fixed left-[-9999px] top-0 opacity-0'
        document.body.appendChild(ta)
        ta.select()
        document.execCommand('copy')
        document.body.removeChild(ta)
        notifySuccess()
        return true
      } catch {
        toast.add({ title: '复制失败，请手动选择复制', color: 'error', icon: 'i-material-symbols-error-outline', duration: 4000 })
        return false
      }
    }
  }

  return { copy }
}
