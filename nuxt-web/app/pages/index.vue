<template>
  <div>
    <!-- Hero -->
    <section class="mx-auto max-w-3xl py-16 text-center sm:py-20">
      <UPageHero 
      :ui="{ container: 'max-w-none px-0 py-0 sm:px-0 sm:py-0 lg:px-0 lg:py-0' }"
      title="粘贴视频链接，AI 帮你生成视频笔记"
      description="VidocNote 帮你把长视频浓缩成结构化详细摘要" />
    </section>

    <!-- 工具区 -->
    <section id="tool" class="mx-auto max-w-3xl pb-16">
      <UForm class="flex flex-col gap-3 sm:flex-row" @submit="onSubmit">
        <UInput v-model="url" type="url" size="lg" class="flex-1" leading-icon="i-material-symbols-link"
          placeholder="粘贴 bilibili 链接" @paste="onPaste" />
        <UButton type="submit" size="lg" icon="i-material-symbols-auto-awesome" :loading="submitting" label="生成摘要"
          class="shrink-0 justify-center" />
      </UForm>

      <!-- 执行进度终端 -->
      <ProgressTerminal :steps="steps" :command="command" class="mt-8" @clear="clearTerminal" />
    </section>

    <PageLogos />

    <Pricing />

    <Accordion />
  </div>
</template>

<script setup lang="ts">
import type { Summary, TerminalStep } from '~/types'
import { buildSummarySteps } from '~/utils/summary-steps'
import { extractVideoUrl } from '~/utils/video-url'
import { faqItems } from '~/utils/faq'

const { isLoggedIn } = useAuth()
const { openModal } = useAuthModal()
const { create, pollUntilDone } = useSummary()

// ---- SEO/GEO：落地页唯一 TDK + OG + 结构化数据 ----
const siteConfig = useSiteConfig()
useSeoMeta({
  title: 'Vidoc Note - AI 视频摘要生成器｜粘贴链接自动生成视频摘要',
  description: 'Vidoc Note 是免费的 AI 视频摘要生成工具：粘贴视频链接，AI 自动提炼视频核心内容，生成结构化详细摘要。历史记录自动保存，适合课程学习、会议回看与新闻速览。',
  keywords: '视频摘要,AI 视频摘要,视频摘要生成器,视频笔记,视频总结,在线视频摘要',
  robots: 'index, follow',
  ogTitle: 'Vidoc Note - 粘贴视频链接，AI 自动生成视频摘要',
  ogDescription: '免费的 AI 视频摘要生成器：粘贴链接，AI 自动生成结构化详细摘要',
  ogType: 'website',
  ogUrl: () => siteConfig.url,
  ogLocale: 'zh_CN',
  ogSiteName: 'Vidoc Note',
  twitterCard: 'summary'
})

useSchemaOrg([
  defineWebSite({
    name: 'Vidoc Note',
    description: '粘贴视频链接，AI 自动生成视频摘要',
    inLanguage: 'zh-CN'
  }),
  defineOrganization({
    name: 'Vidoc Note',
    url: () => siteConfig.url,
    logo: '/logo/logo-icon.svg'
  }),

  defineSoftwareApp({
    name: 'Vidoc Note',
    description: '免费的 AI 视频摘要生成器：粘贴 Bilibili 视频链接，自动生成结构化详细摘要',
    applicationCategory: 'MultimediaApplication',
    operatingSystem: 'Web',
    url: () => siteConfig.url,
    inLanguage: 'zh-CN'
  }),

  defineWebPage({
    '@type': 'FAQPage',
    mainEntity: faqItems.map(item => defineQuestion({
      name: item.question,
      acceptedAnswer: item.answer
    }))
  })
])

const url = ref('')

/** 链接提纯*/
function onPaste(event: ClipboardEvent) {
  const pasted = event.clipboardData?.getData('text') ?? ''
  const cleaned = extractVideoUrl(pasted)
  if (!cleaned || cleaned === pasted.trim()) return
  event.preventDefault()
  url.value = cleaned
}

const summary = ref<Summary | null>(null)
const submitting = ref(false)
const toast = useToast()

const router = useRouter()

// ---- 进度终端：常驻步骤流（后端只给步骤 key，文案与状态由前端渲染）----
const steps = ref<TerminalStep[]>([])
const command = ref('')
let lastStepKey: string | null = null

// 会话令牌：每次提交递增；清除终端或新提交会使旧会话失效（在途轮询自动停止）
let runSeq = 0

function onPoll(s: Summary) {
  summary.value = s
  if (s.progressStep) lastStepKey = s.progressStep
  steps.value = buildSummarySteps(s.status, s.progressStep ?? lastStepKey)
}

// 清除终端：清空步骤流与已粘贴的链接，并终止当前生成会话（解锁按钮，停止后续轮询）
function clearTerminal() {
  runSeq++
  submitting.value = false
  steps.value = []
  command.value = ''
  url.value = ''
  lastStepKey = null
}

async function onSubmit() {
  // 未登录优先弹登录框
  if (!isLoggedIn.value) {
    openModal()
    return
  }
  if (!url.value.trim()) {
    toast.add({ title: '请先粘贴视频链接', color: 'error', icon: 'i-material-symbols-error-outline', duration: 4000 })
    return
  }
  if (!/^https?:\/\//.test(url.value.trim())) {
    toast.add({ title: '链接需以 http(s):// 开头', color: 'error', icon: 'i-material-symbols-error-outline', duration: 4000 })
    return
  }

  const seq = ++runSeq
  submitting.value = true
  summary.value = null
  lastStepKey = null
  command.value = '字节娘正在努力生成中，客官稍安勿躁 (゜-゜)つロ ~'
  steps.value = buildSummarySteps('PENDING', null)
  try {
    const created = await create(url.value.trim())
    if (seq !== runSeq) return // 已被清除/新提交取代
    summary.value = created
    const terminal = await pollUntilDone(created.id, onPoll, () => seq !== runSeq)
    if (seq !== runSeq) return
    if (terminal && terminal.status === 'FAILED') {
      toast.add({ title: '生成失败', description: terminal.errorMessage ?? '请稍后重试', color: 'error', icon: 'i-material-symbols-error-outline', duration: 4000 })
    } else if (terminal && terminal.status === 'COMPLETED') {
      // 生成完成：新标签页打开阅读页（首页不再展示摘要）
      const target = `/summary?id=${terminal.id}`
      const tab = window.open(target, '_blank')
      if (tab) {
        tab.opener = null
      } else {
        // 浏览器把无手势的 window.open 当弹窗拦了：至少别卡住，退回当前页跳转
        await router.push(target)
      }
    }
  } catch (e: any) {
    if (seq !== runSeq) return
    if ((e?.statusCode ?? e?.response?.status) === 402) {
      toast.add({
        title: '积分不足',
        description: e?.data?.message ?? '当前积分不足以生成摘要',
        color: 'error',
        icon: 'i-material-symbols-error-outline',
        duration: 6000,
        actions: [{ label: '查看套餐', onClick: () => { void router.push('/dashboard/plans') } }]
      })
    } else if ((e?.statusCode ?? e?.response?.status) === 503) {
      // 该线路的线程池与排队都满（后端明确返回 503）：不是用户的错，别混进「创建失败」
      toast.add({
        title: '服务器繁忙',
        description: '服务器任务繁忙，请稍后重试',
        color: 'warning',
        icon: 'i-material-symbols-hourglass-top',
        duration: 5000
      })
    } else {
      toast.add({ title: '创建失败', description: e?.data?.message ?? '请稍后重试', color: 'error', icon: 'i-material-symbols-error-outline', duration: 4000 })
    }
    steps.value = []
    command.value = ''
  } finally {
    if (seq === runSeq) {
      submitting.value = false
    }
  }
}
</script>
