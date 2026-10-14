/**
 * 首页常见问题：Accordion 区块与首页 FAQPage 结构化数据共用这一份（SEO优化）
 */
export interface FaqItem {
  question: string
  answer: string
}

export const faqItems: FaqItem[] = [
  {
    question: 'Vidoc Note 如何生成视频摘要？',
    answer: '粘贴视频链接后（整个过程通常需要十几秒到几十秒）：获取视频信息 → 抓取视频的字幕文本 → 交给 AI 生成结构化详细摘要。'
  },
  {
    question: '支持哪些视频平台？',
    answer: '目前只支持bilibili，其它平台还在开发中。'
  },
  {
    question: '视频没有字幕还能生成摘要吗？',
    answer: '不能，Vidoc Note 依赖LLM[大语言模型]，目前暂不支持ASR[自动语音识别]。'
  },
  {
    question: '摘要会保存在哪里？',
    answer: '摘要与视频信息都保存在你的账号下，可以在「历史记录」中随时查看或删除，其他用户无法访问。'
  },
  {
    question: '很长的视频会怎么处理？',
    answer: '单条视频时长上限为 3 小时，超过会被直接拒绝且不扣除积分。3 小时以内的视频，字幕通常不会触及处理上限；万一触及，摘要会标注「内容已截断」。'
  }
]
