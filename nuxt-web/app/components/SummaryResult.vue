<script setup lang="ts">
import type { Summary } from '~/types'
import { formatDuration } from '~/utils/summary-meta'

/**
 * 首页摘要结果展示卡：仅承载 COMPLETED 记录。
 * 视频信息 + 完整正文 + 复制 / 删除本条；关闭与删除均通过事件交由父级复位。
 */
const props = defineProps<{ summary: Summary }>()
const emit = defineEmits<{ close: []; deleted: [] }>()

const { remove } = useSummary()
const { copy } = useCopyText()
const toast = useToast()
const deleting = ref(false)

const metaLine = computed(() => {
  const parts: string[] = []
  if (props.summary.author) parts.push(props.summary.author)
  const duration = formatDuration(props.summary.duration)
  if (duration) parts.push(duration)
  return parts.join(' · ')
})

function onCopy() {
  // 复制的是 Markdown 原文（带语法标记），与展示层的渲染无关
  void copy(props.summary.summaryText ?? '')
}

async function onDelete() {
  if (deleting.value) return
  deleting.value = true
  try {
    await remove(props.summary.id)
    toast.add({ title: '已删除该摘要', color: 'success', icon: 'i-material-symbols-check-circle', duration: 3000 })
    emit('deleted')
  } catch {
    toast.add({ title: '删除失败，请稍后重试', color: 'error', icon: 'i-material-symbols-error-outline', duration: 4000 })
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <div class="overflow-hidden rounded-lg border border-line bg-page shadow-card">
    <!-- 头部：缩略图 + 标题/元信息 + 关闭 -->
    <div class="flex items-start gap-4 p-5 sm:p-6">
      <img
        v-if="summary.thumbnailUrl"
        :src="summary.thumbnailUrl"
        :alt="summary.title || '视频缩略图'"
        referrerpolicy="no-referrer"
        class="h-16 w-28 shrink-0 rounded object-cover"
      />
      <div v-else class="flex h-16 w-28 shrink-0 items-center justify-center rounded border border-line bg-page text-ink-4">
        <UIcon name="i-material-symbols-play-circle" class="size-6" />
      </div>

      <div class="min-w-0 flex-1">
        <h3 class="truncate font-semibold text-ink">{{ summary.title || '未命名视频' }}</h3>
        <p class="mt-1 truncate text-sm text-ink-4">{{ metaLine || '未知来源' }}</p>
      </div>

      <UButton
        icon="i-material-symbols-close"
        color="neutral"
        variant="ghost"
        size="xs"
        aria-label="关闭摘要"
        class="-mr-1 -mt-1 shrink-0"
        @click="emit('close')"
      />
    </div>

    <!-- 正文区 -->
    <div class="px-5 pb-5 sm:px-6 sm:pb-6">
      <UAlert
        v-if="summary.truncated"
        color="warning"
        variant="subtle"
        title="内容过长已截断，摘要可能不完整"
        class="mb-4"
      />
      <SummaryMarkdown :value="summary.summaryText" />

      <div class="mt-5 flex items-center gap-1 border-t border-line pt-4">
        <UButton
          color="neutral"
          variant="ghost"
          size="sm"
          icon="i-material-symbols-content-copy"
          label="复制"
          class="text-ink-3 hover:text-hover"
          @click="onCopy"
        />
        <UButton
          color="neutral"
          variant="ghost"
          size="sm"
          icon="i-material-symbols-menu-book"
          label="阅读视图"
          class="text-ink-3 hover:text-hover"
          :to="`/summary/${summary.id}`"
        />
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
    </div>
  </div>
</template>
