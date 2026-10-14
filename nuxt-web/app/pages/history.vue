<script setup lang="ts">
import type { SummaryListItem } from '~/types'
import type { AppErrorInfo } from '~/utils/api-error'
import { describeApiError } from '~/utils/api-error'
import { platformOf } from '~/utils/summary-meta'

// 历史记录是列表页，底部紧跟「已经到底啦~」，站点页脚在此处只是噪音（用户指定去掉）
definePageMeta({ middleware: 'auth', footer: false, headerNav: false })

useSeoMeta({
  title: '历史记录 - Vidoc Note 视频摘要管理',
  description: '查看和管理你的 Vidoc Note 视频摘要历史记录，随时回顾或删除。',
  robots: 'noindex, follow'
})

const { list, remove } = useSummary()
const { isLoggedIn } = useAuth()
const toast = useToast()

/** 每页条数（服务端会多取一条判断还有没有下一页） */
const PAGE_SIZE = 20
/** 滚动哨兵提前这么多像素就取下一页（等页面滚到底再请求会有明显空窗） */
const SENTINEL_MARGIN = 200

const summaries = ref<SummaryListItem[]>([])
const cursor = ref<string | null>(null)
const hasMore = ref(true)
const loading = ref(true)
const loadingMore = ref(false)
const error = ref<AppErrorInfo | null>(null)
const deletingId = ref<string | null>(null)

/** 列表底部的滚动哨兵：恒在 DOM 里，故 observer 只需挂一次 */
const sentinel = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null

/** 首屏骨架的容器，用来量「从它开始还剩多少高度」 */
const skeletonBox = ref<HTMLElement | null>(null)
/** 首屏骨架行数：按实际能放下几张算，写死条数在矮窗口会把页面顶出滚动条 */
const skeletonCount = ref(2)
/** 与 HistoryListSkeleton 的 `gap-4` 对齐 */
const SKELETON_GAP = 16
const SKELETON_MAX = 4

/** 骨架行数 = 骨架顶部到视口底部，扣掉它下面固定占位的哨兵与页面下边距后能放下的整卡数 */
function fitSkeletonCount() {
  const box = skeletonBox.value
  const card = box?.firstElementChild?.firstElementChild
  if (!box || !(card instanceof HTMLElement)) return
  const below = parseFloat(getComputedStyle(box).marginBottom || '0')
    + (sentinel.value?.offsetHeight ?? 0)
    + parseFloat(getComputedStyle(box.parentElement ?? box).paddingBottom || '0')
  const step = card.getBoundingClientRect().height + SKELETON_GAP
  const room = window.innerHeight - box.getBoundingClientRect().top - below
  skeletonCount.value = Math.min(SKELETON_MAX, Math.max(1, Math.floor((room + SKELETON_GAP) / step)))
}

/** 一次只允许一个在途请求（busy 不参与渲染，故用普通变量） */
let busy = false
/** 请求序号：reset 会打断在途翻页，靠它丢弃旧响应 */
let seq = 0

/** 每条记录在阅读页的地址，同时充当卡片与记录的对应键 */
const detailPath = (id: string) => `/summary?id=${id}`

/** to → 记录：UBlogPosts 的插槽只回传 post 本身（业务字段不在其中），
 *  往 post 里塞额外字段会作为 HTML 属性漏到 DOM，故用 to 反查。 */
const byPath = computed(() => new Map(summaries.value.map(s => [detailPath(s.id), s])))

function summaryOf(post: unknown): SummaryListItem | undefined {
  const to = (post as { to?: string } | null)?.to
  return to ? byPath.value.get(to) : undefined
}

/** 卡片内部布局：≤768px 图在上、信息在下；769px 起缩略图占约 1/3 排左，lg 起切 grid 1fr:2fr。
 *  - 断点写 min-[769px] 而不是 md(768)：用户指定的是 max-width:768px（含 768），
 *    md: 是 min-width:768px，正好差 1px，会让 iPad 竖屏(768) 落到横向；
 *  - variant 必须挂在 post 上（放进 ui 里组件不认），naked 关掉组件自带 bg/ring
 *    与整卡灰底悬停，改用本站卡片配方（纯白 + border-line + shadow-card）；
 *  - lg 起缩略图与信息列都 self-stretch（行高由缩略图决定，两者同高）：徽章贴顶与
 *    视频上沿齐平，up · 时间靠 mt-auto 沉到视频下沿，信息列不会越过视频高度。 */
const POST_UI = {
  root: 'flex flex-col gap-4 border border-line bg-page p-4 shadow-card min-[769px]:flex-row min-[769px]:items-start sm:gap-5 sm:p-5 lg:grid lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)] lg:items-center lg:gap-x-6',
  // shadow-none：naked 变体会给 header 带 shadow-lg，这里去掉
  header: 'w-full shrink-0 overflow-hidden rounded shadow-none min-[769px]:w-1/3 lg:w-full lg:self-stretch',
  // lg:self-stretch 盖掉组件 horizontal 变体给容器的 lg:items-center（否则整列按内容高居中）
  body: 'p-0 sm:p-0 lg:self-stretch lg:px-0 lg:py-0',
  // 做成 flex 行是为了让删除按钮落在标题第一行右侧（≤768）；标题用 group-hover 变蓝，
  // 因为整卡被 to 渲染的 absolute inset-0 链接覆盖，鼠标在卡片任意处都该变蓝
  title: 'flex items-start gap-2 text-lg font-semibold text-ink transition-colors group-hover/blog-post:text-hover',
  // lg:mt-auto 顶开剩余空间把「up · 时间」压到底部（auto 外边距优先于组件的 justify-center）
  description: 'mt-1 flex min-w-0 items-center gap-1 text-xs text-ink-4 lg:mt-auto',
  badge: 'shrink-0',
  // 删除按钮：769 起浮在右上角（absolute 才能脱离 grid 流、z-10 才盖得住整卡链接）；
  // ≤768 那个在 #title 插槽里，与标题第一行同行
  footer: 'absolute right-3 top-3 z-10 hidden items-center min-[769px]:flex'
}

const posts = computed(() =>
  summaries.value.map(s => ({
    title: s.title || '未命名视频',
    // up + 时间同行：空字符串作者要按缺失处理，否则会渲染出「 · 时间」
    description: `${s.author?.trim() || '未知UP主'} · ${formatTime(s.createdAt)}`,
    image: s.thumbnailUrl ?? undefined,
    badge: { label: platformOf(s.videoUrl), color: 'neutral' as const, variant: 'soft' as const },
    to: detailPath(s.id),
    // 阅读页在新标签页打开（UBlogPost 的 target 会透传给 NuxtLink）
    target: '_blank' as const,
    variant: 'naked' as const,
    ui: POST_UI
  }))
)

/**
 * 取一页：reset 为 true 时回到第一页（首次进入 / 登录后重试）。
 * 在途请求靠 seq 作废：reset 会打断进行中的翻页，那一页的响应不能覆盖新数组。
 */
async function load(reset = false) {
  if (busy && !reset) return
  // 没有下一页时 cursor 是 null，此处放行会重复拉第一页
  if (!reset && !hasMore.value) return
  const mine = ++seq
  busy = true
  let ok = false
  if (reset) {
    loading.value = true
    error.value = null
    cursor.value = null
    hasMore.value = true
  } else {
    loadingMore.value = true
  }
  try {
    const page = await list(reset ? null : cursor.value, PAGE_SIZE)
    if (mine !== seq) return
    summaries.value = reset ? page.items : [...summaries.value, ...page.items]
    // hasMore 为真却缺 nextCursor 属于协议异常，按「没有下一页」处理，避免拿 null 再拉第一页
    hasMore.value = page.hasMore && !!page.nextCursor
    cursor.value = page.nextCursor ?? null
    ok = true
  } catch (e: unknown) {
    if (mine !== seq) return
    // 首屏失败走整页 AppError；翻页失败不打扰用户，静默停住（向下滚开再滚回来会自然重试）
    if (reset) error.value = describeApiError(e)
  } finally {
    if (mine === seq) {
      busy = false
      loading.value = false
      loadingMore.value = false
      await nextTick()
      // 只有这一页成功才继续补拉：失败后哨兵仍在视口内，直接再判断会原地打转
      if (ok) loadMoreIfSentinelVisible()
    }
  }
}

/** IntersectionObserver 只在相交状态变化时回调：首屏没填满时哨兵一直在视口内，
 *  需要每次加载完成后主动补一次判断，否则会停在那里不再续拉。 */
function loadMoreIfSentinelVisible() {
  if (!hasMore.value || busy) return
  const el = sentinel.value
  if (el && el.getBoundingClientRect().top <= window.innerHeight + SENTINEL_MARGIN) void load()
}

async function onDelete(summary: SummaryListItem) {
  if (deletingId.value) return
  deletingId.value = summary.id
  try {
    await remove(summary.id)
    summaries.value = summaries.value.filter(s => s.id !== summary.id)
  } catch {
    // 单条删除失败不该顶掉整页列表，与阅读页一致走 toast
    toast.add({ title: '删除失败，请稍后重试', color: 'error', icon: 'i-material-symbols-error-outline', duration: 4000 })
  } finally {
    deletingId.value = null
  }
}

/** 删除按钮在 footer 插槽里只拿得到 post，先反查回整条记录 */
function deleteByPost(post: unknown) {
  const summary = summaryOf(post)
  if (summary) onDelete(summary)
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleString('zh-CN', { hour12: false })
}

// 401 弹登录框后，登录成功要自动重试：弹窗跳回同一路径不会重挂载页面
watch(isLoggedIn, (loggedIn) => {
  if (loggedIn && error.value?.needLogin) void load(true)
})

// 列表接口只返回已完成的任务（失败的不留存、进行中的不展示），按游标续拉
onMounted(() => {
  observer = new IntersectionObserver((entries) => {
    if (entries.some(entry => entry.isIntersecting)) void load()
  }, { rootMargin: `${SENTINEL_MARGIN}px` })
  if (sentinel.value) observer.observe(sentinel.value)
  void load(true)
  void nextTick(fitSkeletonCount)
  window.addEventListener('resize', fitSkeletonCount)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  window.removeEventListener('resize', fitSkeletonCount)
})
</script>

<template>
  <div class="mx-auto max-w-4xl py-12">
    <div class="mb-8">
      <h1 class="flex items-center gap-2 text-3xl font-semibold tracking-normal text-ink">
        <UIcon name="i-material-symbols-schedule" class="size-8 shrink-0" />
        历史记录
      </h1>
    </div>

    <AppError v-if="error" :info="error" compact class="mb-4" />

    <div v-else-if="loading" ref="skeletonBox" class="mb-4">
      <HistoryListSkeleton :count="skeletonCount" />
    </div>

    <!-- 空态只在确实没有下一页时出现：删光当前页但后面还有记录时，列表容器要留在原地续拉 -->
    <div v-else-if="summaries.length === 0 && !hasMore" class="py-16 text-center">
      <UIcon name="i-material-symbols-inbox" class="mx-auto mb-3 size-10 text-ink-4" />
      <p class="text-ink-4">还没有摘要记录，去首页生成第一条吧</p>
      <UButton to="/" class="mt-4" icon="i-material-symbols-auto-awesome" label="去生成摘要" />
    </div>

    <!-- 竖向列表：每条 = 左缩略图（约 1/3）+ 右信息（平台 / 标题 / up · 时间） -->
    <UBlogPosts v-else orientation="vertical" :posts="posts" :ui="{ base: 'gap-4 lg:gap-y-4' }">
      <!-- 缩略图：无图时用带边框的占位块，保证左列不塌 -->
      <template #header="{ post }">
        <img
          v-if="typeof post.image === 'string'"
          :src="post.image"
          :alt="post.title ?? '视频缩略图'"
          referrerpolicy="no-referrer"
          class="h-full w-full object-cover object-top transition-transform duration-200 group-hover/blog-post:scale-110"
        />
        <div v-else class="flex h-full w-full items-center justify-center text-ink-4">
          <UIcon name="i-material-symbols-play-circle" class="size-6" />
        </div>
      </template>

      <!-- 标题 -->
      <template #title="{ post }">
        <span class="line-clamp-2 min-w-0 flex-1">{{ post.title }}</span>
        <UButton
          color="error"
          variant="ghost"
          size="sm"
          icon="i-material-symbols-delete"
          aria-label="删除"
          class="relative z-10 -mr-1.5 -mt-0.5 shrink-0 min-[769px]:hidden"
          :loading="deletingId === summaryOf(post)?.id"
          @click="deleteByPost(post)"
        />
      </template>

      <!-- up + 时间 -->
      <template #description="{ post }">
        <span class="up-badge size-4 shrink-0" aria-hidden="true"></span>
        <span class="truncate">{{ post.description }}</span>
      </template>

      <template #footer="{ post }">
        <UButton
          color="error"
          variant="ghost"
          size="sm"
          icon="i-material-symbols-delete"
          aria-label="删除"
          class="lg:opacity-0 lg:transition-opacity lg:group-hover/blog-post:opacity-100 lg:focus-visible:opacity-100"
          :loading="deletingId === summaryOf(post)?.id"
          @click="deleteByPost(post)"
        />
      </template>
    </UBlogPosts>

    <!-- 滚动哨兵 + 底部状态：恒在 DOM 里（observer 只挂一次） -->
    <div ref="sentinel" class="pt-6">
      <HistoryListSkeleton v-if="loadingMore" :count="1" />
      <p v-else-if="summaries.length > 0 && !hasMore" class="text-center text-sm text-ink-4">已经到底啦~</p>
    </div>
  </div>
</template>

<style scoped>
/* UP 徽章：public/logo 的方块 "UP!" 图形。用 mask + currentColor 才能跟住 meta 文字色，
   <img> 加载的 SVG 里 currentColor 是另一个文档的默认色（黑），不继承页面颜色 */
.up-badge {
  display: inline-block;
  background-color: currentColor;
  -webkit-mask-image: url("/logo/up-text-button.svg");
  mask-image: url("/logo/up-text-button.svg");
  -webkit-mask-position: center;
  mask-position: center;
  -webkit-mask-repeat: no-repeat;
  mask-repeat: no-repeat;
  -webkit-mask-size: contain;
  mask-size: contain;
}
</style>
