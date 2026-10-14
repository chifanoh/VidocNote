<script setup lang="ts">
import type { Summary, TocLink } from '~/types'
import type { ComponentPublicInstance } from 'vue'
import { describeApiError, EMPTY_SUMMARY, INVALID_LINK } from '~/utils/api-error'
import type { AppErrorInfo } from '~/utils/api-error'
import { isSummaryId } from '~/utils/summary-id'

/** 三栏阅读视图：桌面整页零滚动（只有中栏内部滚），窄屏自然流 + 目录抽屉
 *  headerNav: false —— 阅读页头部只留 Logo 与账号入口，不显示主导航 */
definePageMeta({ layout: 'reader', footer: false, headerNav: false })

const route = useRoute()
const router = useRouter()
const toast = useToast()
const { get, remove } = useSummary()
const { isLoggedIn } = useAuth()
const { openModal } = useAuthModal()
const { copy } = useCopyText()

const summaryId = computed(() => {
  const raw = Array.isArray(route.query.id) ? route.query.id[0] : route.query.id
  return raw && isSummaryId(raw) ? raw : null
})

const summary = ref<Summary | null>(null)
const loading = ref(true)
const note = ref<AppErrorInfo | null>(null)
const deleting = ref(false)

const tocLinks = ref<TocLink[]>([])
const activeId = ref<string | null>(null)
const tocOpen = ref(false)
const scrollEl = ref<HTMLElement | null>(null)
const tocTrigger = ref<ComponentPublicInstance | null>(null)

const platform = computed(() => platformOf(summary.value?.videoUrl))
const duration = computed(() => formatDuration(summary.value?.duration))

useSeoMeta({
  title: () => (summary.value?.title ? `${summary.value.title} - 摘要阅读 - Vidoc Note` : '摘要阅读 - Vidoc Note'),
  robots: 'noindex, nofollow'
})

async function load() {
  const id = summaryId.value
  tocLinks.value = []
  activeId.value = null
  if (id === null) {
    summary.value = null
    note.value = INVALID_LINK
    loading.value = false
    return
  }
  loading.value = true
  note.value = null
  try {
    const s = await get(id)
    if (s.status === 'COMPLETED') {
      summary.value = s
    } else {
      // 失败 / 进行中一律不暴露状态（失败的任务后端已删除），只给中立空态
      summary.value = null
      note.value = EMPTY_SUMMARY
    }
  } catch (e: unknown) {
    summary.value = null
    note.value = describeApiError(e)
    if (note.value.needLogin) openModal(route.fullPath)
  } finally {
    loading.value = false
  }
}

watch(summaryId, load)
// 401 弹登录框后，登录成功要自动重试：弹窗跳回同一路径不会重挂载页面
watch(isLoggedIn, (loggedIn) => {
  if (loggedIn && note.value?.needLogin) void load()
})
onMounted(load)

// ---- 目录锚点：在中栏滚动容器内定位，整页不动 ----
function scrollToHeading(id: string) {
  const root = scrollEl.value
  if (!root) return
  const target = root.querySelector<HTMLElement>(`[id="${CSS.escape(id)}"]`)
  target?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  activeId.value = id
  tocOpen.value = false
}

/** 窄屏点目录会关抽屉，reka 随之把焦点还给触发按钮（FocusScope 与 DialogContent 各一次），
 *  而 focus() 默认会把该按钮滚进视口 → 刚跳到的锚点被拉回顶部。这里自己还焦点且禁掉滚动。 */
function onTocCloseAutoFocus(event: Event) {
  event.preventDefault()
  const trigger = tocTrigger.value?.$el
  if (trigger instanceof HTMLElement) trigger.focus({ preventScroll: true })
}

const tocContentProps = { onCloseAutoFocus: onTocCloseAutoFocus }

// ---- 滚动高亮：观察中栏内的标题，取当前最靠上的可见项 ----
let observer: IntersectionObserver | null = null

function setupSpy() {
  observer?.disconnect()
  observer = null
  const root = scrollEl.value
  if (!root) return
  const headings = Array.from(root.querySelectorAll<HTMLElement>('h1[id], h2[id], h3[id], h4[id]'))
  if (!headings.length) return
  activeId.value = headings[0]!.id
  observer = new IntersectionObserver(
    (entries) => {
      const visible = entries
        .filter(entry => entry.isIntersecting)
        .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)
      if (visible.length) activeId.value = visible[0]!.target.id
    },
    { root, rootMargin: '-10% 0px -70% 0px' }
  )
  headings.forEach(h => observer!.observe(h))
}

function onParsed(links: TocLink[]) {
  tocLinks.value = links
  if (links.length) nextTick(setupSpy)
}

onBeforeUnmount(() => observer?.disconnect())

function onCopy() {
  // 复制的是 Markdown 原文（带语法标记），与展示层渲染无关
  void copy(summary.value?.summaryText ?? '')
}

async function onDelete() {
  const s = summary.value
  if (!s || deleting.value) return
  deleting.value = true
  try {
    await remove(s.id)
    toast.add({ title: '已删除该摘要', color: 'success', icon: 'i-material-symbols-check-circle', duration: 3000 })
    await router.push('/history')
  } catch {
    toast.add({ title: '删除失败，请稍后重试', color: 'error', icon: 'i-material-symbols-error-outline', duration: 4000 })
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <div class="lg:fixed lg:inset-x-0 lg:bottom-0 lg:top-[var(--ui-header-height)]">
    <div class="mx-auto h-full max-w-[100rem] px-6 py-6 lg:overflow-hidden">
      <!-- 加载态：与完成态同构的三栏，避免加载完成后跳版 -->
      <SummaryReaderSkeleton v-if="loading" />

      <!-- 非完成态：单列居中 -->
      <div v-else-if="!summary" class="mx-auto max-w-3xl">
        <AppError v-if="note" :info="note" />
      </div>

      <!-- 完成态：三栏（窄屏纵向堆叠：视频信息 → 摘要） -->
      <div v-else class="flex h-full flex-col gap-6 lg:flex-row">
        <!-- 左：视频信息 -->
        <aside class="w-full shrink-0 lg:w-72 lg:overflow-y-auto">
          <div class="rounded-lg border border-line bg-page p-5 shadow-card">
            <img
              v-if="summary.thumbnailUrl"
              :src="summary.thumbnailUrl"
              :alt="summary.title || '视频缩略图'"
              referrerpolicy="no-referrer"
              class="aspect-video w-full rounded object-cover"
            />
            <div v-else class="flex aspect-video w-full items-center justify-center rounded border border-line bg-page text-ink-4">
              <UIcon name="i-material-symbols-play-circle" class="size-8" />
            </div>

            <h1 class="mt-4 text-base font-semibold leading-6 text-ink">
              {{ summary.title || '未命名视频' }}
            </h1>

            <dl class="mt-3 space-y-2 text-sm">
              <div class="flex gap-2">
                <dt class="shrink-0 text-ink-5">平台：</dt>
                <dd class="min-w-0 truncate text-ink-3">{{ platform }}</dd>
              </div>
              <div v-if="summary.author" class="flex gap-2">
                <dt class="shrink-0 text-ink-5">作者：</dt>
                <dd class="min-w-0 truncate text-ink-3">{{ summary.author }}</dd>
              </div>
              <div v-if="duration" class="flex gap-2">
                <dt class="shrink-0 text-ink-5">时长：</dt>
                <dd class="text-ink-3">{{ duration }}</dd>
              </div>
            </dl>

            <div v-if="summary.truncated" class="mt-4 flex flex-wrap items-center gap-2">
              <UBadge color="warning" variant="soft" label="内容已截断" />
            </div>

            <a
              :href="summary.videoUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="mt-4 flex items-center gap-1 text-xs text-ink-4 hover:text-hover hover:underline"
            >
              <UIcon name="i-material-symbols-open-in-new" class="size-4 shrink-0" />
              <span class="truncate">查看原视频</span>
            </a>
          </div>
        </aside>

        <!-- 中：摘要（唯一滚动区） -->
        <div ref="scrollEl" class="min-w-0 flex-1 lg:overflow-y-auto">
          <div class="rounded-lg border border-line bg-page p-6 shadow-card">
            <div class="mb-4 flex flex-wrap items-center gap-1 border-b border-line pb-4">
              <UButton
                color="neutral"
                variant="ghost"
                size="sm"
                icon="i-material-symbols-content-copy"
                label="复制"
                class="text-ink-3 hover:text-hover"
                @click="onCopy"
              />
              <!-- 窄屏目录：折叠成抽屉（xl 起右栏常驻，按钮隐藏） -->
              <UDrawer v-model:open="tocOpen" direction="right" title="文章目录" :content="tocContentProps">
                <UButton
                  ref="tocTrigger"
                  color="neutral"
                  variant="ghost"
                  size="sm"
                  icon="i-material-symbols-list"
                  label="目录"
                  class="text-ink-3 hover:text-hover xl:hidden"
                  :disabled="!tocLinks.length"
                />
                <template #body>
                  <SummaryToc
                    v-if="tocLinks.length"
                    :links="tocLinks"
                    :active-id="activeId"
                    @select="scrollToHeading"
                  />
                  <p v-else class="text-sm text-ink-4">本摘要没有可跳转的小节</p>
                </template>
              </UDrawer>
              <UButton
                color="error"
                variant="ghost"
                size="sm"
                icon="i-material-symbols-delete"
                label="删除"
                class="ms-auto"
                :loading="deleting"
                @click="onDelete"
              />
            </div>

            <UAlert
              v-if="summary.truncated"
              color="warning"
              variant="subtle"
              title="内容过长已截断，摘要可能不完整"
              class="mb-4"
            />

            <SummaryMarkdown :value="summary.summaryText" @parsed="onParsed" />
          </div>
        </div>

        <!-- 右：文章目录（xl 起常驻） -->
        <nav class="hidden w-56 shrink-0 xl:block xl:overflow-y-auto">
          <div class="rounded-lg border border-line bg-page p-4 shadow-card">
            <p class="mb-3 text-lg font-semibold text-ink">文章目录</p>
            <SummaryToc
              v-if="tocLinks.length"
              :links="tocLinks"
              :active-id="activeId"
              @select="scrollToHeading"
            />
            <p v-else class="text-sm text-ink-4">本摘要无目录</p>
          </div>
        </nav>
      </div>
    </div>
  </div>
</template>
