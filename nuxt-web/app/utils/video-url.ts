/**
 * 从粘贴文本里抽出链接，输入框只要其中那一个 URL。
*/
const URL_IN_TEXT = /https?:\/\/[^\s<>"'“”‘’\u3000-\u303f\u4e00-\u9fff\uff00-\uffef]+/i
const TRAILING_JUNK = /[.,;:!?…·—]+$/
const BRACKET_PAIRS: ReadonlyArray<readonly [string, string]> = [['(', ')'], ['[', ']'], ['{', '}']]
const BARE_DOMAIN = /^(?:www\.)?(?:[a-z0-9-]+\.)+[a-z]{2,24}(?::\d+)?(?:\/\S*)?$/i

function countOf(text: string, ch: string): number {
  return text.split(ch).length - 1
}

function stripUnbalancedClosers(url: string): string {
  let out = url
  for (const [open, close] of BRACKET_PAIRS) {
    while (out.endsWith(close) && countOf(out, close) > countOf(out, open)) {
      out = out.slice(0, -1)
    }
  }
  return out
}

function clean(url: string): string {
  return stripUnbalancedClosers(url.replace(/&amp;/g, '&').replace(TRAILING_JUNK, ''))
}

export function extractVideoUrl(raw: string | null | undefined): string {
  const text = (raw ?? '').trim()
  if (!text) return ''

  const found = URL_IN_TEXT.exec(text)
  if (found) return clean(found[0])

  return BARE_DOMAIN.test(text) ? `https://${clean(text)}` : text
}
