<script setup lang="ts">
const props = defineProps<{
  summaryId: string
  title: string | null
}>()

interface Message {
  role: 'user' | 'assistant'
  content: string
}

const SUGGESTIONS = [
  '这段视频的核心观点是什么？',
  '总结一下视频里的关键数据和结论',
  '视频中提到了哪些具体案例？'
]

const { ask } = useAgentChat()
const open = ref(false)
const question = ref('')
const loading = ref(false)
const error = ref('')
const messages = ref<Message[]>([])
const messageList = ref<HTMLElement | null>(null)
const input = ref<HTMLTextAreaElement | null>(null)

async function scrollToBottom() {
  await nextTick()
  if (messageList.value) {
    messageList.value.scrollTop = messageList.value.scrollHeight
  }
}

async function resizeInput() {
  await nextTick()
  if (!input.value) return
  input.value.style.height = 'auto'
  input.value.style.height = `${Math.min(input.value.scrollHeight, 112)}px`
}

async function send() {
  const value = question.value.trim()
  if (!value || loading.value) return

  question.value = ''
  await resizeInput()
  error.value = ''
  messages.value.push({ role: 'user', content: value })
  loading.value = true
  await scrollToBottom()
  try {
    const response = await ask(props.summaryId, value)
    messages.value.push({ role: 'assistant', content: response.answer })
  } catch (e: any) {
    error.value = e?.data?.message ?? '回答失败，请稍后重试'
  } finally {
    loading.value = false
    await scrollToBottom()
    input.value?.focus()
  }
}

function askSuggestion(value: string) {
  question.value = value
  void send()
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    void send()
  }
}

watch(open, async (value) => {
  if (value) {
    await nextTick()
    input.value?.focus()
    await scrollToBottom()
  }
})
</script>

<template>
  <UButton
    color="primary"
    variant="solid"
    size="xl"
    icon="i-material-symbols-auto-awesome"
    aria-label="向 AI 询问这个视频"
    title="向 AI 询问这个视频"
    class="fixed bottom-[max(1.25rem,env(safe-area-inset-bottom))] right-5 z-30 size-14 justify-center rounded-full shadow-[0_12px_32px_rgba(0,0,0,0.24)] ring-4 ring-white/70 transition duration-200 hover:-translate-y-0.5 hover:shadow-[0_16px_38px_rgba(0,0,0,0.3)] sm:right-8"
    @click="open = true"
  />

  <UModal
    v-model:open="open"
    title="视频问答"
    :ui="{
      overlay: 'bg-black/35 backdrop-blur-[2px]',
      content: 'flex h-[calc(100dvh-1rem)] max-h-[42rem] flex-col overflow-hidden sm:h-[min(42rem,calc(100dvh-3rem))] sm:max-w-2xl',
      header: 'flex shrink-0 items-center gap-3 border-b border-line px-5 py-4 pe-12 sm:px-6',
      wrapper: 'min-w-0 flex-1',
      title: 'min-w-0 text-base font-semibold text-ink',
      description: 'min-w-0 text-xs leading-5 text-ink-4',
      body: 'min-h-0 flex-1 overflow-hidden p-0'
    }"
  >
    <template #title>
      <span class="flex items-center gap-2.5">
        <span class="flex size-8 items-center justify-center rounded-lg bg-ink text-white">
          <UIcon name="i-material-symbols-auto-awesome" class="size-4.5" />
        </span>
        <span>视频问答</span>
      </span>
    </template>

    <template #description>
      <div class="mt-0.5 flex min-w-0 flex-col gap-0.5 ps-10.5">
        <span class="truncate" :title="title || undefined">{{ title || '当前视频' }}</span>
        <span class="flex items-center gap-1.5">
          <span class="size-1.5 rounded-full bg-emerald-500" />
          基于完整字幕检索
        </span>
      </div>
    </template>

    <template #body>
      <div class="flex h-full min-h-0 flex-col">
        <div
          v-if="!messages.length && !loading"
          class="flex min-h-0 flex-1 flex-col items-center justify-center overflow-y-auto px-5 py-8 text-center sm:px-8"
        >
          <div class="flex size-14 items-center justify-center rounded-2xl bg-ink text-white shadow-lg shadow-black/10">
            <UIcon name="i-material-symbols-format-quote" class="size-7" />
          </div>
          <h2 class="mt-5 text-xl font-semibold tracking-tight text-ink">从视频里找答案</h2>
          <p class="mt-2 max-w-sm text-sm leading-6 text-ink-4">
            直接询问细节、观点或数据。AI 会检索完整字幕并依据原文回答。
          </p>

          <div class="mt-7 grid w-full max-w-md gap-2">
            <button
              v-for="suggestion in SUGGESTIONS"
              :key="suggestion"
              type="button"
              class="group flex items-center gap-3 rounded-xl border border-line bg-page px-4 py-3 text-left text-sm text-ink-3 transition hover:border-line-strong hover:bg-black/[0.015] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink/15"
              @click="askSuggestion(suggestion)"
            >
              <span class="size-1.5 shrink-0 rounded-full bg-ink-5 transition group-hover:bg-ink" />
              <span class="flex-1">{{ suggestion }}</span>
              <UIcon
                name="i-material-symbols-arrow-outward"
                class="size-4 shrink-0 text-ink-5 transition group-hover:translate-x-0.5 group-hover:text-ink"
              />
            </button>
          </div>
        </div>

        <div
          v-else
          ref="messageList"
          class="min-h-0 flex-1 space-y-5 overflow-y-auto px-5 py-5 sm:px-6"
          aria-live="polite"
        >
          <div
            v-for="(message, index) in messages"
            :key="`${message.role}-${index}`"
            class="flex gap-2.5"
            :class="message.role === 'user' ? 'justify-end' : 'justify-start'"
          >
            <div
              v-if="message.role === 'assistant'"
              class="mt-1 flex size-7 shrink-0 items-center justify-center rounded-lg bg-ink text-white"
              aria-hidden="true"
            >
              <UIcon name="i-material-symbols-auto-awesome" class="size-4" />
            </div>
            <div class="min-w-0" :class="message.role === 'user' ? 'max-w-[85%]' : 'max-w-[calc(85%-2.5rem)]'">
              <p v-if="message.role === 'assistant'" class="mb-1.5 text-[11px] font-medium text-ink-5">Vidoc AI</p>
              <div
                class="whitespace-pre-wrap break-words px-4 py-3 text-sm leading-6"
                :class="message.role === 'user'
                  ? 'rounded-2xl rounded-tr-md bg-ink text-white'
                  : 'rounded-2xl rounded-tl-md border border-line bg-page text-ink-3 shadow-[0_2px_10px_rgba(0,0,0,0.025)]'"
              >
                {{ message.content }}
              </div>
            </div>
          </div>

          <div v-if="loading" class="flex items-start gap-2.5">
            <div class="flex size-7 shrink-0 items-center justify-center rounded-lg bg-ink text-white" aria-hidden="true">
              <UIcon name="i-material-symbols-auto-awesome" class="size-4" />
            </div>
            <div>
              <p class="mb-1.5 text-[11px] font-medium text-ink-5">Vidoc AI</p>
              <div class="flex items-center gap-2.5 rounded-2xl rounded-tl-md border border-line bg-page px-4 py-3.5 text-sm text-ink-4">
                <span class="flex gap-1" aria-hidden="true">
                  <span class="size-1.5 animate-bounce rounded-full bg-ink-4 [animation-delay:-0.24s]" />
                  <span class="size-1.5 animate-bounce rounded-full bg-ink-4 [animation-delay:-0.12s]" />
                  <span class="size-1.5 animate-bounce rounded-full bg-ink-4" />
                </span>
                正在检索字幕并组织回答
              </div>
            </div>
          </div>
        </div>

        <div class="shrink-0 border-t border-line bg-page px-4 py-3.5 sm:px-5 sm:py-4">
          <UAlert v-if="error" color="error" variant="subtle" :title="error" class="mb-3" />
          <form
            class="rounded-2xl border border-line-strong bg-page p-1.5 shadow-[0_4px_20px_rgba(0,0,0,0.04)] transition focus-within:border-ink-4"
            @submit.prevent="send"
          >
            <label for="summary-agent-question" class="sr-only">输入问题</label>
            <textarea
              id="summary-agent-question"
              ref="input"
              v-model="question"
              rows="1"
              maxlength="1200"
              enterkeyhint="send"
              placeholder="问问视频中的具体内容…"
              class="block max-h-28 min-h-10 w-full resize-none overflow-y-auto bg-transparent px-2.5 py-2 text-sm leading-6 text-ink outline-none placeholder:text-ink-5"
              :disabled="loading"
              @input="resizeInput"
              @keydown="onKeydown"
            />
            <div class="flex items-center justify-between gap-3 px-1 pb-0.5">
              <span class="ps-1.5 text-[11px] text-ink-5">Enter 发送 · Shift + Enter 换行</span>
              <UButton
                type="submit"
                icon="i-material-symbols-arrow-upward"
                size="sm"
                aria-label="发送问题"
                class="size-8 justify-center rounded-lg"
                :loading="loading"
                :disabled="!question.trim() || loading"
              />
            </div>
          </form>
        </div>
      </div>
    </template>
  </UModal>
</template>
